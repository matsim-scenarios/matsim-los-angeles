/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package org.matsim.run;

import java.util.List;
import java.util.Random;

import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ScoringParameterSet;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.utils.misc.Time;

import com.google.inject.Inject;

import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;

/**
 * pmanser / SBB, gleich, ikaddoura
 */
public class LosAngelesRaptorIntermodalAccessEgress implements RaptorIntermodalAccessEgress {
	
	Config config;	
	
	Random random = MatsimRandom.getLocalInstance();
	
	@Inject
    LosAngelesRaptorIntermodalAccessEgress(Config config) {
		this.config = config;
	}

	@Override
    public RIntermodalAccessEgress calcIntermodalAccessEgress(final List<? extends PlanElement> legs, RaptorParameters params, Person person) {
		String subpopulationName = null;
		if (person.getAttributes() != null) {
			Object attr = person.getAttributes().getAttribute("subpopulation") ;
			subpopulationName = attr == null ? null : attr.toString();
		}
		
		ScoringParameterSet scoringParams = config.planCalcScore().getScoringParameters(subpopulationName);

		double marginalUtilityOfMoney = scoringParams.getMarginalUtilityOfMoney();	
		if (person.getAttributes() != null && person.getAttributes().getAttribute("marginalUtilityOfMoney") != null) {
			marginalUtilityOfMoney = (double) person.getAttributes().getAttribute("marginalUtilityOfMoney") ;
		}
		
        double utility = 0.0;
        double tTime = 0.0;
        for (PlanElement pe : legs) {
            if (pe instanceof Leg) {
                String mode = ((Leg) pe).getMode();
                double travelTime = ((Leg) pe).getTravelTime();
                
                // overrides individual parameters per person; use default scoring parameters
                if (Time.getUndefinedTime() != travelTime) {
                    tTime += travelTime;
                    utility += travelTime * (scoringParams.getModes().get(mode).getMarginalUtilityOfTraveling() + (-1) * scoringParams.getPerforming_utils_hr()) / 3600;
                }
                Double distance = ((Leg) pe).getRoute().getDistance();
                if (distance != null && distance != 0.) {
                	utility += distance * scoringParams.getModes().get(mode).getMarginalUtilityOfDistance();
                	utility += distance * scoringParams.getModes().get(mode).getMonetaryDistanceRate() * marginalUtilityOfMoney;
                }
                utility += scoringParams.getModes().get(mode).getConstant();
            }
        }
        return new RIntermodalAccessEgress(legs, -utility, tTime);
    }
}
