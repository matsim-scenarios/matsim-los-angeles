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

package org.matsim.run;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.Config;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.CharyparNagelActivityScoring;
import org.matsim.core.scoring.functions.CharyparNagelAgentStuckScoring;
import org.matsim.core.scoring.functions.CharyparNagelMoneyScoring;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.core.scoring.functions.SubpopulationScoringParameters;

/**
* @author ikaddoura
*/

public class LosAngelesPlanScoringFunctionFactory implements ScoringFunctionFactory {
	private static final Logger log = Logger.getLogger(LosAngelesPlanScoringFunctionFactory.class );

	private final double averageAnnualIncomePerPerson = 35363.89;
	private int warnCnt = 0;
	
	private final Config config;
	private Network network;

	private final ScoringParametersForPerson params;

	public LosAngelesPlanScoringFunctionFactory( final Scenario sc ) {
		this( sc.getConfig(), new SubpopulationScoringParameters( sc ) , sc.getNetwork() );
	}

	@Inject
	LosAngelesPlanScoringFunctionFactory(Config config, ScoringParametersForPerson params, Network network) {
		this.config = config;
		this.params = params;
		this.network = network;
	}

	@Override
	public ScoringFunction createNewScoringFunction(Person person) {

		final ScoringParameters parameters = params.getScoringParameters( person );

		SumScoringFunction sumScoringFunction = new SumScoringFunction();
				
		Map<String, Double> type2minOpenTime = new HashMap<>();
		Map<String, Double> type2maxClosingTime = new HashMap<>();
		Set<String> baseTypes = new HashSet<>();
		
		// get the earliest opening time and latest closing time for each person and each (base) activity type
		for (PlanElement pE : person.getSelectedPlan().getPlanElements()) {
			if (pE instanceof Activity) {
				Activity act = (Activity) pE;
				String baseType = act.getType().split("_")[0];
				baseTypes.add(baseType);
				
				if (act.getAttributes().getAttribute("initialStartTime") != null) {
					double startTime = (double) act.getAttributes().getAttribute("initialStartTime");
					if (type2minOpenTime.get(baseType) == null) {
						type2minOpenTime.put(baseType, startTime);
					} else {
						if (type2minOpenTime.get(baseType) > startTime) {
							type2minOpenTime.put(baseType, startTime);
						}
					}
				}
				if (act.getAttributes().getAttribute("initialEndTime") != null) {
					double endTime = (double) act.getAttributes().getAttribute("initialEndTime");
					if (type2maxClosingTime.get(baseType) == null) {
						type2maxClosingTime.put(baseType, endTime);
					} else {
						if (type2maxClosingTime.get(baseType) < endTime) {
							type2maxClosingTime.put(baseType, endTime);
						}
					}
				}
			}
		}
		
		Map<String, double[]> baseType2openingInterval = new HashMap<>();
		for (String actType : baseTypes ) {
			baseType2openingInterval.put(actType, new double[]{type2minOpenTime.getOrDefault(actType, -1.), type2maxClosingTime.getOrDefault(actType, -1.)});
		}
		sumScoringFunction.addScoringFunction(new CharyparNagelActivityScoring( parameters, new PersonSpecificActivityTypeOpeningIntervalCalculator(baseType2openingInterval)));
		
		sumScoringFunction.addScoringFunction(new CharyparNagelAgentStuckScoring( parameters ));

		double personSpecificAnnualIncome = averageAnnualIncomePerPerson;
		if (person.getAttributes().getAttribute("hhinc") != null && person.getAttributes().getAttribute("hhsize") != null) {
			// account for an income-dependent marginal utility of money
			double hhSize = Double.valueOf((String) person.getAttributes().getAttribute("hhsize"));
			double hhAnnualIncome = Double.valueOf((String) person.getAttributes().getAttribute("hhinc"));
			if (hhSize < 1.0) {
				log.warn("hhsize of person " + person.getId().toString() + " is: " + hhSize + ". Using the average annual income.");
			} else {
				personSpecificAnnualIncome = hhAnnualIncome / hhSize;
			}
		} else {
			if (warnCnt <= 5) log.warn(person.getId().toString() + " does not have an income or hhSize attribute (probably a freight agent). "
					+ "Will use the average annual income, thus the marginal utility of money will be 1.0");
			if (warnCnt == 5) log.warn("Further warnings will not be printed.");
			warnCnt++;
		}
		double personSpecificMarginalUtilityOfMoney = averageAnnualIncomePerPerson  / personSpecificAnnualIncome ;
		person.getAttributes().putAttribute("marginalUtilityOfMoney", personSpecificMarginalUtilityOfMoney);

		sumScoringFunction.addScoringFunction(new CharyparNagelMoneyScoring(personSpecificMarginalUtilityOfMoney));		
		sumScoringFunction.addScoringFunction(new CharyparNagelLegScoringWithPersonSpecificMarginalUtilityOfMoney( personSpecificMarginalUtilityOfMoney, parameters , this.network, config.transit().getTransitModes() ));

		return sumScoringFunction;
	}

}

