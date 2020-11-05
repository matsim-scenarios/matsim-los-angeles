/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.analysis;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.io.UncheckedIOException;
import org.opengis.feature.simple.SimpleFeature;

/**
* @author ikaddoura
*/

public class ExperiencedPlanAnalysis {
	
	// TODO: trips of people that have all their trips inside the WSC area
	// TODO: trips starting and ending in the WSC after commuting into the WSC area by car
	// TODO: trips starting and ending in the WSC after commuting into the WSC area by pt
	
	private static final Logger log = Logger.getLogger(ExperiencedPlanAnalysis.class);
	// test
//	private final String plansFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/output/los-angeles-v1.1-1pct/los-angeles-v1.1-1pct.output_experienced_plans.xml.gz";
//	private final String networkFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/output/los-angeles-v1.1-1pct/los-angeles-v1.1-1pct.output_network.xml.gz";
//	private final String outputFile = "output/bc-trip-mode-zone-analysis.csv";
//	private final String outputFile_1 = "output/bc-personId2relevantTripNumbersForTripsWithStartOrEndInside.csv";
//	private final String outputFile_2 = "output/bc-personId2relevantTripNumbersForTripsOfPeopleWithAllTripsStartinAndEndingInsideAndTripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByPtIntoWSC.csv";
	
	// base case
	private final String plansFile = "/media/networkdisk/TU_Server_BAK/scenarios_parking/wsc-reduced-v1.1-1pct_run10/wsc-reduced-v1.1-1pct_run10.output_experienced_plans.xml.gz";
	private final String networkFile = "/media/networkdisk/TU_Server_BAK/scenarios_parking/wsc-reduced-v1.1-1pct_run10/wsc-reduced-v1.1-1pct_run10.output_network.xml.gz";
	private final String outputFile = "output/bc-trip-mode-zone-analysis.csv";
	private final String outputFile_1 = "output/bc-personId2relevantTripNumbersForTripsWithStartOrEndInside.csv";
	private final String outputFile_2 = "output/bc-personId2relevantTripNumbersForTripsOfPeopleWithAllTripsStartinAndEndingInsideAndTripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByPtIntoWSC.csv";
	
	// scenario 1
//	private final String plansFile = "/media/networkdisk/TU_Server_BAK/scenarios_parking/wsc-reduced-drt-scenario1-v1.1-1pct_run3/wsc-reduced-drt-scenario1-v1.1-1pct_run3.output_experienced_plans.xml.gz";
//	private final String networkFile = "/media/networkdisk/TU_Server_BAK/scenarios_parking/wsc-reduced-drt-scenario1-v1.1-1pct_run3/wsc-reduced-drt-scenario1-v1.1-1pct_run3.output_network.xml.gz";
//	private final String outputFile = "output/scenario1-trip-mode-zone-analysis.csv";
//	private final String outputFile_1 = "output/scenario1-personId2relevantTripNumbersForTripsWithStartOrEndInside.csv";
//	private final String outputFile_2 = "output/scenario1-personId2relevantTripNumbersForTripsOfPeopleWithAllTripsStartinAndEndingInsideAndTripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByPtIntoWSC.csv";
	
	// scenario 2
//	private final String plansFile = "/media/networkdisk/TU_Server_BAK/scenarios_parking/wsc-reduced-drt-scenario2-v1.1-1pct_run3/wsc-reduced-drt-scenario2-v1.1-1pct_run3.output_experienced_plans.xml.gz";
//	private final String networkFile = "/media/networkdisk/TU_Server_BAK/scenarios_parking/wsc-reduced-drt-scenario2-v1.1-1pct_run3/wsc-reduced-drt-scenario2-v1.1-1pct_run3.output_network.xml.gz";
//	private final String outputFile = "output/scenario2-trip-mode-zone-analysis.csv";
//	private final String outputFile_1 = "output/scenario2-personId2relevantTripNumbersForTripsWithStartOrEndInside.csv";
//	private final String outputFile_2 = "output/scenario2-personId2relevantTripNumbersForTripsOfPeopleWithAllTripsStartinAndEndingInsideAndTripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByPtIntoWSC.csv";
	
	// scenario 3
//	private final String plansFile = "/media/networkdisk/TU_Server_BAK/scenarios_parking/wsc-reduced-drt-scenario3-v1.1-1pct_run3/wsc-reduced-drt-scenario3-v1.1-1pct_run3.output_experienced_plans.xml.gz";
//	private final String networkFile = "/media/networkdisk/TU_Server_BAK/scenarios_parking/wsc-reduced-drt-scenario3-v1.1-1pct_run3/wsc-reduced-drt-scenario3-v1.1-1pct_run3.output_network.xml.gz";
//	private final String outputFile = "output/scenario3-trip-mode-zone-analysis.csv";
//	private final String outputFile_1 = "output/scenario3-personId2relevantTripNumbersForTripsWithStartOrEndInside.csv";
//	private final String outputFile_2 = "output/scenario3-personId2relevantTripNumbersForTripsOfPeopleWithAllTripsStartinAndEndingInsideAndTripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByPtIntoWSC.csv";
	
	
//	private final String plansFile = "/Users/ihab/Desktop/ils4a/kaddoura/la-wsc-scenarios/scenarios/wsc-reduced-v1.1/output/wsc-reduced-v1.1-10pct_run0/wsc-reduced-v1.1-10pct_run0.output_experienced_plans.xml.gz";
//	private final String networkFile = "/Users/ihab/Desktop/ils4a/kaddoura/la-wsc-scenarios/scenarios/wsc-reduced-v1.1/output/wsc-reduced-v1.1-10pct_run0/wsc-reduced-v1.1-10pct_run0.output_network.xml.gz";
//	private final String outputFile = "/Users/ihab/Desktop/bc-trip-mode-zone-analysis.csv";

//	private final String plansFile = "/Users/ihab/Desktop/ils4a/kaddoura/la-wsc-scenarios/scenarios/wsc-reduced-v1.1/output/wsc-reduced-drt-scenario1-v1.1-10pct_run6ctd2a/wsc-reduced-drt-scenario1-v1.1-10pct_run6ctd2a.output_experienced_plans.xml.gz";
//	private final String networkFile = "/Users/ihab/Desktop/ils4a/kaddoura/la-wsc-scenarios/scenarios/wsc-reduced-v1.1/output/wsc-reduced-drt-scenario1-v1.1-10pct_run6ctd2a/wsc-reduced-drt-scenario1-v1.1-10pct_run6ctd2a.output_network.xml.gz";
//	private final String outputFile = "/Users/ihab/Desktop/scenario1-trip-mode-zone-analysis.csv";
	

	private final String zoneShapeFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/original-data/shp-data/WSC_Boundary_SCAG/WSC_Boundary_SCAG.shp";
	
	public static void main(String[] args) throws IOException {
		ExperiencedPlanAnalysis analysis = new ExperiencedPlanAnalysis();
		analysis.run();	
	}
	
	private void run() throws IOException {
		
		Map<Integer, Geometry> geometries = loadShapeFile(zoneShapeFile);
		
		Config config = ConfigUtils.createConfig();
		config.plans().setInputFile(plansFile);
		config.network().setInputFile(networkFile);
		config.global().setCoordinateSystem("EPSG:3310");
		Scenario scenario = ScenarioUtils.loadScenario(config);
		
		int totalTrips = 0;
		
		int tripsWithStartAndEndInside = 0;
		int tripsWithStartOrEndInside = 0;
		int tripsWithStartAndEndOutside = 0;
		
		int tripsOfPeopleWithAllTripsStartinAndEndingInside = 0;
		
		int tripsStartAndEndInsideOfPeopleAtLeastOneTripOutside = 0;
		int tripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByCarIntoWSC = 0;
		int tripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByPtIntoWSC = 0;
		
		Map<String, Integer> modes2totalTrips = new HashMap<>();
		Map<String, Integer> modes2tripsWithStartAndEndInside = new HashMap<>();
		Map<String, Integer> modes2tripsWithStartOrEndInside = new HashMap<>();
		Map<String, Integer> modes2tripsWithStartAndEndOutside = new HashMap<>();
		Map<String, Integer> modes2tripsOfPeopleWithAllTripsStartinAndEndingInside = new HashMap<>();
		
		Map<String, Integer> modes2tripsWithStartAndEndInsideOfPeopleHasAtLeastOneTripOutside = new HashMap<>();
		// only count subsequent inside trips
		Map<String, Integer> modes2tripsWithStartAndEndInsideOfPeopleHasAtLeastOneTripOutsideAndTravelByCarIntoWSC = new HashMap<>();
		Map<String, Integer> modes2tripsWithStartAndEndInsideOfPeopleHasAtLeastOneTripOutsideAndTravelByPtIntoWSC = new HashMap<>();
		
		Map<Id<Person>, List<Integer>> personId2relevantTripNumbersForTripsWithStartOrEndInside = new HashMap<>();
		Map<Id<Person>, List<Integer>> personId2relevantTripNumbersForTripsOfPeopleWithAllTripsStartinAndEndingInsideAndTripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByPtIntoWSC = new HashMap<>();
		
		int totalPersons = 0;

		int numberOfPersonsAllTripsStartingAndEndingInside = 0;
		int numberOfPersonsAllTripsStartingAndEndingOutside = 0;
		int numberOfPersonsAtLeastOneTripStartingOrEndingInside = 0;
		int numberOfPersonsWithoutAnyTrips = 0;
		
		for (Person person : scenario.getPopulation().getPersons().values()) {
			totalPersons++;
			
			boolean personHasAtLeastOneTrip = false;
			
			boolean allTripsStartingOrEndingInside = true;
			boolean allTripsStartingOrEndingOutside = true;
			boolean atLeastOneTripStartingOrEndingInside = false;
			
			boolean atLeastOneTripOutside = false;
			boolean atLeastOneTripOutsideAndTravelByCarIntoWSC = false;
			boolean atLeastOneTripOutsideAndTravelByPtIntoWSC = false;
			boolean thisPersonCurrentlyInsideWSC = false; // flag to track if person is inside WSC or not
			boolean thisPersonEnteringWSCByCar = false;
			boolean thisPersonEnteringWSCByPt = false;
			
			int tripsThisPerson = 0;
			int tripsThisPersonStartAndEndInside = 0;
			Map<String, Integer> mode2tripsThisPerson = new HashMap<>();
			
			Map<String, Integer> modes2tripsThisPersonWithStartAndEndInsideAndAtLeastOneTripOutside = new HashMap<>();
			Map<String, Integer> modes2tripsThisPersonWithStartAndEndInsideAfterEnteringByCar = new HashMap<>();
			Map<String, Integer> modes2tripsThisPersonWithStartAndEndInsideAfterEnteringByPt = new HashMap<>();
			
			List<Integer> tripsNumbersForTripsofPeopleWithStartAndEndInside = new ArrayList<Integer>();
			
			for (Trip trip : TripStructureUtils.getTrips(person.getSelectedPlan())) {
				totalTrips++;
				tripsThisPerson++;
				personHasAtLeastOneTrip = true;
				
				if (totalTrips%100000 == 0.) {
					log.info("trip # " + totalTrips);
				}
				
				Coord originCoord;
				Coord destinationCoord;
				
				if (trip.getOriginActivity().getCoord() != null) {
					originCoord = trip.getOriginActivity().getCoord();
				} else {
					// activity does not have a coordinate, let's get it via the link ID
					originCoord = scenario.getNetwork().getLinks().get(trip.getOriginActivity().getLinkId()).getCoord();
				}
				
				if (trip.getDestinationActivity().getCoord() != null) {
					destinationCoord = trip.getDestinationActivity().getCoord();
				} else {
					// activity does not have a coordinate, let's get it via the link ID
					destinationCoord = scenario.getNetwork().getLinks().get(trip.getDestinationActivity().getLinkId()).getCoord();
				}
				
				if (originCoord == null || destinationCoord == null) {
					log.warn("Missing coordinate: " + trip.toString());
					log.warn("Skipping this trip. (person: " + person.getId() + ")");
				
				} else {
					
					// We can either get the main mode based on certain mode combinations...
					// legs: walk, car, walk --> car
					// legs: walk, pt, walk, pt, walk, pt, walk --> pt
					// legs: walk, drt, walk --> drt
					// legs: walk, drt, walk, pt, walk --> pt-with-drt
					// legs: walk --> walk
					// legs: walk, ride, walk --> ride
					// 
					// ... or just take the entire combination of modes, see the following code:
					
					Set<String> modesSet = new HashSet<>();				
					for (Leg leg : trip.getLegsOnly()) {
						modesSet.add(leg.getMode());
					}
					String modes = String.join("-", modesSet); 
					modes2totalTrips.merge(modes, 1, Integer::sum);
					mode2tripsThisPerson.merge(modes, 1, Integer::sum);
					
					if (isCoordInArea(originCoord, geometries) && isCoordInArea(destinationCoord, geometries)) {
						// trip starts AND ends inside
						tripsWithStartAndEndInside++;
						modes2tripsWithStartAndEndInside.merge(modes, 1, Integer::sum);
						
						tripsThisPersonStartAndEndInside++;

						modes2tripsThisPersonWithStartAndEndInsideAndAtLeastOneTripOutside.merge(modes, 1, Integer::sum);
						thisPersonCurrentlyInsideWSC = true;
						
						tripsNumbersForTripsofPeopleWithStartAndEndInside.add(tripsThisPerson);
						
						if (thisPersonEnteringWSCByCar) {
							tripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByCarIntoWSC++;
							modes2tripsThisPersonWithStartAndEndInsideAfterEnteringByCar.merge(modes, 1, Integer::sum);
						}
						if (thisPersonEnteringWSCByPt) {
							tripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByPtIntoWSC++;
							modes2tripsThisPersonWithStartAndEndInsideAfterEnteringByPt.merge(modes, 1, Integer::sum);
							
							if (personId2relevantTripNumbersForTripsOfPeopleWithAllTripsStartinAndEndingInsideAndTripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByPtIntoWSC.containsKey(person.getId())) {
								personId2relevantTripNumbersForTripsOfPeopleWithAllTripsStartinAndEndingInsideAndTripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByPtIntoWSC.get(person.getId()).add(tripsThisPerson);
							} else {
								List<Integer> list = new ArrayList<Integer>();
								list.add(tripsThisPerson);
								personId2relevantTripNumbersForTripsOfPeopleWithAllTripsStartinAndEndingInsideAndTripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByPtIntoWSC.put(person.getId(), list);
							}
						}
						
					} else if (isCoordInArea(originCoord, geometries) || isCoordInArea(destinationCoord, geometries)) {
						// trip starts OR ends inside
						tripsWithStartOrEndInside++;
						modes2tripsWithStartOrEndInside.merge(modes, 1, Integer::sum);
						
						if ( personId2relevantTripNumbersForTripsWithStartOrEndInside.containsKey(person.getId()) ) {
							personId2relevantTripNumbersForTripsWithStartOrEndInside.get(person.getId()).add(tripsThisPerson);
						} else {
							List<Integer> list = new ArrayList<Integer>();
							list.add(tripsThisPerson);
							personId2relevantTripNumbersForTripsWithStartOrEndInside.put(person.getId(), list);
						}
						
						
						if (!isCoordInArea(originCoord, geometries) && isCoordInArea(destinationCoord, geometries)) {
							// trip entering WSC
							if (modes.contains("car")) {
								thisPersonEnteringWSCByCar = true;
							}
							
							if (modes.contains("pt")) {
								thisPersonEnteringWSCByPt = true;
							}
							thisPersonCurrentlyInsideWSC = true;						
						}
						
						if (isCoordInArea(originCoord, geometries) && !isCoordInArea(destinationCoord, geometries)) {
							// trip leaving WSC
							thisPersonCurrentlyInsideWSC = false;						
						}
						
					} else if (!isCoordInArea(originCoord, geometries) && !isCoordInArea(destinationCoord, geometries)) {
						// trip starts AND ends outside
						tripsWithStartAndEndOutside++;
						modes2tripsWithStartAndEndOutside.merge(modes, 1, Integer::sum);
						
						thisPersonCurrentlyInsideWSC = true;
						
					} else {
						throw new RuntimeException("This shouldn't happen. Aborting...");
					}
					
					// #########
					
					if (!isCoordInArea(originCoord, geometries) || !isCoordInArea(destinationCoord, geometries)) {
						allTripsStartingOrEndingInside = false;
						
						atLeastOneTripOutside = true;
					}
					
					if (isCoordInArea(originCoord, geometries) || isCoordInArea(destinationCoord, geometries)) {
						allTripsStartingOrEndingOutside = false;
						atLeastOneTripStartingOrEndingInside = true;
					}	
										
				}
			}
			
			if (personHasAtLeastOneTrip) {
				if (allTripsStartingOrEndingInside) {
					numberOfPersonsAllTripsStartingAndEndingInside++;
					
					tripsOfPeopleWithAllTripsStartinAndEndingInside += tripsThisPerson;
					for (String mode : mode2tripsThisPerson.keySet()) {
						modes2tripsOfPeopleWithAllTripsStartinAndEndingInside.merge(mode, mode2tripsThisPerson.get(mode), Integer::sum);
					}
					
					if (personId2relevantTripNumbersForTripsOfPeopleWithAllTripsStartinAndEndingInsideAndTripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByPtIntoWSC.containsKey(person.getId())) {
						personId2relevantTripNumbersForTripsOfPeopleWithAllTripsStartinAndEndingInsideAndTripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByPtIntoWSC.get(person.getId()).addAll(tripsNumbersForTripsofPeopleWithStartAndEndInside);
					} else {
						personId2relevantTripNumbersForTripsOfPeopleWithAllTripsStartinAndEndingInsideAndTripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByPtIntoWSC.put(person.getId(), tripsNumbersForTripsofPeopleWithStartAndEndInside);
					}
				}
				
				if (allTripsStartingOrEndingOutside) {
					numberOfPersonsAllTripsStartingAndEndingOutside++;
				}
				
				if (atLeastOneTripStartingOrEndingInside) {
					numberOfPersonsAtLeastOneTripStartingOrEndingInside++;
				}
				
				if (atLeastOneTripOutside) {
					tripsStartAndEndInsideOfPeopleAtLeastOneTripOutside += tripsThisPersonStartAndEndInside;
					for (String mode :  modes2tripsThisPersonWithStartAndEndInsideAndAtLeastOneTripOutside.keySet()) {
						modes2tripsWithStartAndEndInsideOfPeopleHasAtLeastOneTripOutside.merge(mode, modes2tripsThisPersonWithStartAndEndInsideAndAtLeastOneTripOutside.get(mode), Integer::sum);
					}
					
					if (thisPersonEnteringWSCByCar) {
						for (String mode : modes2tripsThisPersonWithStartAndEndInsideAfterEnteringByCar.keySet()) {
							modes2tripsWithStartAndEndInsideOfPeopleHasAtLeastOneTripOutsideAndTravelByCarIntoWSC.merge(mode, modes2tripsThisPersonWithStartAndEndInsideAfterEnteringByCar.get(mode), Integer::sum);
						}
					}
					if (thisPersonEnteringWSCByPt) {
						for (String mode : modes2tripsThisPersonWithStartAndEndInsideAfterEnteringByPt.keySet()) {
							modes2tripsWithStartAndEndInsideOfPeopleHasAtLeastOneTripOutsideAndTravelByPtIntoWSC.merge(mode, modes2tripsThisPersonWithStartAndEndInsideAfterEnteringByPt.get(mode), Integer::sum);
						}
						
					}
				}
			} else {
				numberOfPersonsWithoutAnyTrips++;
			}
		}
		
		
		// #########################
		
		System.out.println("Total trips; " + totalTrips);
		System.out.println("tripsWithStartAndEndInside (Inside -> Inside); " + tripsWithStartAndEndInside);
		System.out.println("tripsWithStartOrEndInside (Outside -> Inside or Inside -> Outside); " + tripsWithStartOrEndInside);
		System.out.println("tripsWithStartAndEndOutside (Outside -> Outside); " + tripsWithStartAndEndOutside);
		System.out.println("Trips of persons with all trips inside; " + tripsOfPeopleWithAllTripsStartinAndEndingInside);
		System.out.println("Total of trips that start and end in WSC (of people coming from outside) " + tripsStartAndEndInsideOfPeopleAtLeastOneTripOutside);
		System.out.println("Total of trips that start and end in WSC (of people coming from outside by car) " + tripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByCarIntoWSC);
		System.out.println("Total of trips that start and end in WSC (of people coming from outside by pt) " + tripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByPtIntoWSC);
		
		System.out.println("--------------------------------------------------------------------------");

		System.out.println("Mode ; total ; Inside->Inside ; Inside<->Outside ; Outside->Outside ; "
				+ "Inside->Inside (trips by people who have all their trips inside the area) ; "
				+ "Inside->Inside (trips by people who have at least one trip outside the area) ; "  
				+ "Inside->Inside (trips by people who have at least one trip outside the area and enter WSC by car) ; "
				+ "Inside->Inside (trips by people who have at least one trip outside the area and enter WSC by PT)");
		for (String modes : modes2totalTrips.keySet()) {
			System.out.println(modes + ";"
					+ modes2totalTrips.get(modes) +  ";"
					+ modes2tripsWithStartAndEndInside.get(modes) + ";"
					+ modes2tripsWithStartOrEndInside.get(modes) + ";"
					+ modes2tripsWithStartAndEndOutside.get(modes) + ";"
					+ modes2tripsOfPeopleWithAllTripsStartinAndEndingInside.get(modes) + ";"
					+ modes2tripsWithStartAndEndInsideOfPeopleHasAtLeastOneTripOutside.get(modes) + ";"
					+ modes2tripsWithStartAndEndInsideOfPeopleHasAtLeastOneTripOutsideAndTravelByCarIntoWSC.get(modes) + ";"
					+ modes2tripsWithStartAndEndInsideOfPeopleHasAtLeastOneTripOutsideAndTravelByPtIntoWSC.get(modes));
		}
		
		System.out.println("--------------------------------------------------------------------------");
		
		System.out.println("Total persons; " + totalPersons);
		System.out.println("numberOfPersonsAtLeastOneTripStartingOrEndingInside; " + numberOfPersonsAtLeastOneTripStartingOrEndingInside);
		System.out.println("numberOfPersonsAllTripsOutside; " + numberOfPersonsAllTripsStartingAndEndingOutside);
		System.out.println("numberOfPersonsAllTripsInside; " + numberOfPersonsAllTripsStartingAndEndingInside);
		System.out.println("numberOfPersonsWithoutAnyTrips; " + numberOfPersonsWithoutAnyTrips);

		// #######################
		
		File file = new File(outputFile);
		BufferedWriter bw = new BufferedWriter(new FileWriter(file));

		bw.write("Mode ; all ; Inside->Inside ; Inside<->Outside ; Outside->Outside ; "
				+ "Inside->Inside (trips by people who have all their trips inside the area) ; "
				+ "Inside->Inside (trips by people who have at least one trip outside the area) ; "  
				+ "Inside->Inside (trips by people who have at least one trip outside the area and enter WSC by car) ; "
				+ "Inside->Inside (trips by people who have at least one trip outside the area and enter WSC by PT)");
		bw.newLine();
		
		for (String modes : modes2totalTrips.keySet()) {
			bw.write(modes + ";"
					+ modes2totalTrips.get(modes) +  ";"
					+ modes2tripsWithStartAndEndInside.get(modes) + ";"
					+ modes2tripsWithStartOrEndInside.get(modes) + ";"
					+ modes2tripsWithStartAndEndOutside.get(modes) + ";"
					+ modes2tripsOfPeopleWithAllTripsStartinAndEndingInside.get(modes) + ";"
					+ modes2tripsWithStartAndEndInsideOfPeopleHasAtLeastOneTripOutside.get(modes) + ";"
					+ modes2tripsWithStartAndEndInsideOfPeopleHasAtLeastOneTripOutsideAndTravelByCarIntoWSC.get(modes) + ";"
					+ modes2tripsWithStartAndEndInsideOfPeopleHasAtLeastOneTripOutsideAndTravelByPtIntoWSC.get(modes));
			bw.newLine();
		}
		bw.write("all modes ;"
				+ totalTrips +  ";"
				+ tripsWithStartAndEndInside + ";"
				+ tripsWithStartOrEndInside + ";"
				+ tripsWithStartAndEndOutside + ";"
				+ tripsOfPeopleWithAllTripsStartinAndEndingInside + ";"
				+ tripsStartAndEndInsideOfPeopleAtLeastOneTripOutside + ";"
				+ tripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByCarIntoWSC + ";"
				+ tripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByPtIntoWSC);
		bw.newLine();
		
		bw.write("-----");
		bw.newLine();
		
		bw.write("Total persons; " + totalPersons);
		bw.newLine();

		bw.write("numberOfPersonsAtLeastOneTripStartingOrEndingInside; " + numberOfPersonsAtLeastOneTripStartingOrEndingInside);
		bw.newLine();

		bw.write("numberOfPersonsAllTripsOutside; " + numberOfPersonsAllTripsStartingAndEndingOutside);
		bw.newLine();

		bw.write("numberOfPersonsAllTripsInside; " + numberOfPersonsAllTripsStartingAndEndingInside);
		bw.newLine();
		
		bw.write("numberOfPersonsWithoutAnyTrips; " + numberOfPersonsWithoutAnyTrips);
		bw.newLine();
		
		bw.close();
		
		// #######################
		
		File file_1 = new File(outputFile_1);
		BufferedWriter bw_1 = new BufferedWriter(new FileWriter(file_1));

		bw_1.write("person;trip_number");
		bw_1.newLine();
		
		for (Id<Person> person : personId2relevantTripNumbersForTripsWithStartOrEndInside.keySet()) {
			for (Integer trip_number : personId2relevantTripNumbersForTripsWithStartOrEndInside.get(person)) {
				bw_1.write(person.toString() + ";" + trip_number);
				bw_1.newLine();
			}		
		}
		
		bw_1.close();
		
		// #######################
		
		File file_2 = new File(outputFile_2);
		BufferedWriter bw_2 = new BufferedWriter(new FileWriter(file_2));

		bw_2.write("person;trip_number");
		bw_2.newLine();
		
		for (Id<Person> person : personId2relevantTripNumbersForTripsOfPeopleWithAllTripsStartinAndEndingInsideAndTripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByPtIntoWSC.keySet()) {
			for (Integer trip_number : personId2relevantTripNumbersForTripsOfPeopleWithAllTripsStartinAndEndingInsideAndTripsStartAndEndInsideOfPeopleAtLeastOneTripOutsideAndTravelByPtIntoWSC.get(person)) {
				bw_2.write(person.toString() + ";" + trip_number);
				bw_2.newLine();
			}		
		}
		
		bw_2.close();
		
		System.out.println("Processing done!!!!!!!!!!");
	}

	private Map<Integer, Geometry> loadShapeFile(String shapeFile) {
		Map<Integer, Geometry> geometries = new HashMap<>();

		Collection<SimpleFeature> features = null;
		if (!shapeFile.startsWith("http")) {
			features = ShapeFileReader.getAllFeatures(shapeFile);	
		} else {
			try {
				features = getAllFeatures(new URL(shapeFile));
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		}
		if (features == null) throw new RuntimeException("Aborting...");
		int featureCounter = 0;
		for (SimpleFeature feature : features) {
			geometries.put(featureCounter, (Geometry) feature.getDefaultGeometry());
			featureCounter++;
		}
		return geometries;
	}
	
	private static Collection<SimpleFeature> getAllFeatures(final URL url) {
		try {
			FileDataStore store = FileDataStoreFinder.getDataStore(url);
			SimpleFeatureSource featureSource = store.getFeatureSource();

			SimpleFeatureIterator it = featureSource.getFeatures().features();
			List<SimpleFeature> featureSet = new ArrayList<SimpleFeature>();
			while (it.hasNext()) {
				SimpleFeature ft = it.next();
				featureSet.add(ft);
			}
			it.close();
			store.dispose();
			return featureSet;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	private static boolean isCoordInArea(Coord coord, Map<Integer, Geometry> areaGeometries) {
		boolean coordInArea = false;
		for (Geometry geometry : areaGeometries.values()) {
			Point p = MGC.coord2Point(coord);

			if (p.within(geometry)) {
				coordInArea = true;
			}
		}
		return coordInArea;
	}

}

