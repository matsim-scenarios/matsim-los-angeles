/* *********************************************************************** *
 * project: org.matsim.*
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

/**
 * 
 */
package org.matsim.prepare;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.MatsimVehicleWriter;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;


/**
 * 
 * Merges several schedule files...
 * 
 * @author ikaddoura
 *
 */
public class MergeTransitFiles {
	
	private final Logger log = Logger.getLogger(MergeTransitFiles.class);

	private final String outputDir;
	private final String networkCS ;

	private String outnetworkPrefix ;
	
	public static void main(String[] args) {
		String rootDirectory = null;
		
		if (args.length == 1) {
			rootDirectory = args[0];
		} else {
			throw new RuntimeException("Please set the root directory (the directory above 'scag_model'). Aborting...");
		}
		
		if (!rootDirectory.endsWith("/")) rootDirectory = rootDirectory + "/";
				
		String prefix = "scag-merged-" + new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		String outDir = rootDirectory + "/matsim-input-files/pt/";
		
		String transitScheduleFile1 = rootDirectory + "/matsim-input-files/pt/LA_DOT_2019-07-12/scag-transitSchedule_2019-11-01.xml.gz";
		String transitScheduleFile2 = rootDirectory + "/matsim-input-files/pt/LA_GO_BUS_2019-10-02/scag-transitSchedule_2019-11-01.xml.gz";
		String transitScheduleFile3 = rootDirectory + "/matsim-input-files/pt/LA_METRO_BUS_2019-10-04/scag-transitSchedule_2019-11-01.xml.gz";
		String transitScheduleFile4 = rootDirectory + "/matsim-input-files/pt/LA_METRO_RAIL_2019-10-29/scag-transitSchedule_2019-11-01.xml.gz";

		String transitVehiclesFile1 = rootDirectory + "/matsim-input-files/pt/LA_DOT_2019-07-12/scag-transitVehicles_2019-11-01.xml.gz";
		String transitVehiclesFile2 = rootDirectory + "/matsim-input-files/pt/LA_GO_BUS_2019-10-02/scag-transitVehicles_2019-11-01.xml.gz";
		String transitVehiclesFile3 = rootDirectory + "/matsim-input-files/pt/LA_METRO_BUS_2019-10-04/scag-transitVehicles_2019-11-01.xml.gz";
		String transitVehiclesFile4 = rootDirectory + "/matsim-input-files/pt/LA_METRO_RAIL_2019-10-29/scag-transitVehicles_2019-11-01.xml.gz";

		String crs = "EPSG:3310";
		MergeTransitFiles ptMerger = new MergeTransitFiles(crs, outDir, prefix);
		
		Config config = ConfigUtils.createConfig();
		config.transit().setUseTransit(true);
		Scenario scenarioBase = ScenarioUtils.loadScenario(config);
		
		TransitSchedule baseTransitSchedule = scenarioBase.getTransitSchedule();
		Vehicles baseTransitVehicles = scenarioBase.getTransitVehicles();
		
		Scenario scenario1 = ptMerger.loadScenario(transitScheduleFile1, transitVehiclesFile1);
		Scenario scenario2 = ptMerger.loadScenario(transitScheduleFile2, transitVehiclesFile2);
		Scenario scenario3 = ptMerger.loadScenario(transitScheduleFile3, transitVehiclesFile3);
		Scenario scenario4 = ptMerger.loadScenario(transitScheduleFile4, transitVehiclesFile4);

		MergeTransitFiles.mergeSchedule(baseTransitSchedule, "LA_DOT", scenario1.getTransitSchedule());
		MergeTransitFiles.mergeVehicles(baseTransitVehicles, scenario1.getTransitVehicles());

		MergeTransitFiles.mergeSchedule(baseTransitSchedule, "LA_GO_BUS", scenario2.getTransitSchedule());
		MergeTransitFiles.mergeVehicles(baseTransitVehicles, scenario2.getTransitVehicles());

		MergeTransitFiles.mergeSchedule(baseTransitSchedule, "LA_METRO_BUS", scenario3.getTransitSchedule());
		MergeTransitFiles.mergeVehicles(baseTransitVehicles, scenario3.getTransitVehicles());

		MergeTransitFiles.mergeSchedule(baseTransitSchedule, "LA_METRO_RAIL", scenario4.getTransitSchedule());
		MergeTransitFiles.mergeVehicles(baseTransitVehicles, scenario4.getTransitVehicles());

		ptMerger.writeFiles(baseTransitSchedule, baseTransitVehicles);
	}
	
	private static void mergeVehicles(Vehicles baseTransitVehicles, Vehicles transitVehicles) {
		for (VehicleType vehicleType : transitVehicles.getVehicleTypes().values()) {
			VehicleType vehicleType2 = baseTransitVehicles.getFactory().createVehicleType(vehicleType.getId());
			vehicleType2.setNetworkMode(vehicleType.getNetworkMode());
			vehicleType2.setPcuEquivalents(vehicleType.getPcuEquivalents());
			vehicleType2.setDescription(vehicleType.getDescription());
			vehicleType2.getCapacity().setSeats(vehicleType.getCapacity().getSeats());
			
			baseTransitVehicles.addVehicleType(vehicleType2);
		}
		
		for (Vehicle vehicle : transitVehicles.getVehicles().values()) {
			Vehicle vehicle2 = baseTransitVehicles.getFactory().createVehicle(vehicle.getId(), vehicle.getType());
			baseTransitVehicles.addVehicle(vehicle2);
		}
	}

	private void writeFiles(TransitSchedule baseTransitSchedule, Vehicles transitVehicles) {
		String outSchedule = this.outputDir + outnetworkPrefix + "_transitSchedule.xml.gz";
		String outVehicles = this.outputDir + outnetworkPrefix + "_transitVehicles.xml.gz";
		log.info("Writing file to " + outSchedule);
		new TransitScheduleWriter(baseTransitSchedule).writeFile(outSchedule);
		log.info("Writing file to " + outVehicles);
		new MatsimVehicleWriter(transitVehicles).writeFile(outVehicles);
		log.info("... done.");
	}

	private Scenario loadScenario(String scheduleFile, String vehiclesFile) {
		Config config = ConfigUtils.createConfig();
		config.transit().setTransitScheduleFile(scheduleFile);
		config.transit().setVehiclesFile(vehiclesFile);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		return scenario;
	}
	
	/**
	 * Merges two networks into one, by copying all nodes and links from the addNetworks to the baseNetwork.
	 *
	 */
	public static void mergeSchedule(final TransitSchedule baseSchedule, final String id, final TransitSchedule addSchedule) {
		
		for (TransitStopFacility stop : addSchedule.getFacilities().values()) {
			Id<TransitStopFacility> newStopId = Id.create(id + "_" + stop.getId(), TransitStopFacility.class);
			TransitStopFacility stop2 = baseSchedule.getFactory().createTransitStopFacility(newStopId, stop.getCoord(), stop.getIsBlockingLane());
			stop2.setLinkId(stop.getLinkId());
			stop2.setName(stop.getName());
			baseSchedule.addStopFacility(stop2);
		}
		for (TransitLine line : addSchedule.getTransitLines().values()) {
			TransitLine line2 = baseSchedule.getFactory().createTransitLine(Id.create(id + "_" + line.getId(), TransitLine.class));
			
			for (TransitRoute route : line.getRoutes().values()) {
				
				List<TransitRouteStop> stopsWithNewIDs = new ArrayList<>();
				for (TransitRouteStop routeStop : route.getStops()) {
					Id<TransitStopFacility> newFacilityId = Id.create(id + "_" + routeStop.getStopFacility().getId(), TransitStopFacility.class);
					TransitStopFacility stop = baseSchedule.getFacilities().get(newFacilityId);
					stopsWithNewIDs.add(baseSchedule.getFactory().createTransitRouteStop(stop , routeStop.getArrivalOffset(), routeStop.getDepartureOffset()));
				}
				
				TransitRoute route2 = baseSchedule.getFactory().createTransitRoute(route.getId(), route.getRoute(), stopsWithNewIDs, route.getTransportMode());
				route2.setDescription(route.getDescription());
				
				for (Departure departure : route.getDepartures().values()) {
					Departure departure2 = baseSchedule.getFactory().createDeparture(departure.getId(), departure.getDepartureTime());
					departure2.setVehicleId(departure.getVehicleId());
					route2.addDeparture(departure2);
				}
				line2.addRoute(route2);
			}
			baseSchedule.addTransitLine(line2);
		}
	}

	public MergeTransitFiles(String networkCoordinateSystem, String outputDir, String prefix) {
		this.networkCS = networkCoordinateSystem;
		this.outputDir = outputDir.endsWith("/")?outputDir:outputDir+"/";
		this.outnetworkPrefix = prefix;
				
		OutputDirectoryLogging.catchLogEntries();
		try {
			OutputDirectoryLogging.initLoggingWithOutputDirectory(outputDir);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		log.info("--- set the coordinate system for network to be created to " + this.networkCS + " ---");
	}
}
