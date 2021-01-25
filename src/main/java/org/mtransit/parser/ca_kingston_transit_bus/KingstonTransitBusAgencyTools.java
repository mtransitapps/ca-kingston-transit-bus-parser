package org.mtransit.parser.ca_kingston_transit_bus;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mtransit.parser.CleanUtils;
import org.mtransit.parser.DefaultAgencyTools;
import org.mtransit.parser.MTLog;
import org.mtransit.parser.Pair;
import org.mtransit.parser.SplitUtils;
import org.mtransit.parser.SplitUtils.RouteTripSpec;
import org.mtransit.parser.StringUtils;
import org.mtransit.parser.Utils;
import org.mtransit.parser.gtfs.data.GCalendar;
import org.mtransit.parser.gtfs.data.GCalendarDate;
import org.mtransit.parser.gtfs.data.GRoute;
import org.mtransit.parser.gtfs.data.GSpec;
import org.mtransit.parser.gtfs.data.GStop;
import org.mtransit.parser.gtfs.data.GTrip;
import org.mtransit.parser.gtfs.data.GTripStop;
import org.mtransit.parser.mt.data.MAgency;
import org.mtransit.parser.mt.data.MDirectionType;
import org.mtransit.parser.mt.data.MRoute;
import org.mtransit.parser.mt.data.MTrip;
import org.mtransit.parser.mt.data.MTripStop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// https://openkingston.cityofkingston.ca/explore/dataset/transit-gtfs-routes/
// https://opendatakingston.cityofkingston.ca/explore/dataset/transit-gtfs-stops/
// https://api.cityofkingston.ca/gtfs/gtfs.zip
public class KingstonTransitBusAgencyTools extends DefaultAgencyTools {

	public static void main(@Nullable String[] args) {
		if (args == null || args.length == 0) {
			args = new String[3];
			args[0] = "input/gtfs.zip";
			args[1] = "../../mtransitapps/ca-kingston-transit-bus-android/res/raw/";
			args[2] = ""; // files-prefix
		}
		new KingstonTransitBusAgencyTools().start(args);
	}

	@Nullable
	private HashSet<Integer> serviceIdInts;

	@Override
	public void start(@NotNull String[] args) {
		MTLog.log("Generating Kingston Transit bus data...");
		long start = System.currentTimeMillis();
		this.serviceIdInts = extractUsefulServiceIdInts(args, this, true);
		super.start(args);
		MTLog.log("Generating Kingston Transit bus data... DONE in %s.", Utils.getPrettyDuration(System.currentTimeMillis() - start));
	}

	@Override
	public boolean excludingAll() {
		return this.serviceIdInts != null && this.serviceIdInts.isEmpty();
	}

	@Override
	public boolean excludeCalendar(@NotNull GCalendar gCalendar) {
		if (this.serviceIdInts != null) {
			return excludeUselessCalendarInt(gCalendar, this.serviceIdInts);
		}
		return super.excludeCalendar(gCalendar);
	}

	@Override
	public boolean excludeCalendarDate(@NotNull GCalendarDate gCalendarDates) {
		if (this.serviceIdInts != null) {
			return excludeUselessCalendarDateInt(gCalendarDates, this.serviceIdInts);
		}
		return super.excludeCalendarDate(gCalendarDates);
	}

	@Override
	public boolean excludeTrip(@NotNull GTrip gTrip) {
		final String tripHeadSignLC = gTrip.getTripHeadsignOrDefault().toLowerCase(Locale.ENGLISH);
		if (tripHeadSignLC.contains("not in service")) {
			return true; // exclude
		}
		if (this.serviceIdInts != null) {
			return excludeUselessTripInt(gTrip, this.serviceIdInts);
		}
		return super.excludeTrip(gTrip);
	}

	@NotNull
	@Override
	public Integer getAgencyRouteType() {
		return MAgency.ROUTE_TYPE_BUS;
	}

	private static final Pattern DIGITS = Pattern.compile("[\\d]+");

	private static final String ROUTE_12A_RSN = "12A";

	private static final long RID_ENDS_WITH_A = 1_000L;
	private static final long RID_ENDS_WITH_D = 4_000L;
	private static final long RID_ENDS_WITH_P = 16_000L;
	private static final long RID_ENDS_WITH_Q = 17_000L;
	private static final long RID_ENDS_WITH_W = 23_000L;

	@Override
	public long getRouteId(@NotNull GRoute gRoute) {
		String routeShortName = gRoute.getRouteShortName();
		if (StringUtils.isEmpty(routeShortName)) {
			//noinspection deprecation
			routeShortName = gRoute.getRouteId();
		}
		if (Utils.isDigitsOnly(routeShortName)) {
			return Long.parseLong(routeShortName);
		}
		Matcher matcher = DIGITS.matcher(routeShortName);
		if (matcher.find()) {
			int digits = Integer.parseInt(matcher.group());
			String rsn = routeShortName.toLowerCase(Locale.ENGLISH);
			if (rsn.endsWith("a")) {
				return digits + RID_ENDS_WITH_A;
			} else if (rsn.endsWith("d")) {
				return digits + RID_ENDS_WITH_D;
			} else if (rsn.endsWith("p")) {
				return digits + RID_ENDS_WITH_P;
			} else if (rsn.endsWith("q")) {
				return digits + RID_ENDS_WITH_Q;
			} else if (rsn.endsWith("w")) {
				return digits + RID_ENDS_WITH_W;
			}
		}
		if ("COV".equals(routeShortName)) {
			return 99_001L;
		}
		throw new MTLog.Fatal("Unexpected route ID for '%s'!", gRoute);
	}

	private static final String ROUTE_1 = "St Lawrence College - Montreal St";
	private static final String ROUTE_2 = "Kingston Ctr - Division St";
	private static final String ROUTE_3 = "Kingston Ctr - Downtown Transfer Point";
	private static final String ROUTE_4 = "Downtown Transfer Point - Cataraqui Ctr";
	private static final String ROUTE_6 = "St Lawrence College - Cataraqui Ctr";
	private static final String ROUTE_7 = "INVISTA Ctr - Division St / Dalton Ave";
	private static final String ROUTE_8 = "Downtown - SLC (Extra Bus)"; // not official
	private static final String ROUTE_9 = "Brock St / Barrie St - Cataraqui Ctr";
	private static final String ROUTE_10 = "Cataraqui Ctr - Amherstview";
	private static final String ROUTE_11 = "Kingston Ctr - Cataraqui Ctr";
	private static final String ROUTE_12 = "Highway 15 - Kingston Ctr";
	private static final String ROUTE_12A = "CFB Kingston - Downtown Transfer Point";
	private static final String ROUTE_13 = "Downtown - SLC (Extra Bus)"; // not official
	private static final String ROUTE_14 = "Crossfield Ave / Waterloo Dr";
	private static final String ROUTE_15 = "Reddendale - Cataraqui Woods / Cataraqui Ctr";
	private static final String ROUTE_16 = "Bus Terminal - Train Station";
	private static final String ROUTE_17 = "Queen's Shuttle / Main Campus - Queen's Shuttle / West Campus";
	private static final String ROUTE_18 = "Train Station Circuit";
	private static final String ROUTE_19 = "Queen's / Kingston General Hospital - Montreal St P&R";
	private static final String ROUTE_20 = "Queen's Shuttle – Isabel / Tett Ctrs";
	private static final String ROUTE_501 = "Express (Kingston Ctr - Downtown - Kingston Gen. Hospital - St Lawrence College - Cataraqui Ctr)";
	private static final String ROUTE_502 = "Express (St Lawrence College - Kingston Gen. Hospital - Downtown - Kingston Ctr - Cataraqui Ctr)";
	private static final String ROUTE_601 = "Innovation Dr P&R – Queen's / KGH";
	private static final String ROUTE_602 = "Queen's / KGH – Innovation Dr P&R";
	private static final String ROUTE_701 = "King's Crossing Ctr – Cataraqui Ctr";
	private static final String ROUTE_702 = "Cataraqui Ctr - King's Crossing Ctr";
	private static final String ROUTE_801 = "Montreal St. P&R - Queen's/Kingston Gen. Hospital";
	private static final String ROUTE_802 = "Queen's/Kingston Gen. Hospital - Montreal St. P&R";

	@NotNull
	@Override
	public String getRouteLongName(@NotNull GRoute gRoute) {
		if (StringUtils.isEmpty(gRoute.getRouteLongName())) {
			if (ROUTE_12A_RSN.equals(gRoute.getRouteShortName())) {
				return ROUTE_12A;
			}
			if ("18Q".equals(gRoute.getRouteShortName())) {
				return "Queen's Sunday Shuttle";
			}
			if ("COV".equals(gRoute.getRouteShortName())) {
				return "Cataraqui Ctr"; // not official
			}
			//noinspection deprecation
			Matcher matcher = DIGITS.matcher(gRoute.getRouteId());
			if (matcher.find()) {
				int digits = Integer.parseInt(matcher.group());
				switch (digits) {
				// @formatter:off
				case 1: return ROUTE_1;
				case 2: return ROUTE_2;
				case 3: return ROUTE_3;
				case 4: return ROUTE_4;
				case 6: return ROUTE_6;
				case 7: return ROUTE_7;
				case 8: return ROUTE_8;
				case 9: return ROUTE_9;
				case 10: return ROUTE_10;
				case 11: return ROUTE_11;
				case 12: return ROUTE_12;
				case 13: return ROUTE_13;
				case 14: return ROUTE_14;
				case 15: return ROUTE_15;
				case 16: return ROUTE_16;
				case 17: return ROUTE_17;
				case 18: return ROUTE_18;
				case 19: return ROUTE_19;
				case 20: return ROUTE_20;
				case 501: return ROUTE_501;
				case 502: return ROUTE_502;
				case 601: return ROUTE_601;
				case 602: return ROUTE_602;
				case 701: return ROUTE_701;
				case 702: return ROUTE_702;
				case 801: return ROUTE_801;
				case 802: return ROUTE_802;
				// @formatter:on
				}
			}
			throw new MTLog.Fatal("Unexpected route long name '%s'!", gRoute);
		}
		return super.getRouteLongName(gRoute);
	}

	@Override
	public boolean mergeRouteLongName(@NotNull MRoute mRoute, @NotNull MRoute mRouteToMerge) {
		throw new MTLog.Fatal("Unexpected routes to merge: %s & %s!", mRoute, mRouteToMerge);
	}

	private static final String AGENCY_COLOR = "009BC9";

	@NotNull
	@Override
	public String getAgencyColor() {
		return AGENCY_COLOR;
	}

	private static final String KINGSTON_GOSPEL_TEMPLE = "Kingston Gospel Temple";
	private static final String CATARAQUI_CTR_TRANSFER_PT = "Cataraqui Ctr Transfer Pt";

	private static final HashMap<Long, RouteTripSpec> ALL_ROUTE_TRIPS2;

	static {
		HashMap<Long, RouteTripSpec> map2 = new HashMap<>();
		//noinspection deprecation
		map2.put(14L, new RouteTripSpec(14L, // Waterloo Dr / Crossfield Ave
				MDirectionType.EAST.intValue(), MTrip.HEADSIGN_TYPE_STRING, KINGSTON_GOSPEL_TEMPLE, //
				MDirectionType.WEST.intValue(), MTrip.HEADSIGN_TYPE_STRING, CATARAQUI_CTR_TRANSFER_PT) //
				.addTripSort(MDirectionType.EAST.intValue(), //
						Arrays.asList( //
								"S02084", // Cataraqui Centre Transfer Point Bay 7
								"00850", // Centennial Drive
								"S00399" // Kingston Gospel Temple
						)) //
				.addTripSort(MDirectionType.WEST.intValue(), //
						Arrays.asList( //
								"S00399", // Kingston Gospel Temple
								"00410", // Centennial Drive
								"00097", // == Norwest Road
								"S02079", // Cataraqui Centre Transfer Point Bay 3
								"S02084" // Cataraqui Centre Transfer Point Bay 7
						)) //
				.compileBothTripSort());
		ALL_ROUTE_TRIPS2 = map2;
	}

	@Override
	public int compareEarly(long routeId, @NotNull List<MTripStop> list1, @NotNull List<MTripStop> list2, @NotNull MTripStop ts1, @NotNull MTripStop ts2, @NotNull GStop ts1GStop, @NotNull GStop ts2GStop) {
		if (ALL_ROUTE_TRIPS2.containsKey(routeId)) {
			return ALL_ROUTE_TRIPS2.get(routeId).compare(routeId, list1, list2, ts1, ts2, ts1GStop, ts2GStop, this);
		}
		return super.compareEarly(routeId, list1, list2, ts1, ts2, ts1GStop, ts2GStop);
	}

	@NotNull
	@Override
	public ArrayList<MTrip> splitTrip(@NotNull MRoute mRoute, @Nullable GTrip gTrip, @NotNull GSpec gtfs) {
		if (ALL_ROUTE_TRIPS2.containsKey(mRoute.getId())) {
			return ALL_ROUTE_TRIPS2.get(mRoute.getId()).getAllTrips();
		}
		return super.splitTrip(mRoute, gTrip, gtfs);
	}

	@NotNull
	@Override
	public Pair<Long[], Integer[]> splitTripStop(@NotNull MRoute mRoute, @NotNull GTrip gTrip, @NotNull GTripStop gTripStop, @NotNull ArrayList<MTrip> splitTrips, @NotNull GSpec routeGTFS) {
		if (ALL_ROUTE_TRIPS2.containsKey(mRoute.getId())) {
			return SplitUtils.splitTripStop(mRoute, gTrip, gTripStop, routeGTFS, ALL_ROUTE_TRIPS2.get(mRoute.getId()), this);
		}
		return super.splitTripStop(mRoute, gTrip, gTripStop, splitTrips, routeGTFS);
	}

	@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
	@Override
	public void setTripHeadsign(@NotNull MRoute mRoute, @NotNull MTrip mTrip, @NotNull GTrip gTrip, @NotNull GSpec gtfs) {
		if (ALL_ROUTE_TRIPS2.containsKey(mRoute.getId())) {
			return; // split
		}
		if (gTrip.getDirectionId() == null) {
			final String tripHeadsign = gTrip.getTripHeadsignOrDefault();
			if (mRoute.getId() == 1L) {
				if (Arrays.asList( //
						"Saint Lawrence College" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
				if (Arrays.asList( //
						"Montreal Street" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 1);
					return;
				}
			}
			if (mRoute.getId() == 1L + RID_ENDS_WITH_A) { // 1A
				if (Arrays.asList( //
						"Downtown" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
				if (Arrays.asList( //
						"Montreal Street" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 1);
					return;
				}
			}
			if (mRoute.getId() == 2L) {
				if (Arrays.asList( //
						"Kingston Centre" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
				if (Arrays.asList( //
						"Division Street" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 1);
					return;
				}
			}
			if (mRoute.getId() == 3L) {
				if (Arrays.asList( // Downtown
						"Downtown" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
				if (Arrays.asList( // Kingston Ctr
						"Kingston Centre" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 1);
					return;
				}
			}
			if (mRoute.getId() == 3L + RID_ENDS_WITH_A) { // 3A
				if (Arrays.asList( //
						"Downtown", //
						"Downtown via KGH" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
				if (Arrays.asList( //
						"Kingston Centre" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 1);
					return;
				}
			}
			if (mRoute.getId() == 4L) {
				if (Arrays.asList( // Downtown
						"Kingston Centre", //
						"Downtown via Princess St" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
				if (Arrays.asList( // Cataraqui Ctr
						"Cataraqui Centre via Princess St" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 1);
					return;
				}
			}
			if (mRoute.getId() == 6L) {
				if (Arrays.asList( // St Lawrence College
						"Saint Lawrence College" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
				if (Arrays.asList( // Cataraqui Ctr
						"Cataraqui Centre" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 1);
					return;
				}
			}
			if (mRoute.getId() == 7L) {
				if (Arrays.asList( // Rideau Hts
						"Bus Terminal via John Counter Blvd", //
						"Rideau Heights via John Counter Blvd" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
				if (Arrays.asList( // Invista Ctr
						"Invista Centre via John Counter Blvd" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 1);
					return;
				}
			}
			if (mRoute.getId() == 8L) {
				if (Arrays.asList( // Downtown
						"Extra Bus - Downtown via Queen's" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
				if (Arrays.asList( // SLC
						"Queen's University", //
						"Extra Bus - SLC via Queen's" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 1);
					return;
				}
			}
			if (mRoute.getId() == 10L) {
				if (Arrays.asList( // Cataraqui Ctr
						"Cataraqui Centre" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
				if (Arrays.asList( // Amherstview
						"Amherstview" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 1);
					return;
				}
			}
			if (mRoute.getId() == 11L) {
				if (Arrays.asList( // Kingston Ctr
						"Kingston Centre via Bath Road" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
				if (Arrays.asList( // Cataraqui Ctr
						"Cataraqui Centre via Bath Road" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 1);
					return;
				}
			}
			if (mRoute.getId() == 12L) {
				if (Arrays.asList( // Kingston Ctr
						"Kingston Centre" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
				if (Arrays.asList( // Hwy 15
						"CFB Kingston via Downtown" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 1);
					return;
				}
			}
			if (mRoute.getId() == 15L) {
				if (Arrays.asList( // Cataraqui Woods / Ctr
						"Cataraqui Centre", //
						"Cataraqui Centre/Cataraqui Woods" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
				if (Arrays.asList( // Reddendale
						"Reddendale" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 1);
					return;
				}
				// NEW
				if (Arrays.asList( // Kingston Ctr
						"Kingston Centre" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 1);
					return;
				}
			}
			if (mRoute.getId() == 16L) {
				if (Arrays.asList( // Train Sta
						"Bus Terminal", //
						"Train Station via Kingston Centre" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
				if (Arrays.asList( // Division / Dalton
						"Division/Dalton via Kingston Centre" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 1);
					return;
				}
			}
			if (mRoute.getId() == 501L) {
				if (Arrays.asList( // Downtown
						"Express - Downtown via Princess St" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
				if (Arrays.asList( // Cataraqui Ctr
						"Express - Cataraqui Ctr via Front/Bayrdg", //
						"Express - Cataraqui Centre via Front/Bayridge" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 1);
					return;
				}
			}
			if (mRoute.getId() == 502L) {
				if (Arrays.asList( // Downtown
						"Express - Downtown via Bayridge/Front" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
				if (Arrays.asList( // Cataraqui Ctr
						"Express - Cataraqui Centre via Princess" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 1);
					return;
				}
			}
			if (mRoute.getId() == 601L) {
				if (Arrays.asList( // Queen's / KGH
						"Express - Montreal Street Park & Ride", //
						"Express - Queen's/KGH via Downtown" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
			}
			if (mRoute.getId() == 602L) {
				if (Arrays.asList( // Downtown
						"Downtown" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
				if (Arrays.asList( // Innovation Dr
						"Express - Innovation Drive" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 1);
					return;
				}
			}
			if (mRoute.getId() == 701L) {
				if (Arrays.asList( // Cataraqui Ctr
						"Express - Cataraqui Centre via Brock/Bath", //
						"Express - Cataraqui Ctr via Brock/Bath", //
						"Express - Cataraqui Centre via Downtown" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
			}
			if (mRoute.getId() == 702L) {
				if (Arrays.asList( // King's Crossing
						"Express - King's Crossing via Division", //
						"Express - King's Crossing via Downtown" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
			}
			if (mRoute.getId() == 801L) {
				if (Arrays.asList( // Queen's / KGH
						"Express - Queen's/KGH via Downtown", //
						"Express - Innovation Drive" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
			}
			if (mRoute.getId() == 802L) {
				if (Arrays.asList( // Montreal St P&R
						"Express - Montreal Street Park & Ride" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
			}
			if (mRoute.getId() == 99_001L) { // COV
				if (Arrays.asList( // Cataraqui Ctr
						"Cataraqui Centre" //
				).contains(tripHeadsign)) {
					mTrip.setHeadsignString(cleanTripHeadsign(tripHeadsign), 0);
					return;
				}
			}
			throw new MTLog.Fatal("%s: Unexpected trip %s!", mRoute.getId(), gTrip.toStringPlus());
		}
		mTrip.setHeadsignString(
				cleanTripHeadsign(gTrip.getTripHeadsignOrDefault()),
				gTrip.getDirectionIdOrDefault()
		);
	}

	private static final Pattern STARTS_WITH_EXPRESS = Pattern.compile("(^(express -) )*", Pattern.CASE_INSENSITIVE);
	private static final Pattern STARTS_WITH_EXTRA_BUS = Pattern.compile("(^(extra bus -) )*", Pattern.CASE_INSENSITIVE);

	@NotNull
	@Override
	public String cleanTripHeadsign(@NotNull String tripHeadsign) {
		tripHeadsign = STARTS_WITH_EXPRESS.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = STARTS_WITH_EXTRA_BUS.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = CleanUtils.keepToAndRemoveVia(tripHeadsign);
		tripHeadsign = CleanUtils.SAINT.matcher(tripHeadsign).replaceAll(CleanUtils.SAINT_REPLACEMENT);
		tripHeadsign = CleanUtils.cleanSlashes(tripHeadsign);
		tripHeadsign = CleanUtils.cleanStreetTypes(tripHeadsign);
		return CleanUtils.cleanLabel(tripHeadsign);
	}

	@Override
	public boolean mergeHeadsign(@NotNull MTrip mTrip, @NotNull MTrip mTripToMerge) {
		List<String> headsignsValues = Arrays.asList(mTrip.getHeadsignValue(), mTripToMerge.getHeadsignValue());
		if (mTrip.getRouteId() == 4L) {
			if (Arrays.asList( //
					"Kingston Ctr", //
					"Downtown"//
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString("Downtown", mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 7L) {
			if (Arrays.asList( //
					"Bus Term", //
					"Rideau Hts"//
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString("Rideau Hts", mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 8L) {
			if (Arrays.asList( //
					"Queen's University", //
					"SLC"//
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString("SLC", mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 15L) {
			if (Arrays.asList( //
					"Kingston Ctr", //
					"Reddendale"//
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString("Reddendale", mTrip.getHeadsignId());
				return true;
			} else if (Arrays.asList( //
					"Cataraqui Ctr", //
					"Cataraqui Ctr / Cataraqui Woods"//
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString("Cataraqui Ctr", mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 16L) {
			if (Arrays.asList( //
					"Bus Term", //
					"Train Sta" //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString("Train Sta", mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 17L + RID_ENDS_WITH_W) { // 17W
			if (Arrays.asList( //
					StringUtils.EMPTY, //
					"Queen's Main Campus",//
					"Queen's West Campus" //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString("Queen's West Campus", mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 601L) {
			if (Arrays.asList( //
					"Montreal St Pk & Ride", //
					"Queen's / KGH"//
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString("Queen's / KGH", mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 602L) {
			if (Arrays.asList( //
					"Downtown", //
					"Innovation Dr"//
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString("Innovation Dr", mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 702L) {
			if (Arrays.asList( //
					"Downtown", //
					"King's Xing"//
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString("King's Xing", mTrip.getHeadsignId());
				return true;
			}
		} else if (mTrip.getRouteId() == 801L) {
			if (Arrays.asList( //
					"Queen's / KGH", //
					"Innovation Dr" //
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString("Innovation Dr", mTrip.getHeadsignId());
				return true;
			}
		}
		throw new MTLog.Fatal("Unexpected trips to merge: %s & %s!", mTrip, mTripToMerge);
	}

	private static final Pattern SIDE = Pattern.compile("((^|\\W)(side)(\\W|$))", Pattern.CASE_INSENSITIVE);
	private static final String SIDE_REPLACEMENT = "$2" + "$4";

	@NotNull
	@Override
	public String cleanStopName(@NotNull String gStopName) {
		gStopName = SIDE.matcher(gStopName).replaceAll(SIDE_REPLACEMENT);
		gStopName = CleanUtils.cleanBounds(gStopName);
		gStopName = CleanUtils.CLEAN_AT.matcher(gStopName).replaceAll(CleanUtils.CLEAN_AT_REPLACEMENT);
		gStopName = CleanUtils.CLEAN_AND.matcher(gStopName).replaceAll(CleanUtils.CLEAN_AND_REPLACEMENT);
		gStopName = CleanUtils.cleanStreetTypes(gStopName);
		gStopName = CleanUtils.cleanNumbers(gStopName);
		return CleanUtils.cleanLabel(gStopName);
	}

	private static final String PLACE_CATC = "place_catc";
	private static final String PLACE_CHCA = "place_chca";
	private static final String PLACE_DWNP = "place_dwnp";
	private static final String PLACE_GRDC = "place_grdc";
	private static final String PLACE_KNGC = "place_kngc";
	private static final String PLACE_MSPR = "place_mspr";
	private static final String PLACE_RAIL = "place_rail";

	@NotNull
	@Override
	public String getStopCode(@NotNull GStop gStop) {
		//noinspection deprecation
		return gStop.getStopId(); // using stop ID as stop code (useful to match with GTFS real-time)
	}

	@Override
	public int getStopId(@NotNull GStop gStop) {
		//noinspection deprecation
		final String stopId = gStop.getStopId();
		if (stopId.length() > 0 && Utils.isDigitsOnly(stopId)) {
			return Integer.parseInt(stopId); // using stop code as stop ID
		}
		switch (stopId) {
		case PLACE_CATC:
			return 900_000;
		case PLACE_CHCA:
			return 910_000;
		case PLACE_DWNP:
			return 920_000;
		case PLACE_GRDC:
			return 930_000;
		case PLACE_KNGC:
			return 940_000;
		case PLACE_MSPR:
			return 950_000;
		case PLACE_RAIL:
			return 960_000;
		}
		if ("Smspr1".equals(stopId)) {
			return 970000;
		}
		try {
			Matcher matcher = DIGITS.matcher(stopId);
			if (matcher.find()) {
				int digits = Integer.parseInt(matcher.group());
				if (stopId.startsWith("S")) {
					return 190_000 + digits;
				}
				throw new MTLog.Fatal("Unexpected stop ID for '%s'!", gStop);
			}
		} catch (Exception e) {
			throw new MTLog.Fatal(e, "Error while finding stop ID for '%s'!", gStop);
		}
		throw new MTLog.Fatal("Unexpected stop ID for '%s'!", gStop);
	}
}
