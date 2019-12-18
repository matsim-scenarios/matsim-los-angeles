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

/**
 * 
 */
package org.matsim.prepare;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.gtfs.RunGTFS2MATSim;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.TransitStopFacilityImpl;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.utils.CreatePseudoNetwork;
import org.matsim.vehicles.MatsimVehicleWriter;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

/**
 * @author  ikaddoura
 * 
 * This script utilizes GTFS2MATSim and creates a pseudo network and vehicles using MATSim standard API functionality.
 * 
 * The resulting network / vehicles / schedule may have wrong travel speeds which may result in delays or transit vehicles
 * being far ahead the schedule. In case, we observe something like that, we need to extend the script below and adjust
 * travel speeds for certain lines or vehicle types...
 * 
 */

public class CreateTransitScheduleAndVehiclesFromGTFS {

	
	public static void main(String[] args) {
		
		String rootDirectory = null;
		
		if (args.length == 1) {
			rootDirectory = args[0];
		} else {
			throw new RuntimeException("Please set the root directory (the directory above 'scag_model'). Aborting...");
		}
		
		if (!rootDirectory.endsWith("/")) rootDirectory = rootDirectory + "/";
		
		// input data
//		String fileName = "LA_DOT_2019-07-12";
//		String gtfsZipFile = rootDirectory + "gtfs-data/latest_available_2019-10-30/" + fileName + ".zip";
//		int vehicleCapacity = 100;
//		String dateString = "2019-07-08"; // previous monday
		
//		String fileName = "LA_GO_BUS_2019-10-02";
//		String gtfsZipFile = rootDirectory + "gtfs-data/latest_available_2019-10-30/" + fileName + ".zip";
//		int vehicleCapacity = 100;
//		String dateString = "2019-09-30"; // previous monday
		
//		String fileName = "LA_METRO_BUS_2019-10-04";
//		String gtfsZipFile = rootDirectory + "gtfs-data/latest_available_2019-10-30/" + fileName + ".zip";
//		int vehicleCapacity = 100;
//		String dateString = "2019-09-30"; // previous monday
		
//		String fileName = "LA_METRO_RAIL_2019-10-29";
//		String gtfsZipFile = rootDirectory + "gtfs-data/latest_available_2019-10-30/" + fileName + ".zip";
//		int vehicleCapacity = 500;
//		String dateString = "2019-10-28"; // previous monday
		
		String fileName = "METROLINK_2019-10-15";
		String gtfsZipFile = rootDirectory + "gtfs-data/latest_available_2019-10-30/" + fileName + ".zip";
		int vehicleCapacity = 500;
		String dateString = "2019-10-14"; // previous monday

		CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, "EPSG:3310");
		LocalDate date = LocalDate.parse(dateString);

		//output files 
		String scheduleFile = rootDirectory + "matsim-input-files/pt/" + fileName + "/scag-transitSchedule_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".xml.gz";
		String networkFile = rootDirectory + "matsim-input-files/pt/" + fileName + "/scag-network-with-pt_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".xml.gz";
		String transitVehiclesFile = rootDirectory + "matsim-input-files/pt/" + fileName + "/scag-transitVehicles_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".xml.gz";;
		
		OutputDirectoryLogging.catchLogEntries();
		try {
			OutputDirectoryLogging.initLoggingWithOutputDirectory(rootDirectory + "matsim-input-files/pt/" + fileName + "/");
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		//Convert GTFS
		RunGTFS2MATSim.convertGtfs(gtfsZipFile, scheduleFile, date, ct, false);
		
		//Parse the schedule again
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new TransitScheduleReader(scenario).readFile(scheduleFile);
		
		//if neccessary, parse in an existing network file here:
//		new MatsimNetworkReader(scenario.getNetwork()).readFile(rootDirectory + "matsim-input-files/network/scag-network_2019-10-21_network.xml.gz");
		
		//Create a network around the schedule
		new CreatePseudoNetwork(scenario.getTransitSchedule(),scenario.getNetwork(),"pt_" + fileName + "_").createNetwork();
		
		//Create simple transit vehicles
		createTransitVehiclesForSchedule(scenario.getTransitSchedule(), scenario.getTransitVehicles(), vehicleCapacity, "pt_" + fileName + "_");
		
		//Write out network, vehicles and schedule
		new NetworkWriter(scenario.getNetwork()).write(networkFile);
		new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(scheduleFile);
		new MatsimVehicleWriter(scenario.getTransitVehicles()).writeFile(transitVehiclesFile);
	}
	
	private static void createTransitVehiclesForSchedule(final TransitSchedule schedule, final Vehicles vehicles, final int seats, final String idPrefix) {
		VehiclesFactory vehFactotry = vehicles.getFactory();
		VehicleType vehicleType = vehFactotry.createVehicleType(Id.create(idPrefix + "defaultTransitVehicleType", VehicleType.class));
		vehicleType.getCapacity().setSeats( seats );
		vehicleType.getCapacity().setStandingRoom( 0 );
		vehicleType.setPcuEquivalents(0.);
		vehicles.addVehicleType(vehicleType);

		long vehId = 0;
		for (TransitLine line : schedule.getTransitLines().values()) {
			for (TransitRoute route : line.getRoutes().values()) {
				for (Departure departure : route.getDepartures().values()) {
					Vehicle veh = vehFactotry.createVehicle(Id.create(idPrefix + Long.toString(vehId++), Vehicle.class), vehicleType);
					vehicles.addVehicle(veh);
					departure.setVehicleId(veh.getId());
				}
				
				for (TransitRouteStop stop : route.getStops()) {
					// make sure transit vehicles follow the schedule!
					stop.setAwaitDepartureTime(true);
				}
			}
		}
	}
}
