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

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.scenario.ScenarioUtils;

/**
* @author ikaddoura
*/

public class ModifyPopulationForEpisim {

	private static final Logger log = Logger.getLogger(ModifyPopulationForEpisim.class );
	
	private static String inputPlans = "/Users/ihab/Documents/workspace/public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/input/los-angeles-v1.0-population-10pct_2020-03-07.xml.gz";
	private static String outputPlans = "/Users/ihab/Documents/workspace/public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/input/los-angeles-v1.0-population-10pct_2020-03-07_teleported.xml.gz";
	
	public static void main(String[] args) {
				
		for (String arg : args) {
			log.info( arg );
		}
		
		if ( args.length > 0 ) {
			inputPlans = args[0];
			outputPlans = args[1];
		}
				
		Config config = ConfigUtils.createConfig();
		config.global().setCoordinateSystem("EPSG:3310");
		config.plans().setInputFile(inputPlans);
		Scenario scInput = ScenarioUtils.loadScenario(config );
		
		Scenario scOutput = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		Population popOutput = scOutput.getPopulation();
		popOutput.getAttributes().putAttribute("coordinateReferenceSystem", "EPSG:3310");
		
		for (Person p : scInput.getPopulation().getPersons().values()){
			Person personNew = popOutput.getFactory().createPerson(p.getId());
			
			for (String attribute : p.getAttributes().getAsMap().keySet()) {
				personNew.getAttributes().putAttribute(attribute, p.getAttributes().getAttribute(attribute));
			}
			personNew.addPlan(p.getSelectedPlan());
												
			popOutput.addPerson(personNew);
		}
		
		for (Person person : popOutput.getPersons().values()) {
			for (Plan plan : person.getPlans()) {		
				for (Trip trip : TripStructureUtils.getTrips(plan.getPlanElements())) {		
					Leg drtLeg = trip.getLegsOnly().get(0);
					if (drtLeg.getMode().equals("car")) {
						if (trip.getLegsOnly().size() > 1) {
							throw new RuntimeException("There are more than one leg within a drt trip. Needs to be revised. Aborting...");
						}
						drtLeg.setMode("car_teleported");
						drtLeg.setRoute(null);
					}
					if (drtLeg.getMode().equals("freight")) {
						if (trip.getLegsOnly().size() > 1) {
							throw new RuntimeException("There are more than one leg within a drt trip. Needs to be revised. Aborting...");
						}
						drtLeg.setMode("freight_teleported");
						drtLeg.setRoute(null);
					}
							
				}
			}
		}		
		log.info("Writing population...");
		new PopulationWriter(scOutput.getPopulation()).write(outputPlans);
		log.info("Writing population... Done.");
	}
}

