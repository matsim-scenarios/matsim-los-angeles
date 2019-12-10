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

import java.io.BufferedReader;
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
import org.matsim.api.core.v01.population.PlanElement;
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
	private final double timeBinSizeForDurationBasedActivityTypes = 600.;
	private final double useDurationInsteadOfEndTimeThreshold = 7200.;

	private final double sample = 0.1;
	private final String outputFilePrefix = "los-angeles-v1.0-population-" + sample + "_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date());
	
	private int freightTripCounter = 0;
	
	private Map<String, Geometry> internalTierTAZTwo2geometries = new HashMap<>();
	private Map<String, Geometry> internalTierTAZOneForFreight2geometries = new HashMap<>();
	private Map<String, Geometry> externalTierTAZOneForFreight2geometries = new HashMap<>();
	
	private Scenario scenario;
	
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
//		final String householdFile = rootDirectory + "LA012.2013-20_SCAG/test-data/test_households.csv";
//		final String expandPPFile = rootDirectory + "LA012.2013-20_SCAG/test-data/test_pp.csv";
//		final String expandHHFile = rootDirectory + "LA012.2013-20_SCAG/test-data/test_hh.csv";
//		
//		final String freightTripTableAM = rootDirectory + "LA012.2013-20_SCAG/test-data/test_AM_OD_Trips_Table.csv";
//		final String freightTripTableEVE = rootDirectory + "LA012.2013-20_SCAG/test-data/test_EVE_OD_Trips_Table.csv";
//		final String freightTripTableMD = rootDirectory + "LA012.2013-20_SCAG/test-data/test_MD_OD_Trips_Table.csv";
//		final String freightTripTableNT = rootDirectory + "LA012.2013-20_SCAG/test-data/test_NT_OD_Trips_Table.csv";
//		final String freightTripTablePM = rootDirectory + "LA012.2013-20_SCAG/test-data/test_PM_OD_Trips_Table.csv";
		
		final String householdFile = rootDirectory + "LA012.2013-20_SCAG/abm/output_disaggHouseholdList.csv";
		final String personFile = rootDirectory + "LA012.2013-20_SCAG/abm/output_disaggPersonList.csv";
		final String tripFile = rootDirectory + "LA012.2013-20_SCAG/abm/output_disaggTripList.csv";
		final String expandPPFile = rootDirectory + "LA012.2013-20_SCAG/popsyn/expand_pp.csv";
		final String expandHHFile = rootDirectory + "LA012.2013-20_SCAG/popsyn/expand_hh.csv";
		final String internalSeqTAZtoTAZ12bMappingFile = rootDirectory + "LA012.2013-20_SCAG/abm/TAZEQCOUNTY_TIER2.csv";
		
		final String freightTripTableAM = rootDirectory + "LA012c/AM_OD_Trips_Table.csv";
		final String freightTripTableEVE = rootDirectory + "LA012c/EVE_OD_Trips_Table.csv";
		final String freightTripTableMD = rootDirectory + "LA012c/MD_OD_Trips_Table.csv";
		final String freightTripTableNT = rootDirectory + "LA012c/NT_OD_Trips_Table.csv";
		final String freightTripTablePM = rootDirectory + "LA012c/PM_OD_Trips_Table.csv";
		
		final String freightZoneCoordinatesShapeFile = rootDirectory + "LA012c/SCAG_T1_external_Airport_Seaport_EPSG3310/SCAG_T1_external_Airport_Seaport_EPSG3310.shp";
		final String tazShpFile = rootDirectory + "LA012.2013-20_SCAG/shp-files/Tier_2_Transportation_Analysis_Zones_TAZs_in_SCAG_EPSG3310/Tier_2_Transportation_Analysis_Zones_TAZs_in_SCAG_EPSG3310.shp";
		
		final String outputDirectory = rootDirectory + "matsim-input-files/population/";
		
		OutputDirectoryLogging.catchLogEntries();
		try {
			OutputDirectoryLogging.initLoggingWithOutputDirectory(outputDirectory);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		log.info("Loading shape files...");
		internalTierTAZTwo2geometries = loadGeometries(tazShpFile, "ID_TAZ12b"); // (=Tier2) geometry IDs given in household and trip file
		internalTierTAZOneForFreight2geometries = loadGeometries(tazShpFile, "ID_TAZ12a"); // (=Tier1) geometry IDs given in the freight trip table
		externalTierTAZOneForFreight2geometries = loadGeometries(freightZoneCoordinatesShapeFile, "TIER1TAZ"); //
		log.info("Loading shape files... Done.");
		
		log.info("Reading TAZ ID mapping file...");
		final Map<String, String> internalSeqTAZtoTAZ12b = new HashMap<>();
		for (CSVRecord csvRecord : new CSVParser(Files.newBufferedReader(Paths.get(internalSeqTAZtoTAZ12bMappingFile)), csvFormat)) {	
			String idInternalSeqTAZ = csvRecord.get(9);
			String idTAZ12b = csvRecord.get(0);
			internalSeqTAZtoTAZ12b.put(idInternalSeqTAZ, idTAZ12b);
		}
		log.info("Reading TAZ ID mapping file... Done.");
		
		log.info("Creating scenario...");
				
		scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());	
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
		Map<String, Set<Id<Person>>> householdId2PersonIds = new HashMap<>();
		for (CSVRecord csvRecord : new CSVParser(Files.newBufferedReader(Paths.get(personFile)), csvFormat)) {	
			personsInDataSet++;
			
			if (rnd.nextDouble() <= sample) {
				String personId = csvRecord.get(0);
				String householdId = csvRecord.get(2);
				householdIdsOfIncludedPersons.add(householdId);
				if (householdId2PersonIds.containsKey(householdId)) {
					Set<Id<Person>> personIds = householdId2PersonIds.get(householdId);
					if (!personIds.contains(Id.createPersonId(personId))) personIds.add(Id.createPersonId(personId));
				} else {
					Set<Id<Person>> personIds = new HashSet<Id<Person>>();
					personIds.add(Id.createPersonId(personId));
					householdId2PersonIds.put(householdId, personIds);
				}
				String age = csvRecord.get(4);
				int genderCode = Integer.valueOf(csvRecord.get(5));
				String genderString = getGenderString(genderCode);

				Person person = populationFactory.createPerson(Id.createPersonId(personId));
				person.getAttributes().putAttribute("subpopulation", "person");
				person.getAttributes().putAttribute("householdId", householdId);
				person.getAttributes().putAttribute("age", age);
				person.getAttributes().putAttribute("gender", genderString);
				
				scenario.getPopulation().addPerson(person);
				includedPersons++;
			} else {
				excludedPersons++;
			}
		}
		
		log.info("Adding person attributes from expand_pp file...");
		// add more person attributes from expand_pp file
		for (CSVRecord csvRecord : new CSVParser(Files.newBufferedReader(Paths.get(expandPPFile)), csvFormat)) {
			Id<Person> personId = Id.createPersonId(csvRecord.get(0));
			Person person = scenario.getPopulation().getPersons().get(personId);
			if (person != null) {
				int raceCode = Integer.valueOf(csvRecord.get(6));
				String race = getRaceString(raceCode);
				String esrCodeString = csvRecord.get(7);
				int workerCode = Integer.valueOf(csvRecord.get(8));
				String worker = getWorkerString(workerCode);
				String wkind20CodeString = csvRecord.get(9);
				String wkocc24CodeString = csvRecord.get(10);
				String schgCodeString = csvRecord.get(11);
				String eduattCodeString = csvRecord.get(12);
				
				person.getAttributes().putAttribute("race", race);
				person.getAttributes().putAttribute("ESR", esrCodeString);
				person.getAttributes().putAttribute("worker", worker);
				person.getAttributes().putAttribute("wkind20", wkind20CodeString);
				person.getAttributes().putAttribute("wkocc24", wkocc24CodeString);
				person.getAttributes().putAttribute("schg", schgCodeString);
				person.getAttributes().putAttribute("eduatt", eduattCodeString);
			}
		}
		
		// add more person attributes from expand_hh file
		log.info("Adding more person attributes from expand_hh file...");
		for (CSVRecord csvRecord : new CSVParser(Files.newBufferedReader(Paths.get(expandHHFile)), csvFormat)) {
			String householdId = csvRecord.get(0);
			if (householdIdsOfIncludedPersons.contains(householdId)) {
				for (Id<Person> personId: householdId2PersonIds.get(householdId)) {
					Person person = scenario.getPopulation().getPersons().get(personId);
					
					String tenCodeString = csvRecord.get(10);
					String hhSizeCodeString = csvRecord.get(7);
					String hhIncomeCodeString = csvRecord.get(8);
					int hTypeCode = Integer.valueOf(csvRecord.get(9));
					String hType = getHHTypeString(hTypeCode);
					
					person.getAttributes().putAttribute("ten", tenCodeString);
					person.getAttributes().putAttribute("hhsize", hhSizeCodeString);
					person.getAttributes().putAttribute("hhinc", hhIncomeCodeString);
					person.getAttributes().putAttribute("htype", hType);
				}
			}
		}
		
		// add more person attributes (auto ownership) from household file
		log.info("Adding more person attributes from household file...");
		for (CSVRecord csvRecord : new CSVParser(Files.newBufferedReader(Paths.get(householdFile)), csvFormat)) {
			String householdId = csvRecord.get(0);
			if (householdIdsOfIncludedPersons.contains(householdId)) {
				for (Id<Person> personId: householdId2PersonIds.get(householdId)) {
					Person person = scenario.getPopulation().getPersons().get(personId);
					
					String hhNumAutos = csvRecord.get(4);
					
					person.getAttributes().putAttribute("hhnumautos", hhNumAutos);
				}
			}
		}
		
		log.info("Creating persons... Done.");
		
		log.info("Included persons: " + includedPersons);
		log.info("Excluded persons: " + excludedPersons);
		log.info("Total number of persons in data set: " + personsInDataSet);
		
		log.info("Creating plans...");	
		// read trip data and create a plan
		
		// some statistics
		int tripsInDataSet = 0;
		int includedTripsCounter = 0;
		int excludedTripsCounter = 0;
		int warnCounterActivityDuration = 0;
		int warnCounterTripTime = 0;
		
		// for consistency checks
		int personTripsCounter = 0;
		int personTripNumber = 0;
		int previousTripDestination = 0;
		double previousTripStartTime = 0.;
		
		final Map<Id<Person>, Coord> personId2homeCoord = new HashMap<>();
		final Map<Id<Person>, String> personId2homeZoneId = new HashMap<>();
		
		boolean firstTrip;
		BufferedReader br = Files.newBufferedReader(Paths.get(tripFile));
		CSVParser csvParser = new CSVParser(br,csvFormat);
		for (CSVRecord csvRecord : csvParser) {	
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
					// check if persTripNum in trip dataset matches actual trip counter for each person
					personTripsCounter++;
					personTripNumber = Integer.valueOf(csvRecord.get(6));
					if (personTripsCounter != personTripNumber) {
						csvParser.close();
						throw new RuntimeException("Current trip counter for person " + personId + " is not equal to persTripNum. Aborting..." + csvRecord);
					}
					plan = person.getPlans().get(0);
					firstTrip = false;
				} else {
					plan = populationFactory.createPlan();
					firstTrip = true;
					person.addPlan(plan);
					personTripsCounter = 1;
				}
				
				if (firstTrip) {	
					// origin activity
					
					int tripPurposeOriginCode = Integer.valueOf(csvRecord.get(18));
					String tripPurposeOrigin = getTripPurposeString(tripPurposeOriginCode);		
					String tripOriginInternalSeqTAZid = csvRecord.get(20);
					String tripOriginIdTAZ12b = internalSeqTAZtoTAZ12b.get(tripOriginInternalSeqTAZid);
					if (tripOriginIdTAZ12b == null) {
						csvParser.close();
						throw new RuntimeException("Can't identify TAZ (Tier2) based on internal sequence TAZ: " + tripOriginInternalSeqTAZid + " Aborting... " + csvRecord);
					}
					Geometry geometry;	
					if (internalTierTAZTwo2geometries.get(tripOriginIdTAZ12b) == null) {
						csvParser.close();
						throw new RuntimeException("Geometry with ID " + tripOriginIdTAZ12b + " is not in the Tier TAZ2 zone file.");
					} else {
						geometry = internalTierTAZTwo2geometries.get(tripOriginIdTAZ12b);
					}
					Coord coord = getRandomCoord(geometry);
					
					// store the home coordinate for that person to make sure all other home activities have the exact same coordinate
					if(tripPurposeOrigin.startsWith("home")) {
						personId2homeCoord.put(personId, coord);
						personId2homeZoneId.put(personId, tripOriginIdTAZ12b);
					}
					
					Activity act = populationFactory.createActivityFromCoord(tripPurposeOrigin, coord);
					act.getAttributes().putAttribute("zoneId", tripOriginIdTAZ12b);
					plan.addActivity(act);
				}
				
				double tripStartTime = Double.valueOf(csvRecord.get(23)) * 60.;
				double previousActivityEndTime = tripStartTime;

				// set end time of previous activity
				Activity previousActivity = (Activity) plan.getPlanElements().get(plan.getPlanElements().size() - 1);
				previousActivity.getAttributes().putAttribute("initialEndTime", previousActivityEndTime);

				if (previousActivity.getAttributes().getAttribute("initialStartTime") != null) {
					double previousActivityStartTime = (double) previousActivity.getAttributes().getAttribute("initialStartTime");
					double activityDuration = previousActivityEndTime - previousActivityStartTime;
					
					if (activityDuration <= useDurationInsteadOfEndTimeThreshold) {
						final double minimumDuration = 300.;
						if (activityDuration <= 0.) {
							if (warnCounterActivityDuration <= 5) log.warn("------------------------------------");
							if (warnCounterActivityDuration <= 5) log.warn("Activity duration of person is " + activityDuration + " which is either 0 or below 0.");
							if (warnCounterActivityDuration <= 5) log.warn("Trip record: " + csvRecord);
							if (warnCounterActivityDuration <= 5) log.warn("Setting the activity duration to a minimum value of: " + minimumDuration);
							if (warnCounterActivityDuration <= 5) log.warn("------------------------------------");
							if (warnCounterActivityDuration == 5) log.warn("Further types of this warning will not be printed.");
							warnCounterActivityDuration++;
							activityDuration = minimumDuration;
						}
						// use the duration instead of the end time
						previousActivity.setMaximumDuration(activityDuration);
						previousActivity.setEndTime(Double.NEGATIVE_INFINITY);
					} else {
						// use the end time
						previousActivity.setEndTime(previousActivityEndTime);
					}
				} else {
					// the first activity does not have a start time, thus use the end time
					previousActivity.setEndTime(previousActivityEndTime);
				}
				
				// check start_time of second trip is larger than start_time of previous trip
				if (!firstTrip) {
					if (previousTripStartTime >= tripStartTime) {
						if (warnCounterTripTime <= 5) log.warn("------------------------------------");
						if (warnCounterTripTime <= 5) log.warn("Trip start time in wrong order: " + csvRecord);
						if (warnCounterTripTime == 5) log.warn("Further types of this warning will not be printed.");
						if (warnCounterTripTime <= 5) log.warn("------------------------------------");
						warnCounterTripTime++;
					}
				}
				
				// check end location of previous trip is the same as the start location of the second trip
				if (!firstTrip) {
					int currentTripOrigin = Integer.valueOf(csvRecord.get(20));
					if (currentTripOrigin != previousTripDestination) {
						csvParser.close();
						throw new RuntimeException("Previous trip destination is not the same as the current trip origin.");
					}
				}
				
				// trip
				
				double tripEndTime = Double.valueOf(csvRecord.get(40)) * 60.;
				double travelTime = tripEndTime - tripStartTime;
				
				if (travelTime < 0.) {
					csvParser.close();
					throw new RuntimeException("Travel time is < 0. Aborting..." + csvRecord);
				}
				
				if (tripStartTime < 0. || tripEndTime < 0.) {
					csvParser.close();
					throw new RuntimeException("Trip start or and time is < 0. Aborting..." + csvRecord);
				}
				
				String mode = getModeString(Integer.valueOf(csvRecord.get(25)));
				Leg leg = populationFactory.createLeg(mode);	
				leg.setTravelTime(travelTime);
				plan.addLeg(leg);
				
				// destination activity
				
				String tripPurposeDestination = getTripPurposeString(Integer.valueOf(csvRecord.get(19)));
				String tripDestinationInternalSeqTAZid = csvRecord.get(21);
				String tripDestinationIdTAZ12b = internalSeqTAZtoTAZ12b.get(tripDestinationInternalSeqTAZid);
				if (tripDestinationIdTAZ12b == null) {
					csvParser.close();
					throw new RuntimeException("Can't identify TAZ (Tier2) based on internal sequence TAZ: " + tripDestinationInternalSeqTAZid + " Aborting... " + csvRecord);
				}
				Coord coord = null;
				if (tripPurposeDestination.startsWith("home")) {
					if (personId2homeCoord.get(personId) != null && personId2homeZoneId.get(personId).equals(tripDestinationIdTAZ12b)) {
						// get previous home coordinate
						coord = personId2homeCoord.get(personId);
					} else {
						// get random home coordinate
						Geometry geometry;	
						if (internalTierTAZTwo2geometries.get(tripDestinationIdTAZ12b) == null) {
							csvParser.close();
							throw new RuntimeException("Geometry with ID " + tripDestinationIdTAZ12b + " is not in the Tier TAZ2 zone file.");
						} else {
							geometry = internalTierTAZTwo2geometries.get(tripDestinationIdTAZ12b);
						}
						coord = getRandomCoord(geometry);
						
						if (personId2homeCoord.get(personId) == null) {
							personId2homeCoord.put(personId, coord);
						} else {
							log.warn("Person " + personId + " has different home activity zone IDs in different home activities.");
						}
					}
				} else {
					// compute new random coordinate for all non-home activities
					Geometry geometry;	
					if (internalTierTAZTwo2geometries.get(tripDestinationIdTAZ12b) == null) {
						csvParser.close();
						throw new RuntimeException("Geometry with ID " + tripDestinationIdTAZ12b + " is not in the Tier TAZ2 zone file.");
					} else {
						geometry = internalTierTAZTwo2geometries.get(tripDestinationIdTAZ12b);
					}
					coord = getRandomCoord(geometry);
				}
				
				Activity act = populationFactory.createActivityFromCoord(tripPurposeDestination, coord);
				act.getAttributes().putAttribute("initialStartTime", tripEndTime);
				act.getAttributes().putAttribute("zoneId", tripDestinationIdTAZ12b);
				plan.addActivity(act);
				previousTripDestination = Integer.valueOf(csvRecord.get(21));
				previousTripStartTime = tripStartTime;
			}
		}
		csvParser.close();
		
		log.info("Included trips: " + includedTripsCounter);
		log.info("Excluded trips: " + excludedTripsCounter);
		log.info("Total number of trips in data set: " + tripsInDataSet);
		log.info("Number of activities with negative duration: " + warnCounterActivityDuration);
		log.info("Number of trips with start time after start time of previous trip: " + warnCounterTripTime);
		
		log.info("Creating plans... Done.");
		log.info("Creating population... Done.");
		
		log.info("Handling stay home plans...");
		// Get the person's home location via the household ID.
		log.info("Reading household data...");
		Map<String, String> hhId2tierTazId = new HashMap<>();
		for (CSVRecord csvRecord : new CSVParser(Files.newBufferedReader(Paths.get(householdFile)), csvFormat)) {	
			String householdId = csvRecord.get(0);
			if (householdIdsOfIncludedPersons.contains(householdId)) {
				String hhTAZid = csvRecord.get(2);
				hhId2tierTazId.put(householdId, hhTAZid);
			}
		}
		log.info("Reading household data... Done.");
		
		int stayHomePlansCounter = 0;
		for (Person person : scenario.getPopulation().getPersons().values()) {
			if (person.getPlans().size() == 0) {
				// give the agent a stay-home plan

				String hhId = (String) person.getAttributes().getAttribute("householdId");
				String hhZoneId = hhId2tierTazId.get(hhId);
				Geometry geometry;	
				if (internalTierTAZTwo2geometries.get(hhZoneId) == null) {
					throw new RuntimeException("Geometry with ID " + hhZoneId + " is not in the Tier TAZ2 zone file.");
				} else {
					geometry = internalTierTAZTwo2geometries.get(hhZoneId);
				}
				Coord coord = getRandomCoord(geometry);
				Activity act = populationFactory.createActivityFromCoord("home", coord);
				act.getAttributes().putAttribute("zoneId", hhZoneId); // TAZ_Tier2
				
				Plan plan = populationFactory.createPlan();
				plan.addActivity(act);
				person.addPlan(plan);
				
				stayHomePlansCounter++;
			}
		}
		
		log.info("Number of stay-home plans: " + stayHomePlansCounter);
		log.info("Handling stay home plans... Done.");
		
		// now define duration-specific activities
		log.info("1) Setting activity types according to duration (time bin size: " + timeBinSizeForDurationBasedActivityTypes + ").");				
		log.info("2) Merging evening and morning activity if they have the same (base) type.");
		for (Person person : scenario.getPopulation().getPersons().values()) {
			for (Plan plan : person.getPlans()) {				
				setActivityTypesAccordingToDuration(plan, timeBinSizeForDurationBasedActivityTypes);
				mergeOvernightActivities(plan);
			}
		}
		
		log.info("Reading freight trip tables and generate freight agents...");
		addFreightAgents(freightTripTableAM, 6. * 3600, 9. * 3600);
		addFreightAgents(freightTripTableMD, 9. * 3600, 15. * 3600);
		addFreightAgents(freightTripTablePM, 15. * 3600, 19. * 3600);
		addFreightAgents(freightTripTableEVE, 19. * 3600, 21. * 3600);
		addFreightAgents(freightTripTableNT, 21. * 3600, 6. * 3600);
		log.info("Number of freight trips: " + freightTripCounter);
		log.info("Reading freight trip tables and generate freight agents... Done.");

		log.info("Writing population...");
		PopulationWriter writer = new PopulationWriter(scenario.getPopulation());
		writer.write(outputDirectory + outputFilePrefix + ".xml.gz");
		log.info("Writing population... Done.");	
	}

	private void addFreightAgents(String freightTripFile, double fromTime, double toTime) throws IOException {
				
		for (CSVRecord csvRecord : new CSVParser(Files.newBufferedReader(Paths.get(freightTripFile)), csvFormat)) {	
			String fromZoneId = csvRecord.get(0);
			String toZoneId = csvRecord.get(1);
			int lhdt = (int) Math.round(Double.valueOf(csvRecord.get(5)));
			int mhdt = (int) Math.round(Double.valueOf(csvRecord.get(6)));
			int hhdt = (int) Math.round(Double.valueOf(csvRecord.get(7)));
			
			generateTrips("LHDT", lhdt, fromZoneId, toZoneId, fromTime, toTime);
			generateTrips("MHDT", mhdt, fromZoneId, toZoneId, fromTime, toTime);
			generateTrips("HHDT", hhdt, fromZoneId, toZoneId, fromTime, toTime);
		}
	}

	private void generateTrips(
			String name, 
			int trips,
			String fromZoneId,
			String toZoneId,
			double fromTime,
			double toTime) {
		
		final PopulationFactory populationFactory = scenario.getPopulation().getFactory();

		for (int trip = 0; trip < trips; trip++) {
			if (rnd.nextDouble() <= sample) {
					
				Coord coordFrom;
				if (internalTierTAZOneForFreight2geometries.get(fromZoneId) == null) {		
					if (externalTierTAZOneForFreight2geometries.get(fromZoneId) == null) {
						throw new RuntimeException("Geometry with ID " + fromZoneId + " is neither in internal nor the external Tier TAZ1 zone file. Aborting...");
					} else {
						Geometry geometry = externalTierTAZOneForFreight2geometries.get(fromZoneId);
						coordFrom = getRandomCoordAroundGeometry(geometry, 1000.);
					}	
				} else {
					Geometry geometry = internalTierTAZOneForFreight2geometries.get(fromZoneId);
					coordFrom = getRandomCoord(geometry);
				}
				
				Coord coordTo;
				if (internalTierTAZOneForFreight2geometries.get(toZoneId) == null) {		
					if (externalTierTAZOneForFreight2geometries.get(toZoneId) == null) {
						throw new RuntimeException("Geometry with ID " + toZoneId + " is neither in internal nor the external Tier TAZ1 zone file. Aborting...");
					} else {
						Geometry geometry = externalTierTAZOneForFreight2geometries.get(toZoneId);
						coordTo = getRandomCoordAroundGeometry(geometry, 1000.);
					}	
				} else {
					Geometry geometry = internalTierTAZOneForFreight2geometries.get(toZoneId);
					coordTo = getRandomCoord(geometry);
				}
				
				if (coordFrom != null && coordTo != null) {
					String personId = "freight_" +  name + "_" + fromZoneId + "_" + toZoneId + "_" + freightTripCounter;
					Person person = populationFactory.createPerson(Id.createPersonId(personId));
					person.getAttributes().putAttribute("subpopulation", "freight");

					Plan plan = populationFactory.createPlan();

					Activity fromAct = populationFactory.createActivityFromCoord("freightStart", coordFrom);
					fromAct.getAttributes().putAttribute("zoneId", fromZoneId);
					
					if (toTime < fromTime) {
						toTime = toTime + (24 * 3600.);
					}
					double tripStartTime = fromTime + (toTime - fromTime) * rnd.nextDouble();
					if (tripStartTime > (24 * 3600.)) {
						tripStartTime = tripStartTime - (24 * 3600.);
					}
					
					if (tripStartTime < 0.) throw new RuntimeException("freight trip start time is < 0. Aborting...");
					
					fromAct.setEndTime(tripStartTime);
					plan.addActivity(fromAct);
					
					Leg leg = populationFactory.createLeg("freight");	
					plan.addLeg(leg);
					
					Activity toAct = populationFactory.createActivityFromCoord("freightEnd", coordTo);
					toAct.getAttributes().putAttribute("zoneId", toZoneId);
					plan.addActivity(toAct);
					
					person.addPlan(plan);
					scenario.getPopulation().addPerson(person);	
					
					freightTripCounter++;
				} else {
					throw new RuntimeException("Couldn't add freight trip because of missing geometry information.");
				}
				
			} else {
				// skip trip
			}
		}
	}

	private Coord getRandomCoordAroundGeometry(Geometry geometry, double buffer) {		
		Point point = geometry.getCentroid();
		double x = point.getX() + (rnd.nextDouble() - 1.0) * buffer;
		double y = point.getY() + (rnd.nextDouble() - 1.0) * buffer;
		return new Coord(x,y);
	}

	private Coord getRandomCoord(Geometry tazGeometry) {
		
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
	
	private static String getRaceString(int raceCode) {
		switch (raceCode) {
		  case 1:
			  return "HP";
		  case 2:
			  return "NHW";
		  case 3:
			  return "NHB";
		  case 4:
			  return "NHAI";
		  case 5:
			  return "NHAS";
		  case 6:
			  return "NHO";
		}
		throw new RuntimeException("Unkonwn race. Aborting...");
	}
	
	private static String getWorkerString(int workerCode) {
		switch (workerCode) {
		  case 1:
			  return "employed";
		  case 2:
			  return "unemployed";
		}
		throw new RuntimeException("Unknown worker status. Aborting...");
	}
	
	private static String getHHTypeString(int hhTypeCode) {
		switch (hhTypeCode) {
		  case 1:
			  return "SFD";
		  case 2:
			  return "SFA";
		  case 3:
			  return "Multiple";
		  case 4:
			  return "Others";
		}
		throw new RuntimeException("Unknown hhtype. Aborting...");
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
			  return "ride_school_bus";
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
	
	private static void setActivityTypesAccordingToDuration(Plan plan, double timeCategorySize) {		
		for (PlanElement pE : plan.getPlanElements()) {				
			if (pE instanceof Activity) {				
				Activity act = (Activity) pE;
				
				double startTime = Double.NEGATIVE_INFINITY;
				double endTime = Double.NEGATIVE_INFINITY;
			
				if (act.getAttributes().getAttribute("initialStartTime") == null) {
					startTime = 0.;
				} else {
					startTime = (double) act.getAttributes().getAttribute("initialStartTime");
				}
				
				if (act.getAttributes().getAttribute("initialEndTime") == null) {
					endTime = 24 * 3600.;
				} else {
					endTime = (double) act.getAttributes().getAttribute("initialEndTime");
				} 
						
				int durationCategoryNr = (int) Math.round( ((endTime - startTime) / timeCategorySize) ) ;		
				if (durationCategoryNr <= 0) {
					durationCategoryNr = 1;
				}
				
				String newType = act.getType() + "_" + (int) (durationCategoryNr * timeCategorySize);
				act.setType(newType);				
			}
		}
	}
	
	private static void mergeOvernightActivities(Plan plan) {	
		if (plan.getPlanElements().size() > 1) {	
		
			Activity firstActivity = (Activity) plan.getPlanElements().get(0);
			Activity lastActivity = (Activity) plan.getPlanElements().get(plan.getPlanElements().size() - 1);
			
			String firstBaseActivity = firstActivity.getType().split("_")[0];
			String lastBaseActivity = lastActivity.getType().split("_")[0];
			
			if (firstBaseActivity.equals(lastBaseActivity)) {		
				int mergedDuration = Integer.parseInt(firstActivity.getType().split("_")[1]) + Integer.parseInt(lastActivity.getType().split("_")[1]);			
				firstActivity.setType(firstBaseActivity + "_" + mergedDuration);
				lastActivity.setType(lastBaseActivity + "_" + mergedDuration);
			}		
		}
	}

}

