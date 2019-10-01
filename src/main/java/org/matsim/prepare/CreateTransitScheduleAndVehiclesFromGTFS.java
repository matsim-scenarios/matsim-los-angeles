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

import java.time.LocalDate;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.gtfs.RunGTFS2MATSim;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.utils.CreatePseudoNetwork;
import org.matsim.pt.utils.CreateVehiclesForSchedule;
import org.matsim.vehicles.VehicleWriterV1;

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
		
		//input data
		String gtfsZipFile = "/Users/ihab/Documents/vsp/Projects/MATSim_LosAngeles/gtfs-data/la-metro_20101211_0848.zip"; 
		CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, "EPSG:3310");
		LocalDate date = LocalDate.parse("2010-12-01");

		//output files 
		String scheduleFile = "/Users/ihab/Documents/vsp/Projects/MATSim_LosAngeles/matsim-input-files/pt/LA_transitSchedule_GTFS_2019-10-01.xml.gz";
		String networkFile = "/Users/ihab/Documents/vsp/Projects/MATSim_LosAngeles/matsim-input-files/pt/LA_network_with-pt_2019-10-01.xml.gz";
		String transitVehiclesFile ="/Users/ihab/Documents/vsp/Projects/MATSim_LosAngeles/matsim-input-files/pt/LA_transitVehicles_GTFS_2019-10-01.xml.gz";
		
		//Convert GTFS
		RunGTFS2MATSim.convertGtfs(gtfsZipFile, scheduleFile, date, ct, false);
		
		//Parse the schedule again
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new TransitScheduleReader(scenario).readFile(scheduleFile);
		
		//if neccessary, parse in an existing network file here:
		new MatsimNetworkReader(scenario.getNetwork()).readFile("/Users/ihab/Documents/vsp/Projects/MATSim_LosAngeles/matsim-input-files/socal-LA-network_2019-09-22_network.xml.gz");
		
		//Create a network around the schedule
		new CreatePseudoNetwork(scenario.getTransitSchedule(),scenario.getNetwork(),"pt_").createNetwork();
		
		//Create simple transit vehicles
		new CreateVehiclesForSchedule(scenario.getTransitSchedule(), scenario.getTransitVehicles()).run();
		
		//Write out network, vehicles and schedule
		new NetworkWriter(scenario.getNetwork()).write(networkFile);
		new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(scheduleFile);
		new VehicleWriterV1(scenario.getTransitVehicles()).writeFile(transitVehiclesFile);
	}
}
