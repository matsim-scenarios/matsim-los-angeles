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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.log4j.Logger;
import org.matsim.analysis.TripAnalysisFilter.TripConsiderType;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.prepare.drt.ShapeFileUtils;
import org.matsim.run.LosAngelesIntermodalPtDrtRouterAnalysisModeIdentifier;

/**
 * @author Huajun Chai
 *
 */
public class HCAnalysisRunLA {
	private static final Logger log = Logger.getLogger(HCAnalysisRunLA.class);
	private final static CSVFormat csvFormat = CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(';');
	private final static CSVFormat txtFormat = CSVFormat.DEFAULT.withDelimiter('\t');
			
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
		int scalingFactor;

		final String[] helpLegModes = {TransportMode.walk,"access_egress_pt"};
		final String homeActivityPrefix = "home";
		final String modesString = "car,pt,bike,ride,ride_taxi,drt1,drt2"; // the modes we are interested in

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
			
			scalingFactor = Integer.valueOf(args[10]);

			if(!args[11].equals("null")) visualizationScriptInputDirectory = args[11];

									
		} else {
			
			runDirectory = "output/wsc10-10/";//"./test/output/org/matsim/run/RunLosAngelesScenarioTest/test1/";
			runId = "wsc10-10";//"los-angeles-WSC-reduced-v1.1-1pct_run0";		
			
			// for scenario comparison, otherwise set to null
			runDirectoryToCompareWith = null;
			runIdToCompareWith = null;
			
			scenarioCRS = "EPSG:3310";
			
			shapeFileZones = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/original-data/shp-data/hexagon-grid-7500/hexagon-grid-7500.shp";
			crsShapFileZones = "EPSG:3310";
			zoneId = "ID";
						
			shapeFileWSC = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/original-data/shp-data/WSC_Boundary_SCAG/WSC_Boundary_SCAG.shp";			
			crsShapeFileWSC = "EPSG:3310";
			
			// 100 for 1% population sample; 10 for a 10% population sample, ...
			scalingFactor = 100;
			
			// for some QGIS visualizations (optional)
			visualizationScriptInputDirectory = null;

		}
		
		final String outputPersonsFile = runDirectory + runId + ".output_persons.csv";
		final String outputExperiencedPlanScore = runDirectory + runId + ".300.experienced_plans_scores.txt";
		
		final Map<String, Double> scores = new HashMap<>();
		final Map<String, List<String>> outputPersons = new HashMap<>();
		final Map<String, Integer> households = new HashMap<>();
		
		ShapeFileUtils shpUtils = new ShapeFileUtils(shapeFileWSC);
		
		double total_scores = 0;
		
		for (CSVRecord csvRecord : new CSVParser(Files.newBufferedReader(Paths.get(outputPersonsFile)), csvFormat)) {	
			String person = csvRecord.get(0);
			// we do not process freight trips
			if (person.startsWith("freight"))
				break;
			String first_act_x = csvRecord.get(1);
			String first_act_y = csvRecord.get(2);
			
			// skip for persons not in the analysis zone
			if (!shpUtils.isCoordInArea(new Coord(Double.valueOf(first_act_x), Double.valueOf(first_act_y))))
				continue;
			
			String hh_id = csvRecord.get(7);
			String hh_size = csvRecord.get(6);
			List<String> attribs= new ArrayList<String>();
			attribs.add(first_act_x);
			attribs.add(first_act_y);
			attribs.add(hh_id);
			
			outputPersons.put(person, attribs);
			// there are persons (in gq) and freight trips without hh info, put
			if (hh_size.equals("")) {
				hh_size = "-1";
			}
			households.put(hh_id, Integer.valueOf(hh_size));
		}
		
		for (CSVRecord csvRecord : new CSVParser(Files.newBufferedReader(Paths.get(outputExperiencedPlanScore)), txtFormat)) {	
			String person = csvRecord.get(0);
			if (!outputPersons.containsKey(person))
				continue;
			String score = csvRecord.get(csvRecord.size()-1);
			
			scores.put(person, Double.valueOf(score));
			total_scores += Double.valueOf(score);
		}

		log.info("Total score: " + total_scores);
		log.info("Ave score per hh: " + total_scores / households.size());
		log.info("Done!");
	}
	
	private static void getNetBenefit() {
		
	}
}
		

