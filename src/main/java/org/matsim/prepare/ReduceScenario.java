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

import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;
import org.matsim.core.network.algorithms.MultimodalNetworkCleaner;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.prepare.drt.ShapeFileUtils;
import org.matsim.run.RunLosAngelesScenario;

/**
* @author ikaddoura
*/

public class ReduceScenario {
	private static final Logger log = Logger.getLogger(ReduceScenario.class );
	
	public static void main(String[] args) {
		
		String planningAreaShpFile;
		String[] argsWithoutCustomAttributes;

		final double linkBuffer = 1000.;
		final double personBuffer = 1000.;
		
		for (String arg : args) {
			log.info( arg );
		}
		
		if ( args.length==0 ) {
			argsWithoutCustomAttributes = new String[] {"./scenarios/los-angeles-v1.1/input/los-angeles-v1.1-10pct.config.xml"}  ;
			planningAreaShpFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/original-data/shp-data/WSC-LA-planning-area/WSC-LA-planning-area.shp";
			
		} else {
			planningAreaShpFile = args[1];
			log.info("planningAreaShpFile: " + planningAreaShpFile);
			
			argsWithoutCustomAttributes = new String[args.length - 1];
			argsWithoutCustomAttributes[0] = args[0];
			log.info("config file: " + args[0]);
			log.info("other arguments: ");
			for (int n = 2; n < args.length ; n++) {
				argsWithoutCustomAttributes[n-1] = args[n];
				log.info(args[n]);
			}
			log.info("---------------");
		}
				
		Config config = RunLosAngelesScenario.prepareConfig( args ) ;
		config.controler().setLastIteration(0);
		Scenario scenario = RunLosAngelesScenario.prepareScenario( config ) ;
		
		ShapeFileUtils shpUtils = new ShapeFileUtils(planningAreaShpFile);
		
		Set<Id<Link>> linksToDelete = new HashSet<>();
		for (Link link : scenario.getNetwork().getLinks().values()) {
			if (shpUtils.isCoordInArea(link.getCoord(), linkBuffer)
					|| shpUtils.isCoordInArea(link.getFromNode().getCoord(), linkBuffer) 
					|| shpUtils.isCoordInArea(link.getToNode().getCoord(), linkBuffer)
					|| link.getAllowedModes().contains(TransportMode.pt)
					|| link.getFreespeed() >= 5. ) {
				// keep the link
			} else {
				linksToDelete.add(link.getId());
			}
		}
		
		log.info("Links to delete: " + linksToDelete.size());
		for (Id<Link> linkId : linksToDelete) {
			scenario.getNetwork().removeLink(linkId);
		}
		
		// clean the network
		log.info("number of nodes before cleaning:" + scenario.getNetwork().getNodes().size());
		log.info("number of links before cleaning:" + scenario.getNetwork().getLinks().size());
		log.info("attempt to clean the network");
		new MultimodalNetworkCleaner(scenario.getNetwork()).removeNodesWithoutLinks();
		Set<String> modes = new HashSet<>();
		modes.add(TransportMode.car);
		new MultimodalNetworkCleaner(scenario.getNetwork()).run(modes);
		log.info("number of nodes after cleaning:" + scenario.getNetwork().getNodes().size());
		log.info("number of links after cleaning:" + scenario.getNetwork().getLinks().size());
		
		Controler controler = RunLosAngelesScenario.prepareControler(scenario);
		
		// now delete irrelevant persons
		Set<Id<Person>> personsToDelete = new HashSet<>();
		
		for (Person person : scenario.getPopulation().getPersons().values()) {
			boolean keepPerson = false;
			for (Trip trip : TripStructureUtils.getTrips(person.getSelectedPlan())) {
				
				// keep all agents starting or ending in area
				if (shpUtils.isCoordInArea(trip.getOriginActivity().getCoord(), personBuffer)
						|| shpUtils.isCoordInArea(trip.getDestinationActivity().getCoord(), personBuffer)) {
					keepPerson = true;
					break;
				}
				
				// also keep persons traveling through or close to area (beeline)
				if (shpUtils.isLineInArea(trip.getOriginActivity().getCoord(), trip.getDestinationActivity().getCoord(), personBuffer)) {
					keepPerson = true;
					break;
				}
				
			}
			if (!keepPerson) {
				personsToDelete.add(person.getId());
			}
		}
		log.info("Persons to delete: " + personsToDelete.size());
		for (Id<Person> personId : personsToDelete) {
			scenario.getPopulation().removePerson(personId);
		}
		controler.run();
		
		log.info("deleted persons: " + personsToDelete.size());
		log.info("remaining persons: " + scenario.getPopulation().getPersons().size());

		log.info("deleted links: " + linksToDelete.size());
		log.info("remaining links: " + scenario.getNetwork().getLinks().size());
	}

}

