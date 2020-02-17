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

import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
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

public class IncomeDependentPlanScoringFunctionFactory implements ScoringFunctionFactory {
	private static final Logger log = Logger.getLogger(IncomeDependentPlanScoringFunctionFactory.class );

	private final double averageAnnualIncomePerPerson = 35363.89;
	private int warnCnt = 0;
	
	private final Config config;
	private Network network;

	private final ScoringParametersForPerson params;

	public IncomeDependentPlanScoringFunctionFactory( final Scenario sc ) {
		this( sc.getConfig(), new SubpopulationScoringParameters( sc ) , sc.getNetwork() );
	}

	@Inject
	IncomeDependentPlanScoringFunctionFactory(Config config, ScoringParametersForPerson params, Network network) {
		this.config = config;
		this.params = params;
		this.network = network;
	}

	@Override
	public ScoringFunction createNewScoringFunction(Person person) {

		final ScoringParameters parameters = params.getScoringParameters( person );

		SumScoringFunction sumScoringFunction = new SumScoringFunction();
		sumScoringFunction.addScoringFunction(new CharyparNagelActivityScoring( parameters ));
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

