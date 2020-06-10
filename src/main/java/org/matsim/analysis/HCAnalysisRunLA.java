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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	private final static double dailyPTFare = 2.0;
	
	private static Map<String, Double> scores = new HashMap<>();
	private static Map<String, List<String>> outputPersons = new HashMap<>();
	private static Map<String, Integer> households_size = new HashMap<>();
	private static Map<String, Double> households_inc = new HashMap<>();
	private static Set<String> personsByPT = new HashSet<>();
	
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
		final String outputExperiencedPlanScoreFile = runDirectory + runId + ".300.experienced_plans_scores.txt";
		final String outputLegsFile = runDirectory + runId + ".output_legs.csv";
		
		log.info("RunID: " + runId + "; Area: " + shapeFileWSC);
		
		getNetBenefit(outputPersonsFile, outputExperiencedPlanScoreFile, shapeFileWSC);
		
		getPTRevenue(outputLegsFile);
	}
	
	private static void getPTRevenue(String outputLegsFile) throws IOException {
		for (CSVRecord csvRecord : new CSVParser(Files.newBufferedReader(Paths.get(outputLegsFile)), csvFormat)) {
			String person = csvRecord.get(0);
			
			String mode = csvRecord.get(6);
			if (mode.equals("pt") && !personsByPT.contains(person))
				personsByPT.add(person);
		}
		
		log.info("Total PT Revenue: $" + dailyPTFare * personsByPT.size());	
	}
	
	// get the value of percentile
	public static Double getPercentile(List<Double> incomes_sorted, double percentile)
    {
		int index = (int) Math.ceil((percentile / 100) * incomes_sorted.size());     
		return incomes_sorted.get(index - 1);
    }
	
	private static void getNetBenefit(String outputPersonsFile, String outputExperiencedPlanScoreFile, String shapeFile) throws NumberFormatException, IOException {
		ShapeFileUtils shpUtils = new ShapeFileUtils(shapeFile);
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
			String hh_inc = csvRecord.get(10);
			List<String> attribs= new ArrayList<String>();
			attribs.add(first_act_x);
			attribs.add(first_act_y);
			attribs.add(hh_id);
			attribs.add(hh_inc);
			
			// there are persons (in gq) and freight trips without hh info, put
			if (hh_size.equals("") || hh_inc.equals("")) {
				continue;
			}
			outputPersons.put(person, attribs);
			households_size.put(hh_id, Integer.valueOf(hh_size));
			households_inc.put(hh_id, Double.valueOf(hh_inc));
		}
		
		List<Double> incomes = new ArrayList<Double>(households_inc.values());
		Collections.sort(incomes);
		// get percentile values
		double quintile_1 = getPercentile(incomes, 20);
		double quintile_2 = getPercentile(incomes, 40);
		double quintile_3 = getPercentile(incomes, 60);
		double quintile_4 = getPercentile(incomes, 80);
		double quintile_5 = getPercentile(incomes, 100);
		
		log.info("1st quintile: " + quintile_1);
		log.info("2nd quintile: " + quintile_2);
		log.info("3rd quintile: " + quintile_3);
		log.info("4th quintile: " + quintile_4);
		log.info("5th quintile: " + quintile_5);
		
		List<Double> scores_P_1 = new ArrayList<Double>(); 
		List<Double> scores_P_2 = new ArrayList<Double>(); 
		List<Double> scores_P_3 = new ArrayList<Double>(); 
		List<Double> scores_P_4 = new ArrayList<Double>(); 
		List<Double> scores_P_5 = new ArrayList<Double>();
		
		Set<String> hh_P_1 = new HashSet<String>();
		Set<String> hh_P_2 = new HashSet<String>();
		Set<String> hh_P_3 = new HashSet<String>();
		Set<String> hh_P_4 = new HashSet<String>();
		Set<String> hh_P_5 = new HashSet<String>();
		
		// Start parsing scores 
		for (CSVRecord csvRecord : new CSVParser(Files.newBufferedReader(Paths.get(outputExperiencedPlanScoreFile)), txtFormat)) {	
			String person = csvRecord.get(0);
			if (!outputPersons.containsKey(person))
				continue;
			double score = Double.valueOf(csvRecord.get(csvRecord.size()-1));
			double income = Double.valueOf(outputPersons.get(person).get(3));
			
			if (income < quintile_1) {
				scores_P_1.add(score);
				hh_P_1.add(outputPersons.get(person).get(2));
			}
			else if (quintile_1 <= income && income < quintile_2) {
				scores_P_2.add(score);
				hh_P_2.add(outputPersons.get(person).get(2));
			}
			else if (quintile_2 <= income && income < quintile_3) {
				scores_P_3.add(score);
				hh_P_3.add(outputPersons.get(person).get(2));
			}
			else if (quintile_3 <= income && income < quintile_4) {
				scores_P_4.add(score);
				hh_P_4.add(outputPersons.get(person).get(2));
			}
			else if (quintile_4 <= income && income <= quintile_5) {
				scores_P_5.add(score);	
				hh_P_5.add(outputPersons.get(person).get(2));
			}
			
			scores.put(person, score);
			total_scores += score;
		}
		
		if (households_inc.size() != hh_P_1.size() + hh_P_2.size() + hh_P_3.size() + hh_P_4.size() + hh_P_5.size()) {
			log.warn("The size of households_inc does not equal to the sum of households among all quintiles!!!");
		}
		
		log.info("Total net benefit (1st quintile): " + scores_P_1.parallelStream().mapToDouble(Double::doubleValue).sum());
		log.info("Total net benefit (2nd quintile): " + scores_P_2.parallelStream().mapToDouble(Double::doubleValue).sum());
		log.info("Total net benefit (3rd quintile): " + scores_P_3.parallelStream().mapToDouble(Double::doubleValue).sum());
		log.info("Total net benefit (4th quintile): " + scores_P_4.parallelStream().mapToDouble(Double::doubleValue).sum());
		log.info("Total net benefit (5th quintile): " + scores_P_5.parallelStream().mapToDouble(Double::doubleValue).sum());
		log.info("Net benefit (1st quintile) per HH: " + scores_P_1.parallelStream().mapToDouble(Double::doubleValue).sum() / hh_P_1.size());
		log.info("Net benefit (2nd quintile) per HH: " + scores_P_2.parallelStream().mapToDouble(Double::doubleValue).sum() / hh_P_2.size());
		log.info("Net benefit (3rd quintile) per HH: " + scores_P_3.parallelStream().mapToDouble(Double::doubleValue).sum() / hh_P_3.size());
		log.info("Net benefit (4th quintile) per HH: " + scores_P_4.parallelStream().mapToDouble(Double::doubleValue).sum() / hh_P_4.size());
		log.info("Net benefit (5th quintile) per HH: " + scores_P_5.parallelStream().mapToDouble(Double::doubleValue).sum() / hh_P_5.size());	

		log.info("Total score: " + total_scores);
		log.info("Ave score per hh: " + total_scores / households_size.size());
		log.info("Done!");
	}
}
		

