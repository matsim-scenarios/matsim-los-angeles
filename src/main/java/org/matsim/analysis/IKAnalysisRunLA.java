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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.matsim.analysis.TripAnalysisFilter.TripConsiderType;
import org.matsim.analysis.VehicleAnalysisFilter.StringComparison;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.run.LosAngelesIntermodalPtDrtRouterAnalysisModeIdentifier;

/**
 * @author ikaddoura
 *
 */
public class IKAnalysisRunLA {
	private static final Logger log = Logger.getLogger(IKAnalysisRunLA.class);
			
	public static void main(String[] args) throws IOException {
			
		String runDirectory = null;
		String runId = null;
		String runDirectoryToCompareWith = null;
		String runIdToCompareWith = null;
		String visualizationScriptInputDirectory = null;
		String scenarioCRS = null;	
		String shapeFileZones = null;
		String crsShapFileZones = null;
		String shapeFileWSC = null;
		String zoneId = null;
		String crsShapeFileWSC = null;
		String shapeFileOutsideWSC = null;
		String crsShapeFileOutsideWSC = null;
		int scalingFactor;

		final String[] helpLegModes = {TransportMode.walk,"access_egress_pt"};
		final String homeActivityPrefix = "home";
		final String modesString = "car,pt,bike,ride,ride_taxi,drt1,drt2,walk"; // the modes we are interested in

		if (args.length > 0) {

			runDirectory = args[0];
			runId = args[1];
			
			runDirectoryToCompareWith = args[2];
			runIdToCompareWith = args[3];
						
			scenarioCRS = args[4];
			
			shapeFileZones = args[5];
			crsShapFileZones = args[6];
			zoneId = args[7];
			
			shapeFileWSC = args[8];
			crsShapeFileWSC = args[9];
			
			shapeFileOutsideWSC = args[10];
			crsShapeFileOutsideWSC = args[11];
			
			scalingFactor = Integer.valueOf(args[12]);

			if(!args[13].equals("null")) visualizationScriptInputDirectory = args[13];

									
		} else {
			
			runDirectory = "./test/output/org/matsim/run/RunLosAngelesScenarioTest/test1/";
			runId = "los-angeles-WSC-reduced-v1.1-1pct_run0";		
			
			// for scenario comparison, otherwise set to null
			runDirectoryToCompareWith = null;
			runIdToCompareWith = null;
			
			scenarioCRS = "EPSG:3310";
			
			shapeFileZones = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/original-data/shp-data/hexagon-grid-7500/hexagon-grid-7500.shp";
			crsShapFileZones = "EPSG:3310";
			zoneId = "ID";
						
			shapeFileWSC = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/original-data/shp-data/WSC_Boundary_SCAG/WSC_Boundary_SCAG.shp";			
			crsShapeFileWSC = "EPSG:3310";
			
			shapeFileOutsideWSC = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/original-data/shp-data/WSC-LA-planning-area_without-WSC/WSC-LA-planning-area_without-WSC.shp";			
			crsShapeFileOutsideWSC = "EPSG:3310";
			
			// 100 for 1% population sample; 10 for a 10% population sample, ...
			scalingFactor = 100;
			
			// for some QGIS visualizations (optional)
			visualizationScriptInputDirectory = null;

		}
		
		Scenario scenario1 = loadScenario(runDirectory, runId, scenarioCRS);
		Scenario scenario0 = loadScenario(runDirectoryToCompareWith, runIdToCompareWith, scenarioCRS);
		
		List<AgentFilter> agentFilters = new ArrayList<>();
		
		AgentAnalysisFilter filter1a = new AgentAnalysisFilter("all-persons");
		filter1a.preProcess(scenario1);
		agentFilters.add(filter1a);
		
		AgentAnalysisFilter filter1b = new AgentAnalysisFilter("residents-in-WSC");
		filter1b.setZoneFile(shapeFileWSC);
		filter1b.setRelevantActivityType(homeActivityPrefix);
		filter1b.preProcess(scenario1);
		agentFilters.add(filter1b);
		
		AgentAnalysisFilter filter1c = new AgentAnalysisFilter("residents-planning-area-outside-WSC");
		filter1c.setZoneFile(shapeFileOutsideWSC);
		filter1c.setRelevantActivityType(homeActivityPrefix);
		filter1c.preProcess(scenario1);
		agentFilters.add(filter1c);
		
		List<TripFilter> tripFilters = new ArrayList<>();
		
		TripAnalysisFilter tripFilter1a = new TripAnalysisFilter("all-trips");
		tripFilter1a.preProcess(scenario1);
		tripFilters.add(tripFilter1a);
		
		TripAnalysisFilter tripFilter1b = new TripAnalysisFilter("trips-origin-OR-end-in-WSC");
		tripFilter1b.setZoneInformation(shapeFileWSC, crsShapeFileWSC);
		tripFilter1b.preProcess(scenario1);
		tripFilter1b.setBuffer(2000.);
		tripFilter1b.setTripConsiderType(TripConsiderType.OriginOrDestination);
		tripFilters.add(tripFilter1b);
		
		TripAnalysisFilter tripFilter1d = new TripAnalysisFilter("trips-origin-AND-end-in-WSC");
		tripFilter1d.setZoneInformation(shapeFileWSC, crsShapeFileWSC);
		tripFilter1d.preProcess(scenario1);
		tripFilter1d.setBuffer(0.);
		tripFilter1d.setTripConsiderType(TripConsiderType.OriginAndDestination);
		tripFilters.add(tripFilter1d);
		
		TripAnalysisFilter tripFilter1c = new TripAnalysisFilter("trips-in-planning-area-outside-WSC");
		tripFilter1c.setZoneInformation(shapeFileOutsideWSC, crsShapeFileOutsideWSC);
		tripFilter1c.preProcess(scenario1);
		tripFilter1c.setBuffer(2000.);
		tripFilter1c.setTripConsiderType(TripConsiderType.OriginOrDestination);
		tripFilters.add(tripFilter1c);
		
		final List<VehicleFilter> vehicleFilters = new ArrayList<>();
		
		VehicleAnalysisFilter vehicleAnalysisFilter0 = null;
		vehicleFilters.add(vehicleAnalysisFilter0);
		
		VehicleAnalysisFilter vehicleAnalysisFilter1 = new VehicleAnalysisFilter("drt-vehicles", "rt", StringComparison.Contains);
		vehicleFilters.add(vehicleAnalysisFilter1);
		
		List<String> modes = new ArrayList<>();
		for (String mode : modesString.split(",")) {
			modes.add(mode);
		}

		MatsimAnalysis analysis = new MatsimAnalysis();
		analysis.setScenario1(scenario1);
		analysis.setScenario0(scenario0);
		
		analysis.setAgentFilters(agentFilters);		
		analysis.setTripFilters(tripFilters);
		analysis.setVehicleFilters(vehicleFilters);
		
		analysis.setScenarioCRS(scenarioCRS);
		analysis.setScalingFactor(scalingFactor);
		analysis.setModes(modes);
		analysis.setHelpLegModes(helpLegModes);
		analysis.setZoneInformation(shapeFileZones, crsShapFileZones, zoneId);
		analysis.setVisualizationScriptInputDirectory(visualizationScriptInputDirectory);

		analysis.setMainModeIdentifier(new LosAngelesIntermodalPtDrtRouterAnalysisModeIdentifier());
		
		analysis.run();
	}
	
	private static Scenario loadScenario(String runDirectory, String runId, String scenarioCRS) {
		log.info("Loading scenario...");
		
		if (runDirectory == null || runDirectory.equals("") || runDirectory.equals("null")) {
			return null;	
		}
		
		if (!runDirectory.endsWith("/")) runDirectory = runDirectory + "/";
		
		String networkFile = runDirectory + runId + ".output_network.xml.gz";
		String populationFile = runDirectory + runId + ".output_plans.xml.gz";

		Config config = ConfigUtils.createConfig();

		config.global().setCoordinateSystem(scenarioCRS);
		config.controler().setRunId(runId);
		config.controler().setOutputDirectory(runDirectory);
		config.plans().setInputFile(populationFile);
		config.network().setInputFile(networkFile);
		
		return ScenarioUtils.loadScenario(config);
	}

}
		

