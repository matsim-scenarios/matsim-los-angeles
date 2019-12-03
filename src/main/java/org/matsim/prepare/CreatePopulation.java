/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
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

package org.matsim.prepare;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.opengis.feature.simple.SimpleFeature;

/**
* @author ikaddoura
*/

public class CreatePopulation {
	private static final Logger log = Logger.getLogger(CreatePopulation.class);
	
	private final CSVFormat csvFormat = CSVFormat.DEFAULT.withFirstRecordAsHeader();
	private final Random rnd = MatsimRandom.getRandom();
	private final String crs = "EPSG:3310";
	private final double sample = 0.001;
	private final String outputFilePrefix = "scag-population-" + sample + "_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date());
	
	public static void main(String[] args) throws IOException {
		String rootDirectory = null;
		
		if (args.length == 1) {
			rootDirectory = args[0];
		} else {
			throw new RuntimeException("Please set the root directory (the directory above 'scag_model'). Aborting...");
		}
		
		if (!rootDirectory.endsWith("/")) rootDirectory = rootDirectory + "/";
		
		CreatePopulation createPopulation = new CreatePopulation();
		createPopulation.run(rootDirectory);
	}
	
	private void run(String rootDirectory) throws NumberFormatException, IOException {
		
//		final String personFile = rootDirectory + "LA012.2013-20_SCAG/test-data/test_persons.csv";
//		final String tripFile = rootDirectory + "LA012.2013-20_SCAG/test-data/test_trips.csv";
		final String householdFile = rootDirectory + "LA012.2013-20_SCAG/abm/output_disaggHouseholdList.csv";
		final String personFile = rootDirectory + "LA012.2013-20_SCAG/abm/output_disaggPersonList.csv";
		final String tripFile = rootDirectory + "LA012.2013-20_SCAG/abm/output_disaggTripList.csv";
		final String tazShpFile = rootDirectory + "LA012.2013-20_SCAG/shp-files/Tier_2_Transportation_Analysis_Zones_TAZs_in_SCAG_EPSG3310/Tier_2_Transportation_Analysis_Zones_TAZs_in_SCAG_EPSG3310.shp";
		final String outputDirectory = rootDirectory + "matsim-input-files/population/";
		
		OutputDirectoryLogging.catchLogEntries();
		try {
			OutputDirectoryLogging.initLoggingWithOutputDirectory(outputDirectory);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		log.info("Loading shape file...");
		final Map<String, Geometry> geometries = loadGeometries(tazShpFile, "OBJECTID");
		log.info("Loading shape file... Done.");
		
		log.info("Creating scenario...");
				
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());	
		scenario.getConfig().global().setCoordinateSystem(crs );
		
		log.info("Creating scenario... Done.");
		
		log.info("Creating population...");

		PopulationFactory populationFactory = scenario.getPopulation().getFactory();

		// create persons and add person attributes
		log.info("Creating persons...");

		int includedPersons = 0;
		int excludedPersons = 0;
		int personsInDataSet = 0;
		Set<String> householdIdsOfIncludedPersons = new HashSet<>();
		for (CSVRecord csvRecord : new CSVParser(Files.newBufferedReader(Paths.get(personFile)), csvFormat)) {	
			personsInDataSet++;
			
			if (rnd.nextDouble() <= sample) {
				String personId = csvRecord.get(0);
				String householdId = csvRecord.get(2);
				householdIdsOfIncludedPersons.add(householdId);
				String age = csvRecord.get(4);
				int genderCode = Integer.valueOf(csvRecord.get(5));
				String genderString = getGenderString(genderCode);

				Person person = populationFactory.createPerson(Id.createPersonId(personId));
				person.getAttributes().putAttribute("householdId", householdId);
				person.getAttributes().putAttribute("age", age);
				person.getAttributes().putAttribute("gender", genderString);
				
				// TODO: add person attributes for all (required) attributes

				scenario.getPopulation().addPerson(person);
				includedPersons++;
			} else {
				excludedPersons++;
			}
		}
		
		log.info("Creating persons... Done.");
		
		log.info("Included persons: " + includedPersons);
		log.info("Excluded persons: " + excludedPersons);
		log.info("Total number of persons in data set: " + personsInDataSet);
		
		log.info("Creating plans...");
		
		// read trip data and create a plan
		int tripsInDataSet = 0;
		int includedTripsCounter = 0;
		int excludedTripsCounter = 0;
		boolean firstTrip;
		for (CSVRecord csvRecord : new CSVParser(Files.newBufferedReader(Paths.get(tripFile)), csvFormat)) {	
			tripsInDataSet++;
			if (tripsInDataSet%1000000 == 0) {
				log.info("trip record #" + tripsInDataSet );
			}
			
			Id<Person> personId = Id.createPersonId(csvRecord.get(4));			
			Person person = scenario.getPopulation().getPersons().get(personId);
			if (person == null) {
				// person was excluded from our sample
				excludedTripsCounter++;
			} else {
				includedTripsCounter++;
				Plan plan; 
				if (person.getPlans().size() > 0) {
					plan = person.getPlans().get(0);
					firstTrip = false;
				} else {
					plan = populationFactory.createPlan();
					firstTrip = true;
					person.addPlan(plan);	
				}
				
				if (firstTrip) {	
					// origin activity
					int tripPurposeOriginCode = Integer.valueOf(csvRecord.get(18));
					String tripPurposeOrigin = getTripPurposeString(tripPurposeOriginCode);		
					String tripOriginTAZid = csvRecord.get(20);
					Coord coord = getRandomCoord(tripOriginTAZid, geometries);
					Activity act = populationFactory.createActivityFromCoord(tripPurposeOrigin, coord);
					plan.addActivity(act);
				}
				
				// set end time of previous activity
				double originEndTimeSec = Double.valueOf(csvRecord.get(23)) * 60.;
				Activity previousActivity = (Activity) plan.getPlanElements().get(plan.getPlanElements().size() - 1);
				previousActivity.setEndTime(originEndTimeSec);
				// TODO: set duration instead of end time for short activities
				
				// trip
				Integer modeCode = Integer.valueOf(csvRecord.get(25));
				String mode = getModeString(modeCode);
				Leg leg = populationFactory.createLeg(mode);		
				plan.addLeg(leg);
				
				// destination activity
				int tripPurposeDestinationCode = Integer.valueOf(csvRecord.get(19));
				String tripPurposeDestination = getTripPurposeString(tripPurposeDestinationCode);
				String tripDestinationTAZid = csvRecord.get(21);
				Coord coord = getRandomCoord(tripDestinationTAZid, geometries);
				Activity act = populationFactory.createActivityFromCoord(tripPurposeDestination, coord);	
				plan.addActivity(act);
			}
		}
		
		log.info("Included trips: " + includedTripsCounter);
		log.info("Excluded trips: " + excludedTripsCounter);
		log.info("Total number of trips in data set: " + tripsInDataSet);
		
		log.info("Creating plans... Done.");
		log.info("Creating population... Done.");
		
		log.info("Handling stay home plans...");
		// Get the person's correct home location via the household ID.
		
		// This also means we need to parse the household data
		log.info("Reading household data...");
		Map<String, String> hhId2TazId = new HashMap<>();
		for (CSVRecord csvRecord : new CSVParser(Files.newBufferedReader(Paths.get(householdFile)), csvFormat)) {	
			String householdId = csvRecord.get(0);
			if (householdIdsOfIncludedPersons.contains(householdId)) {
				String hhTAZid = csvRecord.get(2);
				hhId2TazId.put(householdFile, hhTAZid);
			}
		}
		log.info("Reading household data... Done.");
		
		int stayHomePlansCounter = 0;
		for (Person person : scenario.getPopulation().getPersons().values()) {
			if (person.getPlans().size() == 0) {
				// give the agent a stay-home plan

				Coord coord = getRandomCoord(hhId2TazId.get(person.getAttributes().getAttribute("householdId")), geometries);
				Activity act = populationFactory.createActivityFromCoord("home", coord);
				
				Plan plan = populationFactory.createPlan();
				plan.addActivity(act);
				person.addPlan(plan);
				
				stayHomePlansCounter++;
			}
		}
		
		log.info("Number of stay-home plans: " + stayHomePlansCounter);
		
		log.info("Handling stay home plans... Done.");
		
		log.info("Writing population...");
		PopulationWriter writer = new PopulationWriter(scenario.getPopulation());
		writer.write(outputDirectory + outputFilePrefix + ".xml.gz");
		log.info("Writing population... Done.");	
	}

	private Coord getRandomCoord(String tazId, Map<String, Geometry> geometries) {
		Geometry tazGeometry;
		if (geometries.get(tazId) == null) {
			throw new RuntimeException("TAZ " + tazId + " is not in shape file. Aborting...");
		} else {
			tazGeometry = geometries.get(tazId);
		}
		
		Point p = null;
        double x, y;
        do {
            x = tazGeometry.getEnvelopeInternal().getMinX() + rnd.nextDouble()
                    * (tazGeometry.getEnvelopeInternal().getMaxX() - tazGeometry.getEnvelopeInternal().getMinX());
            y = tazGeometry.getEnvelopeInternal().getMinY() + rnd.nextDouble()
                    * (tazGeometry.getEnvelopeInternal().getMaxY() - tazGeometry.getEnvelopeInternal().getMinY());
            p = MGC.xy2Point(x, y);
        }
        while (!tazGeometry.contains(p));
		
		Coord coord = MGC.point2Coord(p);
		return coord;
	}

	private static String getGenderString(int genderCode) {
		switch (genderCode) {
		  case 1:
			  return "male";
		  case 2:
			  return "female";
		}
		throw new RuntimeException("Unknown gender. Aborting...");
	}

	private static String getModeString(Integer modeCode) {
		switch (modeCode) {
		  case 1:
			  // SOV
			  return "car";
		  case 2:
			  // HOV2Dr
			  return "car";
		  case 3:
			  // HOV3Dr
			  return "car";
		  case 4:
			  // HOVPass
			  return "ride";		   
		  case 5:
			  // Walk_Bus
			  return "pt";
		  case 6:
			  // KNR_Bus
			  return "pt";
		  case 7:
			  // PNR_Bus
			  return "pt";
		  case 8:
			  // Walk_Rail
			  return "pt";
		  case 9:
			  // KNR_Rail
			  return "pt";
		  case 10:
			  // PNR_Rail
			  return "pt";
		  case 11:
			  return "walk";
		  case 12:
			  return "bike";
		  case 13:
			  return "ride_taxi";
		  case 14:
			  // School_Bus
			  return "school_bus";
		}
		throw new RuntimeException("Unknown trip mode. Aborting...");
	}

	private static String getTripPurposeString(int tripPurposeOriginCode) {
		switch (tripPurposeOriginCode) {
		  case 0:
			  return "home";
		  case 1:
			  return "work";
		  case 2:
			  return "university";
		  case 3:
			  return "school";
		  case 4:
			  return "escort";		   
		  case 41:
			  return "schoolescort";		    
		  case 411:
			  return "schoolpureescort";
		  case 412:
			  return "schoolridesharing";
		  case 42:
			  return "non-schoolescort";
		  case 5:
			  return "shop";
		  case 6:
			  return "maintenance";
		  case 61:
			  return "HHmaintenance";
		  case 62:
			  return "personalmaintenance";
		  case 7:
			  return "eatout";
		  case 71:
			  return "eatoutbreakfast";
		  case 72:
			  return "eatoutlunch";
		  case 73:
			  return "eatoutdinner";
		  case 8:
			  return "visiting";
		  case 9:
			  return "discretionary";
		  case 10:
			  return "specialevent";
		  case 11:
			  return "atwork";
		  case 12:
			  return "atworkbusiness";
		  case 13:
			  return "atworklunch";
		  case 14:
			  return "atworkother";
		  case 15:
			  return "business";
		}
		throw new RuntimeException("Unknown trip purpose code. Aborting...");
	}
	
	private static Map<String, Geometry> loadGeometries(String shapeFile, String idHeader) {
		Map<String, Geometry> zones = new HashMap<>();
        if (shapeFile != null) {
            Collection<SimpleFeature> features;
        	features = ShapeFileReader.getAllFeatures(shapeFile);
        	for (SimpleFeature feature : features) {
    			String id = feature.getAttribute(idHeader).toString();
    			Geometry geometry = (Geometry) feature.getDefaultGeometry();
    			zones.put(id, geometry);
    		}
        }
        return zones;
	}

}

