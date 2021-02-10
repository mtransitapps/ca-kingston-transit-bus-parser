package org.mtransit.parser.ca_kingston_transit_bus;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mtransit.commons.CharUtils;
import org.mtransit.commons.CleanUtils;
import org.mtransit.commons.StringUtils;
import org.mtransit.parser.DefaultAgencyTools;
import org.mtransit.parser.MTLog;
import org.mtransit.parser.Utils;
import org.mtransit.parser.gtfs.data.GCalendar;
import org.mtransit.parser.gtfs.data.GCalendarDate;
import org.mtransit.parser.gtfs.data.GRoute;
import org.mtransit.parser.gtfs.data.GSpec;
import org.mtransit.parser.gtfs.data.GStop;
import org.mtransit.parser.gtfs.data.GTrip;
import org.mtransit.parser.mt.data.MAgency;
import org.mtransit.parser.mt.data.MRoute;
import org.mtransit.parser.mt.data.MTrip;

import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mtransit.commons.StringUtils.EMPTY;

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
		if (CharUtils.isDigitsOnly(routeShortName)) {
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

	@Override
	public void setTripHeadsign(@NotNull MRoute mRoute, @NotNull MTrip mTrip, @NotNull GTrip gTrip, @NotNull GSpec gtfs) {
		mTrip.setHeadsignString(
				cleanTripHeadsign(gTrip.getTripHeadsignOrDefault()),
				gTrip.getDirectionIdOrDefault()
		);
	}

	@Override
	public boolean directionSplitterEnabled() {
		return true;
	}

	@Override
	public boolean directionFinderEnabled() {
		return true;
	}

	private static final Pattern ENDS_WITH_PARENTHESIS_ = Pattern.compile("( \\(.*\\))", Pattern.CASE_INSENSITIVE);

	private static final Pattern TRANSFER_POINT_ = Pattern.compile("( transfer (point|pt) (platform|p:)\\d+$)", Pattern.CASE_INSENSITIVE);

	@NotNull
	@Override
	public String cleanDirectionHeadsign(boolean fromStopName, @NotNull String directionHeadSign) {
		directionHeadSign = super.cleanDirectionHeadsign(fromStopName, directionHeadSign);
		if (fromStopName) {
			directionHeadSign = ENDS_WITH_PARENTHESIS_.matcher(directionHeadSign).replaceAll(EMPTY);
			directionHeadSign = TRANSFER_POINT_.matcher(directionHeadSign).replaceAll(EMPTY);
		}
		return directionHeadSign;
	}

	private static final Pattern STARTS_WITH_EXPRESS = Pattern.compile("(^(express -) )*", Pattern.CASE_INSENSITIVE);
	private static final Pattern STARTS_WITH_EXTRA_BUS = Pattern.compile("(^(extra bus -) )*", Pattern.CASE_INSENSITIVE);

	private static final Pattern KGH_ = CleanUtils.cleanWords("kingston general hosp", "kingston general hospital");
	private static final String KGH_REPLACEMENT = CleanUtils.cleanWordsReplacement("KGH");

	@NotNull
	@Override
	public String cleanTripHeadsign(@NotNull String tripHeadsign) {
		tripHeadsign = STARTS_WITH_EXPRESS.matcher(tripHeadsign).replaceAll(EMPTY);
		tripHeadsign = STARTS_WITH_EXTRA_BUS.matcher(tripHeadsign).replaceAll(EMPTY);
		tripHeadsign = KGH_.matcher(tripHeadsign).replaceAll(KGH_REPLACEMENT);
		tripHeadsign = CleanUtils.keepToAndRemoveVia(tripHeadsign);
		tripHeadsign = CleanUtils.SAINT.matcher(tripHeadsign).replaceAll(CleanUtils.SAINT_REPLACEMENT);
		tripHeadsign = CleanUtils.cleanSlashes(tripHeadsign);
		tripHeadsign = CleanUtils.cleanStreetTypes(tripHeadsign);
		return CleanUtils.cleanLabel(tripHeadsign);
	}

	@Override
	public boolean mergeHeadsign(@NotNull MTrip mTrip, @NotNull MTrip mTripToMerge) {
		throw new MTLog.Fatal("Unexpected trips to merge: %s & %s!", mTrip, mTripToMerge);
	}

	private static final Pattern SIDE_ = CleanUtils.cleanWord("side");

	@NotNull
	@Override
	public String cleanStopName(@NotNull String gStopName) {
		gStopName = SIDE_.matcher(gStopName).replaceAll(EMPTY);
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
		if (stopId.length() > 0 && CharUtils.isDigitsOnly(stopId)) {
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
