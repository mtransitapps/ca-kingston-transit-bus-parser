package org.mtransit.parser.ca_kingston_transit_bus;

import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mtransit.parser.DefaultAgencyTools;
import org.mtransit.parser.Utils;
import org.mtransit.parser.gtfs.data.GCalendar;
import org.mtransit.parser.gtfs.data.GCalendarDate;
import org.mtransit.parser.gtfs.data.GRoute;
import org.mtransit.parser.gtfs.data.GSpec;
import org.mtransit.parser.gtfs.data.GStop;
import org.mtransit.parser.gtfs.data.GTrip;
import org.mtransit.parser.mt.data.MAgency;
import org.mtransit.parser.mt.data.MRoute;
import org.mtransit.parser.CleanUtils;
import org.mtransit.parser.mt.data.MTrip;

// https://www.cityofkingston.ca/residents/transit/about
// https://www.cityofkingston.ca/cok/data/transit_feeds/
// https://www.cityofkingston.ca/cok/data/transit_feeds/google_transit.zip
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
		System.out.printf("\nGenerating Kingston Transit bus data...");
		long start = System.currentTimeMillis();
		this.serviceIds = extractUsefulServiceIds(args, this);
		super.start(args);
		System.out.printf("\nGenerating Kingston Transit bus data... DONE in %s.\n", Utils.getPrettyDuration(System.currentTimeMillis() - start));
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
		if (this.serviceIds != null) {
			return excludeUselessTrip(gTrip, this.serviceIds);
		}
		return super.excludeTrip(gTrip);
	}

	@Override
	public Integer getAgencyRouteType() {
		return MAgency.ROUTE_TYPE_BUS;
	}

	private static final Pattern DIGITS = Pattern.compile("[\\d]+");

	private static final String ROUTE_12A_RSN = "12A";

	@Override
	public long getRouteId(GRoute gRoute) {
		if (ROUTE_12A_RSN.equals(gRoute.route_short_name)) {
			return 1012;
		}
		Matcher matcher = DIGITS.matcher(gRoute.route_id);
		matcher.find();
		return Integer.parseInt(matcher.group());
	}

	private static final String ROUTE_1 = "St Lawrence College - Montreal St";
	private static final String ROUTE_2 = "Kingston Centre - Division St";
	private static final String ROUTE_3 = "Kingston Centre - Downtown Transfer Point";
	private static final String ROUTE_4 = "Downtown Transfer Point - Cataraqui Centre";
	private static final String ROUTE_6 = "St Lawrence College - Cataraqui Centre";
	private static final String ROUTE_7 = "INVISTA Centre - Division St / Dalton Ave";
	private static final String ROUTE_9 = "Brock St / Barrie St - Cataraqui Centre";
	private static final String ROUTE_10 = "Cataraqui Centre - Amherstview";
	private static final String ROUTE_11 = "Kingston Centre - Cataraqui Centre";
	private static final String ROUTE_12 = "Highway 15 - Kingston Centre";
	private static final String ROUTE_12A = "CFB Kingston - Downtown Transfer Point";
	private static final String ROUTE_14 = "Crossfield Ave / Waterloo Dr";
	private static final String ROUTE_15 = "Reddendale - Cataraqui Woods / Cataraqui Centre";
	private static final String ROUTE_16 = "Bus Terminal - Train Station";
	private static final String ROUTE_17 = "Queen's Shuttle / Main Campus - Queen's Shuttle / West Campus";
	private static final String ROUTE_18 = "Train Station Circuit";
	private static final String ROUTE_19 = "Queen's / Kingston General Hospital - Montreal St Park & Ride";
	private static final String ROUTE_501 = "Express (Kingston Centre - Downtown - Kingston Gen. Hospital - St Lawrence College - Cataraqui Centre)";
	private static final String ROUTE_502 = "Express (St Lawrence College - Kingston Gen. Hospital - Downtown - Kingston Centre - Cataraqui Centre)";
	private static final String ROUTE_601 = "Innovation Dr Park & Ride – Queen's / KGH";
	private static final String ROUTE_602 = "Queen's / KGH – Innovation Dr Park & Ride";
	private static final String ROUTE_701 = "King's Crossing Centre – Cataraqui Centre";
	private static final String ROUTE_702 = "Cataraqui Centre - King's Crossing Centre";

	@Override
	public String getRouteLongName(GRoute gRoute) {
		if (ROUTE_12A_RSN.equals(gRoute.route_short_name)) {
			return ROUTE_12A;
		}
		Matcher matcher = DIGITS.matcher(gRoute.route_id);
		matcher.find();
		int digits = Integer.parseInt(matcher.group());
		switch (digits) {
		// @formatter:off
		case 1: return ROUTE_1;
		case 2: return ROUTE_2;
		case 3: return ROUTE_3;
		case 4: return ROUTE_4;
		case 6: return ROUTE_6;
		case 7: return ROUTE_7;
		case 9: return ROUTE_9;
		case 10: return ROUTE_10;
		case 11: return ROUTE_11;
		case 12: return ROUTE_12;
		case 14: return ROUTE_14;
		case 15: return ROUTE_15;
		case 16: return ROUTE_16;
		case 17: return ROUTE_17;
		case 18: return ROUTE_18;
		case 19: return ROUTE_19;
		case 501: return ROUTE_501;
		case 502: return ROUTE_502;
		case 601: return ROUTE_601;
		case 602: return ROUTE_602;
		case 701: return ROUTE_701;
		case 702: return ROUTE_702;
		// @formatter:on
		default:
			System.out.println("getRouteLongName() > Unexpected route ID '" + digits + "' (" + gRoute + ")");
			System.exit(-1);
			return null;
		}
	}

	private static final String AGENCY_COLOR = "009BC9";

	@Override
	public String getAgencyColor() {
		return AGENCY_COLOR;
	}

	private static final String CATARAQUI = "Cataraqui";
	private static final String VIA = " via ";

	@Override
	public void setTripHeadsign(MRoute mRoute, MTrip mTrip, GTrip gTrip, GSpec gtfs) {
		int directionId = gTrip.direction_id;
		String stationName = cleanTripHeadsign(gTrip.trip_headsign);
		int indexOfVIA = stationName.toLowerCase(Locale.ENGLISH).indexOf(VIA);
		if (indexOfVIA >= 0) {
			stationName = stationName.substring(0, indexOfVIA);
		}
		if (mRoute.id == 15l) {
			if (directionId == 1) {
				stationName = CATARAQUI;
			}
		}
		mTrip.setHeadsignString(stationName, directionId);
	}

	@Override
	public String cleanTripHeadsign(String tripHeadsign) {
		tripHeadsign = CleanUtils.cleanStreetTypes(tripHeadsign);
		return CleanUtils.cleanLabel(tripHeadsign);
	}

	private static final Pattern AT = Pattern.compile("( at )", Pattern.CASE_INSENSITIVE);
	private static final String AT_REPLACEMENT = " / ";

	private static final Pattern AND = Pattern.compile("( and )", Pattern.CASE_INSENSITIVE);
	private static final String AND_REPLACEMENT = " & ";


	@Override
	public String cleanStopName(String gStopName) {
		gStopName = CleanUtils.cleanStreetTypes(gStopName);
		gStopName = AND.matcher(gStopName).replaceAll(AND_REPLACEMENT);
		gStopName = AT.matcher(gStopName).replaceAll(AT_REPLACEMENT);
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

	@Override
	public int getStopId(GStop gStop) {
		String stopId = gStop.stop_id;
		if (stopId != null && stopId.length() > 0 && Utils.isDigitsOnly(stopId)) {
			return Integer.valueOf(stopId); // using stop code as stop ID
		}
		if (PLACE_CATC.equals(stopId)) {
			return 10000000;
		} else if (PLACE_CHCA.equals(stopId)) {
			return 11000000;
		} else if (PLACE_DWNP.equals(stopId)) {
			return 12000000;
		} else if (PLACE_GRDC.equals(stopId)) {
			return 13000000;
		} else if (PLACE_KNGC.equals(stopId)) {
			return 14000000;
		} else if (PLACE_MSPR.equals(stopId)) {
			return 15000000;
		} else if (PLACE_RAIL.equals(stopId)) {
			return 16000000;
		}
		try {
			Matcher matcher = DIGITS.matcher(stopId);
			matcher.find();
			int digits = Integer.parseInt(matcher.group());
			return digits;
		} catch (Exception e) {
			System.out.println("Error while finding stop ID for " + gStop);
			e.printStackTrace();
			System.exit(-1);
			return -1;
		}
	}
}
