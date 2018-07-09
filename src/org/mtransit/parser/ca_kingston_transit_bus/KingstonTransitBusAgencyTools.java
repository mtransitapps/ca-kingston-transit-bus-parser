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
import org.mtransit.parser.DefaultAgencyTools;
import org.mtransit.parser.Pair;
import org.mtransit.parser.SplitUtils;
import org.mtransit.parser.Utils;
import org.mtransit.parser.SplitUtils.RouteTripSpec;
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
import org.mtransit.parser.mt.data.MTripStop;
import org.mtransit.parser.CleanUtils;
import org.mtransit.parser.mt.data.MTrip;

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

	@Override
	public long getRouteId(GRoute gRoute) {
		if (Utils.isDigitsOnly(gRoute.getRouteShortName())) {
			return Long.parseLong(gRoute.getRouteShortName());
		}
		if (ROUTE_12A_RSN.equals(gRoute.getRouteShortName())) {
			return 1012L;
		}
		if ("18Q".equals(gRoute.getRouteShortName())) {
			return 17018L;
		}
		if ("COV".equals(gRoute.getRouteShortName())) {
			return 99001L;
		}
		System.out.printf("\nUnexpected route ID for '%s'!\n", gRoute);
		System.exit(-1);
		return -1L;
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
	private static final String ROUTE_13 = "Downtown - SLC (Extra Bus)"; // not official
	private static final String ROUTE_14 = "Crossfield Ave / Waterloo Dr";
	private static final String ROUTE_15 = "Reddendale - Cataraqui Woods / Cataraqui Centre";
	private static final String ROUTE_16 = "Bus Terminal - Train Station";
	private static final String ROUTE_17 = "Queen's Shuttle / Main Campus - Queen's Shuttle / West Campus";
	private static final String ROUTE_18 = "Train Station Circuit";
	private static final String ROUTE_19 = "Queen's / Kingston General Hospital - Montreal St Park & Ride";
	private static final String ROUTE_20 = "Queen's Shuttle – Isabel / Tett Centres";
	private static final String ROUTE_501 = "Express (Kingston Centre - Downtown - Kingston Gen. Hospital - St Lawrence College - Cataraqui Centre)";
	private static final String ROUTE_502 = "Express (St Lawrence College - Kingston Gen. Hospital - Downtown - Kingston Centre - Cataraqui Centre)";
	private static final String ROUTE_601 = "Innovation Dr Park & Ride – Queen's / KGH";
	private static final String ROUTE_602 = "Queen's / KGH – Innovation Dr Park & Ride";
	private static final String ROUTE_701 = "King's Crossing Centre – Cataraqui Centre";
	private static final String ROUTE_702 = "Cataraqui Centre - King's Crossing Centre";

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
				// @formatter:on
				}
			}
			if (isGoodEnoughAccepted()) {
				return "Route " + gRoute.getRouteShortName();
			}
			System.out.printf("\nUnexpected route long name '%s'!", gRoute);
			System.exit(-1);
			return null;
		}
		return super.getRouteLongName(gRoute);
	}

	@Override
	public boolean mergeRouteLongName(MRoute mRoute, MRoute mRouteToMerge) {
		System.out.printf("\nUnexpected routes to merge: %s & %s!\n", mRoute, mRouteToMerge);
		System.exit(-1);
		return false;
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
		map2.put(1L, new RouteTripSpec(1L, //
				0, MTrip.HEADSIGN_TYPE_STRING, "St Lawrence College", //
				1, MTrip.HEADSIGN_TYPE_STRING, "Montreal St") //
				.addTripSort(0, //
						Arrays.asList(new String[] { //
						"00150", // != Joyce Street (north side of Guthrie) <=
								"00154", // Sutherland Drive (west side of Virginia)
								"00168", // !=
								"00172", // ==
								"00201", // !=
								"00203", // <>
								"00746", // <>
								"S09056", // <>
								"09068", // <>
								"02018", // <>
								"00202", // !=
								"00254", // == Charles Street (west side of Montreal)
								"09097", // != Raglan Road (west side of Montreal)
								"02030", // == Rideaucrest Home (west side of Rideau)
								"00459", // == Portsmouth Avenue (north side of King)
								"00751", // != Baiden Street (west side of Portsmouth) =>
								"00460", // !=
								"S02070", // St. Lawrence College Transfer Point
						})) //
				.addTripSort(1, //
						Arrays.asList(new String[] { //
						"S02070", // St. Lawrence College Transfer Point
								"00751", // Baiden Street (west side of Portsmouth)
								"02069", // ==
								"00300", // ==
								"S02040", // ==
								"09093", // !=
								"00261", // ==
								"00199", // !=
								"00203", // <>
								"00746", // <>
								"S09056", // <>
								"09068", // <>
								"02018", // <>
								"00200", // !=
								"00171", // ==
								"00146", // !=
								"00150", // != Joyce Street (north side of Guthrie =>
						})) //
				.compileBothTripSort());
		map2.put(2L, new RouteTripSpec(2L, //
				0, MTrip.HEADSIGN_TYPE_STRING, "Kingston Ctr", //
				1, MTrip.HEADSIGN_TYPE_STRING, "Division St") //
				.addTripSort(0, //
						Arrays.asList(new String[] { //
						"Smspr1", // != Montreal Street Park and Ride <=
								"00150", // Joyce Street (north side of Guthrie) <=
								"00145", // ==
								"00168", // ++
								"09076", // ++
								"00459", // == Portsmouth Avenue (north side of King)
								"00460", // != != Baiden Street (east side of Portsmouth)
								"00464", // != <> Calderwood Drive (east side of Portsmouth)
								"S02070", // != <> St. Lawrence College Transfer Point
								"00463", // != != Churchill Street (east side of Portsmouth)
								"00461", // == Cartwright Street (south side of Churchill)
								"S00451", // ==
								"00855", // !=
								"09087", // !=
								"00450", // ==
								"S00502", // Kingston Centre Transfer Point Platform 4
						})) //
				.addTripSort(1, //
						Arrays.asList(new String[] { //
						"S00502", // Kingston Centre Transfer Point Platform 4
								"00449", // ==
								"09086", // !=
								"00454", // !=
								"S00800", // ==
								"00452", // ==
								"00464", // != !=
								"00464", // != <> Calderwood Drive (east side of Portsmouth)
								"S02070", // != <>
								"00751", // == !=
								"S02036", // == Downtown Transfer Point Platform 2
								"03020", // != Barrie Street (north side of Queen)
								"00294", // != Clergy Street (north side of Brock)
								"00273", // != Princess Street (east side of Division)
								"00279", // == Colborne Street (east side of Division)
								"00142", // ==
								"00150", // != Joyce Street (north side of Guthrie) =>
								"00144", // !=
								"Smspr1", // Montreal Street Park and Ride =>
						})) //
				.compileBothTripSort());
		map2.put(3L, new RouteTripSpec(3L, //
				0, MTrip.HEADSIGN_TYPE_STRING, "Downtown", //
				1, MTrip.HEADSIGN_TYPE_STRING, "Kingston Ctr") //
				.addTripSort(0, //
						Arrays.asList(new String[] { //
						"S00503", // Kingston Centre Transfer Point Platform 6
								"00467", // ==
								"03023", // !=
								"S02070", // != <>
								"00751", // ==
								"00305", // ==
								"03018", // != Brock Street (north side) east of Montreal Street
								"S02035", // != Downtown Transfer Point Platform 1
						})) //
				.addTripSort(1, //
						Arrays.asList(new String[] { //
						"S02035", // != Downtown Transfer Point Platform 1
								"03018", // != Brock Street (north side) east of Montreal Street
								"00303", // ==
								"00459", // ==
								"00460", // !=
								"00463", // !=
								"00464", // xx
								"S02070", // != <>
								"00464", // xx
								"00465", // ==
								"S00503", // Kingston Centre Transfer Point Platform 6
						})) //
				.compileBothTripSort());
		map2.put(4L, new RouteTripSpec(4L, //
				0, MTrip.HEADSIGN_TYPE_STRING, "Downtown", //
				1, MTrip.HEADSIGN_TYPE_STRING, "Cataraqui Ctr") //
				.addTripSort(0, //
						Arrays.asList(new String[] { //
						"S02077", // Cataraqui Centre Transfer Point Platform 1
								"S00501", // Kingston Centre Transfer Point Platform 2
								"09070", // ==
								"00290", // !=
								"09082", // != ==
								"00277", // !=
								"03005", // != ==
								"09082", // ==
								"S02037", // != Brock Street (north side) west of Bagot Street
								"S02039", // != Bagot Street (west side) north of Brock Street
						})) //
				.addTripSort(1, //
						Arrays.asList(new String[] { //
						"S02037", // != Brock Street (north side) west of Bagot Street
								"S00292", // ==
								"00273", // !=
								"00278", // !=
								"00274", // !=
								"00275", // ==
								"S02077", // Cataraqui Centre Transfer Point Platform 1
						})) //
				.compileBothTripSort());
		map2.put(6L, new RouteTripSpec(6L, //
				0, MTrip.HEADSIGN_TYPE_STRING, "St Lawrence College", //
				1, MTrip.HEADSIGN_TYPE_STRING, "Cataraqui Ctr") //
				.addTripSort(0, //
						Arrays.asList(new String[] { //
						"S02079", // Cataraqui Centre Transfer Point Platform 3
								"00520", // ==
								"00751", // != Baiden Street (west side of Portsmouth) =>
								"00460", // !=
								"S02070", // St. Lawrence College Transfer Point
						})) //
				.addTripSort(1, //
						Arrays.asList(new String[] { //
						"S02070", // St. Lawrence College Transfer Point
								"00751", // Baiden Street (west side of Portsmouth) <=
								"00521", // ==
								"00557", // ==
								"00559", // !=
								"09007", // !=
								"S02074", // ==
								"00097", // ==
								"S02079", // != Cataraqui Centre Transfer Point Platform 3 => CONTNUE
								"S02084", // != Cataraqui Centre Transfer Point Platform 7 => END
						})) //
				.compileBothTripSort());
		map2.put(7L, new RouteTripSpec(7L, //
				0, MTrip.HEADSIGN_TYPE_STRING, "Rideau Hts", // Dalton / Division
				1, MTrip.HEADSIGN_TYPE_STRING, "Invista Ctr") //
				.addTripSort(0, //
						Arrays.asList(new String[] { //
						"S00712", // INVISTA Centre
								"09044", // ==
								"S02014", // != Bus Terminal Transfer Point on John Counter
								"00218", // !=
								"S09074", // != Bus Terminal Transfer Point
								"02021", // !=
								"00746", // ==
								"00847", // ==
								"00207", // !=
								"00728", // != Dalton Avenue (east side of Grant Timmins) =>
								"09094", // !=
								"00150", // != Joyce Street (north side of Guthrie) =>
						})) //
				.addTripSort(1, //
						Arrays.asList(new String[] { //
						"00150", // != Joyce Street (north side of Guthrie) <=
								"09095", // !=
								"00728", // Dalton Avenue (east side of Grant Timmins) <=
								"00206", // !=
								"00182", // ==
								"S00052", // == Bus Terminal Transfer Point
								"S02007", // != Bus Terminal Transfer Point =>
								"09050", // !=
								"S00712", // INVISTA Centre
						})) //
				.compileBothTripSort());
		map2.put(8L, new RouteTripSpec(8L, //
				0, MTrip.HEADSIGN_TYPE_STRING, "Downtown", //
				1, MTrip.HEADSIGN_TYPE_STRING, "St Lawrence College") //
				.addTripSort(0, //
						Arrays.asList(new String[] { //
						"S02070", // St. Lawrence College Transfer Point
								"00300", // ==
								"S02039", // != Downtown Transfer Point Platform 4 =>
								"S02037", // != Downtown Transfer Point Platform 3 =>
						})) //
				.addTripSort(1, //
						Arrays.asList(new String[] { //
						"S02039", // Downtown Transfer Point Platform 4
								"00107", // ++
								"S02070", // St. Lawrence College Transfer Point
						})) //
				.compileBothTripSort());
		map2.put(10L, new RouteTripSpec(10L, //
				0, MTrip.HEADSIGN_TYPE_STRING, "Cataraqui Ctr", //
				1, MTrip.HEADSIGN_TYPE_STRING, "Amherstview") //
				.addTripSort(0, //
						Arrays.asList(new String[] { //
						"S09088", // Speers Boulevard (north side of Kildare)
								"S02084", // Cataraqui Centre Transfer Point Platform 7
						})) //
				.addTripSort(1, //
						Arrays.asList(new String[] { //
						"S02084", // Cataraqui Centre Transfer Point Platform 7
								"S09088", // Speers Boulevard (north side of Kildare)
						})) //
				.compileBothTripSort());
		map2.put(11L, new RouteTripSpec(11L, //
				0, MTrip.HEADSIGN_TYPE_STRING, "Kingston Ctr", //
				1, MTrip.HEADSIGN_TYPE_STRING, "Cataraqui Ctr") //
				.addTripSort(0, //
						Arrays.asList(new String[] { //
						"S02082", // Cataraqui Centre Transfer Point Platform 5
								"S02075", // Gardiners Centre Transfer Point
								"09029", // ==
								"09026", // !=
								"09018", // ==
								"02008", // ==
								"S00506", // Kingston Centre Transfer Point Platform 5
								"S00502", // Kingston Centre Transfer Point Platform 4
						})) //
				.addTripSort(1, //
						Arrays.asList(new String[] { //
						"S00506", // Kingston Centre Transfer Point Platform 5
								"S02082", // Cataraqui Centre Transfer Point Platform 5
						})) //
				.compileBothTripSort());
		map2.put(12L, new RouteTripSpec(12L, //
				0, MTrip.HEADSIGN_TYPE_STRING, "Kingston Ctr", //
				1, MTrip.HEADSIGN_TYPE_STRING, "Hwy 15") //
				.addTripSort(0, //
						Arrays.asList(new String[] { //
						"S09205", // Rideau Town Centre (south side of Gore)
								"00132", // ==
								"02010", // !=
								"09065", // !=
								"00755", // != <>
								"00089", // != <>
								"S00029", // == <>
								"00086", // == !=
								"00305", // ==
								"S02035", // != Downtown Transfer Point Platform 1
								"00326", // ==
								"S00502", // != Kingston Centre Transfer Point Platform 4
								"S00501", // != Kingston Centre Transfer Point Platform 2
						})) //
				.addTripSort(1, //
						Arrays.asList(new String[] { //
						"S00502", // Kingston Centre Transfer Point Platform 4
								"00092", // ==
								"S02038", // != Downtown Transfer Point Platform 5
								"00042", // ==
								"S00126", // ==
								"09096", // !=
								"00755", // != <>
								"00089", // != <>
								"S00029", // == <> Princess Mary Avenue (north side of Cambrai)
								"00124", // !=
								"S00115", // ==
								"00079", // !=
								"00088", // !=
								"09064", // !=
								"09225", // ==
								"S09205",// Rideau Town Centre (south side of Gore)
						})) //
				.compileBothTripSort());
		map2.put(13L, new RouteTripSpec(13L, //
				0, MTrip.HEADSIGN_TYPE_STRING, "Downtown", // Extra Bus
				1, MTrip.HEADSIGN_TYPE_STRING, "St Lawrence College") // SLC // Extra Bus
				.addTripSort(0, //
						Arrays.asList(new String[] { //
						"S02070", // St. Lawrence College Transfer Point
								"S02039", // Bagot Street (west side) north of Brock Street
						})) //
				.addTripSort(1, //
						Arrays.asList(new String[] { //
						"S02039", // Bagot Street (west side) north of Brock Street
								"S02070", // St. Lawrence College Transfer Point
						})) //
				.compileBothTripSort());
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
		map2.put(15L, new RouteTripSpec(15L, //
				MDirectionType.NORTH.intValue(), MTrip.HEADSIGN_TYPE_STRING, "Cataraqui Woods / Ctr", // Cataraqui Ctr / Cataraqui Woods
				MDirectionType.SOUTH.intValue(), MTrip.HEADSIGN_TYPE_STRING, "Reddendale") //
				.addTripSort(MDirectionType.NORTH.intValue(), //
						Arrays.asList(new String[] { //
						"S09037", // Centre 70 Park and Ride (west side of Days)
								"00576", // ==
								"00577", // !=
								"00578", // ==
								"00560", // ==
								"00811", // !=
								"09007", // !=
								"S02074", // ==
								"S00028", // 1386 Waverley Crescent (north side)
								"00736", // != !=
								"00097", // xx <>
								"S02077", // xx != Cataraqui Centre Transfer Point Platform 1 =>
								"S02081", // xx != Cataraqui Centre Transfer Point Platform 4
								"00799", // xx
								"00741", // !=
								"00651", // != !=
								"00097", // xx <>
								"S02081", // xx != Cataraqui Centre Transfer Point Platform 4
								"00799", // xx
								"00077", // !=
								"00742", // Peachwood Street (west side of Birchwood)
						})) //
				.addTripSort(MDirectionType.SOUTH.intValue(), //
						Arrays.asList(new String[] { //
						"00742", // Peachwood Street (west side of Birchwood)
								"00740", // . !=
								"00097", // <>
								"S02085", // !=
								"S00018", // 1385 Waverley Crescent (south side)
								"S02075", // Gardiners Centre Transfer Point
								"S09037", // Centre 70 Park and Ride (west side of Days)
						})) //
				.compileBothTripSort());
		map2.put(16L, new RouteTripSpec(16L, //
				0, MTrip.HEADSIGN_TYPE_STRING, "Train Sta", //
				1, MTrip.HEADSIGN_TYPE_STRING, "Division / Dalton") // Bus Terminal
				.addTripSort(0, //
						Arrays.asList(new String[] { //
						"00728", // Dalton Avenue (east side of Grant Timmins)
								"00832", // ==
								"00216", // != <>
								"00051", // != <>
								"00690", // != <>
								"S02014", // != <> Bus Terminal Transfer Point Platform 2 =>
								"S00052", // != <> Bus Terminal Transfer Point Platform 3 =>
								"00217", // !=
								"S09074", // Bus Terminal Transfer Point Platform 4
								"02021", // ==
								"00237", // !=
								"S00396", // Train Station Transfer Point =>
						})) //
				.addTripSort(1, //
						Arrays.asList(new String[] { //
						"S00396", // Train Station Transfer Point
								"09229", // !=
								"00216", // <>
								"00051", // <>
								"00690", // <> ==
								"S00052", // != <> Bus Terminal Transfer Point Platform 3 =>
								"S02014", // <> != Bus Terminal Transfer Point Platform 2
								"00218", // !=
								"00728", // Dalton Avenue (east side of Grant Timmins)
						})) //
				.compileBothTripSort());
		map2.put(17l, new RouteTripSpec(17l, //
				MDirectionType.EAST.intValue(), MTrip.HEADSIGN_TYPE_STRING, MAIN_CAMPUS, //
				MDirectionType.WEST.intValue(), MTrip.HEADSIGN_TYPE_STRING, WEST_CAMPUS) //
				.addTripSort(MDirectionType.EAST.intValue(), //
						Arrays.asList(new String[] { //
						"00472", "00107", "S00444" //
						})) //
				.addTripSort(MDirectionType.WEST.intValue(), //
						Arrays.asList(new String[] { //
						"S00444", "S00430", "00472" //
						})) //
				.compileBothTripSort());
		map2.put(18L, new RouteTripSpec(18L, //
				0, MTrip.HEADSIGN_TYPE_STRING, "Train Sta", //
				1, MTrip.HEADSIGN_TYPE_STRING, "St Lawrence College") //
				.addTripSort(0, //
						Arrays.asList(new String[] { //
						"S02070", // != St. Lawrence College Transfer Point <=
								"00463", // != Churchill Street (east side of Portsmouth) <=
								"00464", // == xx
								"00465", // == !=
								"00374", // ++
								"S00396", // Train Station Transfer Point
						})) //
				.addTripSort(1, //
						Arrays.asList(new String[] { //
						"S00396", // Train Station Transfer Point
								"S02007", // Bus Terminal Transfer Point Platform 1
								"S02039", // Downtown Transfer Point Platform 4
								"00459", // ==
								"00463", // != Churchill Street (east side of Portsmouth) =>
								"00460", // !=
								"00464", // != xx
								"S02070", // != St. Lawrence College Transfer Point =>
						})) //
				.compileBothTripSort());
		map2.put(17018L, new RouteTripSpec(17018L, // 18Q
				0, MTrip.HEADSIGN_TYPE_STRING, "Queen's", //
				1, MTrip.HEADSIGN_TYPE_STRING, "St Lawrence College") //
				.addTripSort(0, //
						Arrays.asList(new String[] { //
						"S00396", // Train Station Transfer Point
								"S02007", // Bus Terminal Transfer Point
								"00710", // Victoria Hall (north side of Bader
								"09080", // St. Lawrence Avenue (south side of Stuart #QUEEN_S_UNIVERSITY
						})) //
				.addTripSort(1, //
						Arrays.asList(new String[] { //
						"09080", // St. Lawrence Avenue (south side of Stuart #QUEEN_S_UNIVERSITY
								"S02070", // St. Lawrence College Transfer Point
								"00472", // Van Order Drive (north side of Norman Rogers)
						})) //
				.compileBothTripSort());
		map2.put(20L, new RouteTripSpec(20L, //
				MDirectionType.EAST.intValue(), MTrip.HEADSIGN_TYPE_STRING, MAIN_CAMPUS, //
				MDirectionType.WEST.intValue(), MTrip.HEADSIGN_TYPE_STRING, WEST_CAMPUS) //
				.addTripSort(MDirectionType.EAST.intValue(), //
						Arrays.asList(new String[] { //
						"S02009", "S00417", "S02042" //
						})) //
				.addTripSort(MDirectionType.WEST.intValue(), //
						Arrays.asList(new String[] { //
						"S02042", "S00411", "S02009" //
						})) //
				.compileBothTripSort());
		map2.put(501L, new RouteTripSpec(501L, //
				0, MTrip.HEADSIGN_TYPE_STRING, "Downtown", //
				1, MTrip.HEADSIGN_TYPE_STRING, "Cataraqui Ctr") //
				.addTripSort(0, //
						Arrays.asList(new String[] { //
						"S02077", // Cataraqui Centre Transfer Point Platform 1
								"S09042", // Kingston Centre (south side of Princess)
								"S00356", // ==
								"09212", // !=
								"S00272", // !=
								"S02039", // == Downtown Transfer Point Platform 4
						})) //
				.addTripSort(1, //
						Arrays.asList(new String[] { //
						"S02039", // Downtown Transfer Point Platform 4
								"S00411", // ==
								"00412", // !=
								"S09040", // !=
								"S09038", // ==
								"S02003", // Bayridge Centre (east side of Bayridge)
								"S02077", // Cataraqui Centre Transfer Point Platform 1
						})) //
				.compileBothTripSort());
		map2.put(502L, new RouteTripSpec(502L, //
				0, MTrip.HEADSIGN_TYPE_STRING, "Downtown", //
				1, MTrip.HEADSIGN_TYPE_STRING, "Cataraqui Ctr") //
				.addTripSort(0, //
						Arrays.asList(new String[] { //
						"S02078", // Cataraqui Centre Transfer Point Platform 2
								"S00413", // Sir John A Macdonald Blvd. (south side of King)
								"S00426", // Kingston General Hospital (south side of Stuart)
								"S02037", // Brock Street (north side) west of Bagot Street
						})) //
				.addTripSort(1, //
						Arrays.asList(new String[] { //
						"S02037", // Brock Street (north side) west of Bagot Street
								"S09041", // ++
								"S02078", // Cataraqui Centre Transfer Point Platform 2
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

	private static final String VIA = " via ";

	@Override
	public void setTripHeadsign(MRoute mRoute, MTrip mTrip, GTrip gTrip, GSpec gtfs) {
		if (ALL_ROUTE_TRIPS2.containsKey(mRoute.getId())) {
			return; // split
		}
		mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), gTrip.getDirectionId() == null ? 0 : gTrip.getDirectionId());
	}

	private static final Pattern STARTS_WITH_EXPRESS = Pattern.compile("(^(express -) )*", Pattern.CASE_INSENSITIVE);

	@Override
	public String cleanTripHeadsign(String tripHeadsign) {
		int indexOfVIA = tripHeadsign.toLowerCase(Locale.ENGLISH).indexOf(VIA);
		if (indexOfVIA >= 0) {
			tripHeadsign = tripHeadsign.substring(0, indexOfVIA);
		}
		tripHeadsign = STARTS_WITH_EXPRESS.matcher(tripHeadsign).replaceAll(StringUtils.EMPTY);
		tripHeadsign = CleanUtils.SAINT.matcher(tripHeadsign).replaceAll(CleanUtils.SAINT_REPLACEMENT);
		tripHeadsign = CleanUtils.removePoints(tripHeadsign);
		tripHeadsign = CleanUtils.cleanSlashes(tripHeadsign);
		tripHeadsign = CleanUtils.cleanStreetTypes(tripHeadsign);
		return CleanUtils.cleanLabel(tripHeadsign);
	}

	@Override
	public boolean mergeHeadsign(MTrip mTrip, MTrip mTripToMerge) {
		List<String> headsignsValues = Arrays.asList(mTrip.getHeadsignValue(), mTripToMerge.getHeadsignValue());
		if (mTrip.getRouteId() == 15l) {
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
		}
		System.out.printf("\nUnexpected trips to merge: %s & %s!\n", mTrip, mTripToMerge);
		System.exit(-1);
		return false;
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
				System.out.printf("\nUnexpected stop ID for '%s'!\n", gStop);
				System.exit(-1);
				return digits;
			}
		} catch (Exception e) {
			System.out.printf("\nError while finding stop ID for '%s'!\n", gStop);
			e.printStackTrace();
			System.exit(-1);
			return -1;
		}
		System.out.printf("\nUnexpected stop ID for '%s'!\n", gStop);
		System.exit(-1);
		return -1;
	}
}
