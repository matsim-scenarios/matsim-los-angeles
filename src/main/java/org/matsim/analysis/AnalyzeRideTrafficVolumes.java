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
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;

/**
* @author ikaddoura
*/

public class AnalyzeRideTrafficVolumes {
	private static final Logger log = Logger.getLogger(AnalyzeRideTrafficVolumes.class);

	public static void main(String[] args) {
		
		String plansFile;
		String analysisOutputFile;
		String crs;
		
		if (args.length == 0) {
			plansFile = "/Users/ihab/Documents/workspace/public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/output/los-angeles-v1.1-1pct/los-angeles-v1.1-1pct.output_plans.xml.gz";
			analysisOutputFile = "/Users/ihab/Documents/workspace/public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/output/los-angeles-v1.1-1pct/ride-traffic-volume.csv";
			crs = "EPSG:3310";
		} else {
			plansFile = args[0];
			analysisOutputFile = args[1];
			crs = args[2];
		}
		
		// ########################################
		
		Config config = ConfigUtils.createConfig();
		config.plans().setInputFile(plansFile);
		config.global().setCoordinateSystem(crs);		
		
		Scenario scenaro = ScenarioUtils.loadScenario(config);
				
		Map<Id<Link>, Integer> linkId2rideAgents = new HashMap<>();
		
		for (Person person : scenaro.getPopulation().getPersons().values()) {
			for (Leg leg : TripStructureUtils.getLegs(person.getSelectedPlan())) {
				if (leg.getMode().equals("ride")) {
					if (leg.getRoute() instanceof NetworkRoute) {
						NetworkRoute route = (NetworkRoute) leg.getRoute();
						for (Id<Link> linkId : route.getLinkIds()) {
							linkId2rideAgents.merge(linkId, 1, Integer::sum);
						}
					} else {
						throw new RuntimeException("Ride route is not a network route. Aborting...");
					}
				}
			}
		}
		
		File file = new File(analysisOutputFile);
		
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(file));
			bw.write("link;rideAgents");
			bw.newLine();
			
			for (Id<Link> linkId : linkId2rideAgents.keySet()){
				double volume = linkId2rideAgents.get(linkId);
				bw.write(linkId + ";" + volume);
				bw.newLine();
			}
			
			bw.close();
			log.info("Output written to " + analysisOutputFile);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}

