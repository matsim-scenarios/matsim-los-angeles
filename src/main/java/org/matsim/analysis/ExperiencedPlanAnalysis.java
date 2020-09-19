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
	
	private static final Logger log = Logger.getLogger(ExperiencedPlanAnalysis.class);

//	private final String plansFile = "../public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/output/los-angeles-v1.1-1pct/los-angeles-v1.1-1pct.output_experienced_plans.xml.gz";
//	private final String networkFile = "../public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/output/los-angeles-v1.1-1pct/los-angeles-v1.1-1pct.output_network.xml.gz";
//	private final String outputFile = "/Users/ihab/Desktop/trip-mode-zone-analysis.csv";
	
	private final String plansFile = "/Users/ihab/Desktop/ils4a/kaddoura/la-wsc-scenarios/scenarios/wsc-reduced-v1.1/output/wsc-reduced-drt-scenario1-v1.1-10pct_run6ctd2a/wsc-reduced-drt-scenario1-v1.1-10pct_run6ctd2a.output_experienced_plans.xml.gz";
	private final String networkFile = "/Users/ihab/Desktop/ils4a/kaddoura/la-wsc-scenarios/scenarios/wsc-reduced-v1.1/output/wsc-reduced-drt-scenario1-v1.1-10pct_run6ctd2a/wsc-reduced-drt-scenario1-v1.1-10pct_run6ctd2a.output_network.xml.gz";
	private final String outputFile = "/Users/ihab/Desktop/scenario1-trip-mode-zone-analysis.csv";

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
		
		Map<String, Integer> modes2totalTrips = new HashMap<>();
		Map<String, Integer> modes2tripsWithStartAndEndInside = new HashMap<>();
		Map<String, Integer> modes2tripsWithStartOrEndInside = new HashMap<>();
		Map<String, Integer> modes2tripsWithStartAndEndOutside = new HashMap<>();

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
			
			for (Trip trip : TripStructureUtils.getTrips(person.getSelectedPlan())) {
				totalTrips++;
				
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
					
					if (isCoordInArea(originCoord, geometries) && isCoordInArea(destinationCoord, geometries)) {
						// trip starts AND ends inside
						tripsWithStartAndEndInside++;
						modes2tripsWithStartAndEndInside.merge(modes, 1, Integer::sum);
						
					} else if (isCoordInArea(originCoord, geometries) || isCoordInArea(destinationCoord, geometries)) {
						// trip starts OR ends inside
						tripsWithStartOrEndInside++;
						modes2tripsWithStartOrEndInside.merge(modes, 1, Integer::sum);
						
					} else if (!isCoordInArea(originCoord, geometries) && !isCoordInArea(destinationCoord, geometries)) {
						// trip starts AND ends outside
						tripsWithStartAndEndOutside++;
						modes2tripsWithStartAndEndOutside.merge(modes, 1, Integer::sum);
						
					} else {
						throw new RuntimeException("This shouldn't happen. Aborting...");
					}
					
					// #########
					
					if (!isCoordInArea(originCoord, geometries) || !isCoordInArea(destinationCoord, geometries)) {
						allTripsStartingOrEndingInside = false;
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
				}
				
				if (allTripsStartingOrEndingOutside) {
					numberOfPersonsAllTripsStartingAndEndingOutside++;
				}
				
				if (atLeastOneTripStartingOrEndingInside) {
					numberOfPersonsAtLeastOneTripStartingOrEndingInside++;
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
		
		System.out.println("--------------------------------------------------------------------------");

		System.out.println("Mode ; total ; Inside->Inside ; Inside<->Outside ; Outside->Outside");
		for (String modes : modes2totalTrips.keySet()) {
			System.out.println(modes + ";"
					+ modes2totalTrips.get(modes) +  ";"
					+ modes2tripsWithStartAndEndInside.get(modes) + ";"
					+ modes2tripsWithStartOrEndInside.get(modes) + ";"
					+ modes2tripsWithStartAndEndOutside.get(modes));
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

		bw.write("Mode ; all ; Inside->Inside ; Inside<->Outside ; Outside->Outside");
		bw.newLine();
		
		for (String modes : modes2totalTrips.keySet()) {
			bw.write(modes + ";"
					+ modes2totalTrips.get(modes) +  ";"
					+ modes2tripsWithStartAndEndInside.get(modes) + ";"
					+ modes2tripsWithStartOrEndInside.get(modes) + ";"
					+ modes2tripsWithStartAndEndOutside.get(modes));
			bw.newLine();
		}
		bw.write("all modes ;"
				+ totalTrips +  ";"
				+ tripsWithStartAndEndInside + ";"
				+ tripsWithStartOrEndInside + ";"
				+ tripsWithStartAndEndOutside);
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

