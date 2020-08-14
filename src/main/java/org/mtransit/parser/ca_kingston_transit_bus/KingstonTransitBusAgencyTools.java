package org.mtransit.parser.ca_kingston_transit_bus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.mtransit.parser.CleanUtils;
import org.mtransit.parser.DefaultAgencyTools;
import org.mtransit.parser.MTLog;
import org.mtransit.parser.Pair;
import org.mtransit.parser.SplitUtils;
import org.mtransit.parser.SplitUtils.RouteTripSpec;
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

// https://openkingston.cityofkingston.ca/explore/dataset/transit-gtfs-routes/
// https://opendatakingston.cityofkingston.ca/explore/dataset/transit-gtfs-stops/
// https://api.cityofkingston.ca/gtfs/gtfs.zip
public class KingstonTransitBusAgencyTools extends DefaultAgencyTools {

	public static void main(String[] args) {
		if (args == null || args.length == 0) {
			args = new String[3];
			args[0] = "input/gtfs.zip";
			args[1] = "../../mtransitapps/ca-kingston-transit-bus-android/res/raw/";
			args[2] = ""; // files-prefix
		}
		new KingstonTransitBusAgencyTools().start(args);
	}

	private HashSet<String> serviceIds;

	@Override
	public void start(String[] args) {
		MTLog.log("Generating Kingston Transit bus data...");
		long start = System.currentTimeMillis();
		this.serviceIds = extractUsefulServiceIds(args, this, true);
		super.start(args);
		MTLog.log("Generating Kingston Transit bus data... DONE in %s.", Utils.getPrettyDuration(System.currentTimeMillis() - start));
	}

	@Override
	public boolean excludingAll() {
		return this.serviceIds != null && this.serviceIds.isEmpty();
	}

	@Override
	public boolean excludeCalendar(GCalendar gCalendar) {
		if (this.serviceIds != null) {
			return excludeUselessCalendar(gCalendar, this.serviceIds);
		}
		return super.excludeCalendar(gCalendar);
	}

	@Override
	public boolean excludeCalendarDate(GCalendarDate gCalendarDates) {
		if (this.serviceIds != null) {
			return excludeUselessCalendarDate(gCalendarDates, this.serviceIds);
		}
		return super.excludeCalendarDate(gCalendarDates);
	}

	@Override
	public boolean excludeTrip(GTrip gTrip) {
		if (gTrip.getTripHeadsign().toLowerCase(Locale.ENGLISH).contains("not in service")) {
			return true; // exclude
		}
		if (this.serviceIds != null) {
			return excludeUselessTrip(gTrip, this.serviceIds);
		}
		return super.excludeTrip(gTrip);
	}

	@Override
	public boolean excludeRoute(GRoute gRoute) {
		return super.excludeRoute(gRoute);
	}

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
	public long getRouteId(GRoute gRoute) {
		String routeShortName = gRoute.getRouteShortName();
		if (StringUtils.isEmpty(routeShortName)) {
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
	private static final String ROUTE_8 = "Route 8"; // TODO
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

	@Override
	public String getRouteLongName(GRoute gRoute) {
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
	public boolean mergeRouteLongName(MRoute mRoute, MRoute mRouteToMerge) {
		throw new MTLog.Fatal("Unexpected routes to merge: %s & %s!", mRoute, mRouteToMerge);
	}

	private static final String AGENCY_COLOR = "009BC9";

	@Override
	public String getAgencyColor() {
		return AGENCY_COLOR;
	}

	private static final String KINGSTON_GOSPEL_TEMPLE = "Kingston Gospel Temple";
	private static final String CATARAQUI_CTR_TRANSFER_PT = "Cataraqui Ctr Transfer Pt";
	private static final String MAIN_CAMPUS = "Main Campus";
	private static final String WEST_CAMPUS = "West Campus";

	private static HashMap<Long, RouteTripSpec> ALL_ROUTE_TRIPS2;
	static {
		HashMap<Long, RouteTripSpec> map2 = new HashMap<Long, RouteTripSpec>();
		map2.put(14L, new RouteTripSpec(14L, // Waterloo Dr / Crossfield Ave
				MDirectionType.EAST.intValue(), MTrip.HEADSIGN_TYPE_STRING, KINGSTON_GOSPEL_TEMPLE, //
				MDirectionType.WEST.intValue(), MTrip.HEADSIGN_TYPE_STRING, CATARAQUI_CTR_TRANSFER_PT) //
				.addTripSort(MDirectionType.EAST.intValue(), //
						Arrays.asList(new String[] { //
						"S02084", // Cataraqui Centre Transfer Point Bay 7
								"00850", // Centennial Drive
								"S00399" // Kingston Gospel Temple
						})) //
				.addTripSort(MDirectionType.WEST.intValue(), //
						Arrays.asList(new String[] { //
						"S00399", // Kingston Gospel Temple
								"00410", // Centennial Drive
								"00097", // == Norwest Road
								"S02079", // Cataraqui Centre Transfer Point Bay 3
								"S02084", // Cataraqui Centre Transfer Point Bay 7
						})) //
				.compileBothTripSort());
		ALL_ROUTE_TRIPS2 = map2;
	}

	@Override
	public int compareEarly(long routeId, List<MTripStop> list1, List<MTripStop> list2, MTripStop ts1, MTripStop ts2, GStop ts1GStop, GStop ts2GStop) {
		if (ALL_ROUTE_TRIPS2.containsKey(routeId)) {
			return ALL_ROUTE_TRIPS2.get(routeId).compare(routeId, list1, list2, ts1, ts2, ts1GStop, ts2GStop, this);
		}
		return super.compareEarly(routeId, list1, list2, ts1, ts2, ts1GStop, ts2GStop);
	}

	@Override
	public ArrayList<MTrip> splitTrip(MRoute mRoute, GTrip gTrip, GSpec gtfs) {
		if (ALL_ROUTE_TRIPS2.containsKey(mRoute.getId())) {
			return ALL_ROUTE_TRIPS2.get(mRoute.getId()).getAllTrips();
		}
		return super.splitTrip(mRoute, gTrip, gtfs);
	}

	@Override
	public Pair<Long[], Integer[]> splitTripStop(MRoute mRoute, GTrip gTrip, GTripStop gTripStop, ArrayList<MTrip> splitTrips, GSpec routeGTFS) {
		if (ALL_ROUTE_TRIPS2.containsKey(mRoute.getId())) {
			return SplitUtils.splitTripStop(mRoute, gTrip, gTripStop, routeGTFS, ALL_ROUTE_TRIPS2.get(mRoute.getId()), this);
		}
		return super.splitTripStop(mRoute, gTrip, gTripStop, splitTrips, routeGTFS);
	}

	@Override
	public void setTripHeadsign(MRoute mRoute, MTrip mTrip, GTrip gTrip, GSpec gtfs) {
		if (ALL_ROUTE_TRIPS2.containsKey(mRoute.getId())) {
			return; // split
		}
		if (gTrip.getDirectionId() == null) {
			if (mRoute.getId() == 1L) {
				if (Arrays.asList( //
						"Saint Lawrence College" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
				if (Arrays.asList( //
						"Montreal Street" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 1);
					return;
				}
			}
			if (mRoute.getId() == 1L + RID_ENDS_WITH_A) { // 1A
				if (Arrays.asList( //
						"Downtown" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
				if (Arrays.asList( //
						"Montreal Street" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 1);
					return;
				}
			}
			if (mRoute.getId() == 2L) {
				if (Arrays.asList( //
						"Kingston Centre" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
				if (Arrays.asList( //
						"Division Street" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 1);
					return;
				}
			}
			if (mRoute.getId() == 3L) {
				if (Arrays.asList( // Downtown
						"Downtown" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
				if (Arrays.asList( // Kingston Ctr
						"Kingston Centre" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 1);
					return;
				}
			}
			if (mRoute.getId() == 3L + RID_ENDS_WITH_A) { // 3A
				if (Arrays.asList( //
						"Downtown", //
						"Downtown via KGH" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
				if (Arrays.asList( //
						"Kingston Centre" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 1);
					return;
				}
			}
			if (mRoute.getId() == 4L) {
				if (Arrays.asList( // Downtown
						"Kingston Centre", //
						"Downtown via Princess St" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
				if (Arrays.asList( // Cataraqui Ctr
						"Cataraqui Centre via Princess St" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 1);
					return;
				}
			}
			if (mRoute.getId() == 6L) {
				if (Arrays.asList( // St Lawrence College
						"Saint Lawrence College" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
				if (Arrays.asList( // Cataraqui Ctr
						"Cataraqui Centre" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 1);
					return;
				}
			}
			if (mRoute.getId() == 7L) {
				if (Arrays.asList( // Rideau Hts
						"Bus Terminal via John Counter Blvd", //
						"Rideau Heights via John Counter Blvd" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
				if (Arrays.asList( // Invista Ctr
						"Invista Centre via John Counter Blvd" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 1);
					return;
				}
			}
			if (mRoute.getId() == 8L) {
				if (Arrays.asList( // Downtown
						"Extra Bus - Downtown via Queen's" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
				if (Arrays.asList( // SLC
						"Extra Bus - SLC via Queen's" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 1);
					return;
				}
			}
			if (mRoute.getId() == 10L) {
				if (Arrays.asList( // Cataraqui Ctr
						"Cataraqui Centre" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
				if (Arrays.asList( // Amherstview
						"Amherstview" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 1);
					return;
				}
			}
			if (mRoute.getId() == 11L) {
				if (Arrays.asList( // Kingston Ctr
						"Kingston Centre via Bath Road" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
				if (Arrays.asList( // Cataraqui Ctr
						"Cataraqui Centre via Bath Road" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 1);
					return;
				}
			}
			if (mRoute.getId() == 12L) {
				if (Arrays.asList( // Kingston Ctr
						"Kingston Centre" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
				if (Arrays.asList( // Hwy 15
						"CFB Kingston via Downtown" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 1);
					return;
				}
			}
			if (mRoute.getId() == 15L) {
				if (Arrays.asList( // Cataraqui Woods / Ctr
						"Cataraqui Centre", //
						"Cataraqui Centre/Cataraqui Woods" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
				if (Arrays.asList( // Reddendale
						"Reddendale" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 1);
					return;
				}
				// NEW
				if (Arrays.asList( // Kingston Ctr
						"Kingston Centre" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 1);
					return;
				}
			}
			if (mRoute.getId() == 16L) {
				if (Arrays.asList( // Train Sta
						"Bus Terminal", //
						"Train Station via Kingston Centre" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
				if (Arrays.asList( // Division / Dalton
						"Division/Dalton via Kingston Centre" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 1);
					return;
				}
			}
			if (mRoute.getId() == 501L) {
				if (Arrays.asList( // Downtown
						"Express - Downtown via Princess St" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
				if (Arrays.asList( // Cataraqui Ctr
						"Express - Cataraqui Centre via Front/Bayridge" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 1);
					return;
				}
			}
			if (mRoute.getId() == 502L) {
				if (Arrays.asList( // Downtown
						"Express - Downtown via Bayridge/Front" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
				if (Arrays.asList( // Cataraqui Ctr
						"Express - Cataraqui Centre via Princess" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 1);
					return;
				}
			}
			if (mRoute.getId() == 601L) {
				if (Arrays.asList( // Queen's / KGH
						"Express - Montreal Street Park & Ride", //
						"Express - Queen's/KGH via Downtown" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
			}
			if (mRoute.getId() == 602L) {
				if (Arrays.asList( // Downtown
						"Downtown" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
				if (Arrays.asList( // Innovation Dr
						"Express - Innovation Drive" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 1);
					return;
				}
			}
			if (mRoute.getId() == 701L) {
				if (Arrays.asList( // Cataraqui Ctr
						"Express - Cataraqui Centre via Brock/Bath", //
						"Express - Cataraqui Centre via Downtown" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
			}
			if (mRoute.getId() == 702L) {
				if (Arrays.asList( // King's Crossing
						"Express - King's Crossing via Division", //
						"Express - King's Crossing via Downtown" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
			}
			if (mRoute.getId() == 801L) {
				if (Arrays.asList( // Queen's / KGH
						"Express - Queen's/KGH via Downtown", //
						"Express - Innovation Drive" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
			}
			if (mRoute.getId() == 802L) {
				if (Arrays.asList( // Montreal St P&R
						"Express - Montreal Street Park & Ride" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
			}
			if (mRoute.getId() == 99_001L) { // COV
				if (Arrays.asList( // Cataraqui Ctr
						"Cataraqui Centre" //
				).contains(gTrip.getTripHeadsign())) {
					mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), 0);
					return;
				}
			}
			throw new MTLog.Fatal("%s: Unexpected trip %s!", mRoute.getId(), gTrip.toStringPlus());
		}
		mTrip.setHeadsignString(
			cleanTripHeadsign(gTrip.getTripHeadsign()),
			gTrip.getDirectionId() == null ? 0 : gTrip.getDirectionId()
		);
	}

	private static final Pattern STARTS_WITH_EXPRESS = Pattern.compile("(^(express -) )*", Pattern.CASE_INSENSITIVE);

	@Override
	public String cleanTripHeadsign(String tripHeadsign) {
		tripHeadsign = STARTS_WITH_EXPRESS.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = CleanUtils.keepToAndRemoveVia(tripHeadsign);
		tripHeadsign = CleanUtils.SAINT.matcher(tripHeadsign).replaceAll(CleanUtils.SAINT_REPLACEMENT);
		tripHeadsign = CleanUtils.removePoints(tripHeadsign);
		tripHeadsign = CleanUtils.cleanSlashes(tripHeadsign);
		tripHeadsign = CleanUtils.cleanStreetTypes(tripHeadsign);
		return CleanUtils.cleanLabel(tripHeadsign);
	}

	@Override
	public boolean mergeHeadsign(MTrip mTrip, MTrip mTripToMerge) {
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
					"Bus Terminal", //
					"Rideau Hts"//
			).containsAll(headsignsValues)) {
				mTrip.setHeadsignString("Rideau Hts", mTrip.getHeadsignId());
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
					"Bus Terminal", //
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

	private static final Pattern SIDE = Pattern.compile("((^|\\W){1}(side)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String SIDE_REPLACEMENT = "$2$4";

	private static final Pattern EAST_ = Pattern.compile("((^|\\W){1}(east)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String EAST_REPLACEMENT = "$2E$4";

	private static final Pattern WEST_ = Pattern.compile("((^|\\W){1}(west)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String WEST_REPLACEMENT = "$2W$4";

	private static final Pattern NORTH_ = Pattern.compile("((^|\\W){1}(north)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String NORTH_REPLACEMENT = "$2N$4";

	private static final Pattern SOUTH_ = Pattern.compile("((^|\\W){1}(south)(\\W|$){1})", Pattern.CASE_INSENSITIVE);
	private static final String SOUTH_REPLACEMENT = "$2S$4";

	@Override
	public String cleanStopName(String gStopName) {
		gStopName = SIDE.matcher(gStopName).replaceAll(SIDE_REPLACEMENT);
		gStopName = EAST_.matcher(gStopName).replaceAll(EAST_REPLACEMENT);
		gStopName = WEST_.matcher(gStopName).replaceAll(WEST_REPLACEMENT);
		gStopName = NORTH_.matcher(gStopName).replaceAll(NORTH_REPLACEMENT);
		gStopName = SOUTH_.matcher(gStopName).replaceAll(SOUTH_REPLACEMENT);
		gStopName = CleanUtils.CLEAN_AT.matcher(gStopName).replaceAll(CleanUtils.CLEAN_AT_REPLACEMENT);
		gStopName = CleanUtils.CLEAN_AND.matcher(gStopName).replaceAll(CleanUtils.CLEAN_AND_REPLACEMENT);
		gStopName = CleanUtils.cleanStreetTypes(gStopName);
		gStopName = CleanUtils.cleanNumbers(gStopName);
		gStopName = CleanUtils.removePoints(gStopName);
		return CleanUtils.cleanLabel(gStopName);
	}

	private static final String PLACE_CATC = "place_catc";
	private static final String PLACE_CHCA = "place_chca";
	private static final String PLACE_DWNP = "place_dwnp";
	private static final String PLACE_GRDC = "place_grdc";
	private static final String PLACE_KNGC = "place_kngc";
	private static final String PLACE_MSPR = "place_mspr";
	private static final String PLACE_RAIL = "place_rail";

	@Override
	public String getStopCode(GStop gStop) {
		return gStop.getStopId(); // using stop ID as stop code (useful to match with GTFS real-time)
	}

	@Override
	public int getStopId(GStop gStop) {
		String stopId = gStop.getStopId();
		if (stopId != null && stopId.length() > 0 && Utils.isDigitsOnly(stopId)) {
			return Integer.valueOf(stopId); // using stop code as stop ID
		}
		if (PLACE_CATC.equals(stopId)) {
			return 900000;
		} else if (PLACE_CHCA.equals(stopId)) {
			return 910000;
		} else if (PLACE_DWNP.equals(stopId)) {
			return 920000;
		} else if (PLACE_GRDC.equals(stopId)) {
			return 930000;
		} else if (PLACE_KNGC.equals(stopId)) {
			return 940000;
		} else if (PLACE_MSPR.equals(stopId)) {
			return 950000;
		} else if (PLACE_RAIL.equals(stopId)) {
			return 960000;
		}
		if ("Smspr1".equals(stopId)) {
			return 970000;
		}
		try {
			Matcher matcher = DIGITS.matcher(stopId);
			if (matcher.find()) {
				int digits = Integer.parseInt(matcher.group());
				if (stopId.startsWith("S")) {
					return 190000 + digits;
				}
				throw new MTLog.Fatal("Unexpected stop ID for '%s'!", gStop);
			}
		} catch (Exception e) {
			throw new MTLog.Fatal(e, "Error while finding stop ID for '%s'!", gStop);
		}
		throw new MTLog.Fatal("Unexpected stop ID for '%s'!", gStop);
	}
}
