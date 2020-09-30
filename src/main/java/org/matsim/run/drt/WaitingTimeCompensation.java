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

package org.matsim.run.drt;

import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonMoneyEvent;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ScoringParameterSet;

import com.google.inject.Inject;

/**
* @author ikaddoura
*/

public class WaitingTimeCompensation implements PersonDepartureEventHandler, PersonArrivalEventHandler, PersonEntersVehicleEventHandler {

	@Inject Scenario scenario;
	@Inject EventsManager events;
	
	private final String drtModePrefix = "drt";
	private final double betaPerformingCompensationFactor = 1.0;
	
	Map<Id<Person>, Double> personId2drtDepartureTime = new HashMap<>();
	Map<Id<Person>, String> personId2drtMode = new HashMap<>();
	
	@Override
	public void reset(int iteration) {
		this.personId2drtDepartureTime.clear();
		this.personId2drtMode.clear();
	}

	@Override
	public void handleEvent(PersonDepartureEvent event) {
		if (event.getLegMode().startsWith(drtModePrefix)) {
			personId2drtDepartureTime.put(event.getPersonId(), event.getTime());
			personId2drtMode.put(event.getPersonId(), event.getLegMode());
		}
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if (personId2drtDepartureTime.get(event.getPersonId()) != null) {
			double waitingTime = event.getTime() - personId2drtDepartureTime.get(event.getPersonId());

			Person person = scenario.getPopulation().getPersons().get(event.getPersonId());
			String subpopulation = (String) person.getAttributes().getAttribute("subpopulation");

			ScoringParameterSet scoringParams = scenario.getConfig().planCalcScore().getScoringParameters(subpopulation);
			
			String drtMode = personId2drtMode.get(event.getPersonId());
			double waitingTimeDisutility = (waitingTime / 3600.) * (betaPerformingCompensationFactor * scoringParams.getPerforming_utils_hr() + (-1. * scoringParams.getModes().get(drtMode).getMarginalUtilityOfTraveling()));			
			
			double marginalUtilityOfMoney;
			if (person.getAttributes().getAttribute("marginalUtilityOfMoney") == null) {
				throw new RuntimeException("Person does not have a marginal utility of money. Aborting...");
//				marginalUtilityOfMoney = scoringParams.getMarginalUtilityOfMoney();
			} else {
				marginalUtilityOfMoney = (double) person.getAttributes().getAttribute("marginalUtilityOfMoney");
			}
			
			double compensationAmount = waitingTimeDisutility / marginalUtilityOfMoney;
			
			events.processEvent(new PersonMoneyEvent(event.getTime(), event.getPersonId(), compensationAmount, "drtWaitingTimeCompensation", "fictiveTransactionPartner"));
		}
	}

	@Override
	public void handleEvent(PersonArrivalEvent event) {
		personId2drtDepartureTime.remove(event.getPersonId());
		personId2drtMode.remove(event.getPersonId());
	}

}

