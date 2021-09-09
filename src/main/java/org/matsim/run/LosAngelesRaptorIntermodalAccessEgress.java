/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package org.matsim.run;

import java.util.List;
import java.util.Random;

import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.drt.fare.DrtFareParams;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ScoringParameterSet;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.utils.misc.OptionalTime;

import com.google.inject.Inject;

import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import ch.sbb.matsim.routing.pt.raptor.RaptorStopFinder;

/**
 * pmanser / SBB, gleich, ikaddoura
 */
public class LosAngelesRaptorIntermodalAccessEgress implements RaptorIntermodalAccessEgress {
	
	Config config;	
	DrtConfigGroup drtConfigGroup;
	
	Random random = MatsimRandom.getLocalInstance();
	
	@Inject
    LosAngelesRaptorIntermodalAccessEgress(Config config) {
		this.config = config;
		drtConfigGroup = ConfigUtils.addOrGetModule(config, DrtConfigGroup.class);
	}

	@Override
    public RIntermodalAccessEgress calcIntermodalAccessEgress(final List<? extends PlanElement> legs, RaptorParameters params, Person person, RaptorStopFinder.Direction direction) {
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
				OptionalTime travelTime = ((Leg) pe).getTravelTime();
                
                // overrides individual parameters per person; use default scoring parameters
                if (travelTime.isDefined()) {
                    tTime += travelTime.seconds();
                    utility += travelTime.seconds() * (scoringParams.getModes().get(mode).getMarginalUtilityOfTraveling() + (-1) * scoringParams.getPerforming_utils_hr()) / 3600;
                }
                Double distance = ((Leg) pe).getRoute().getDistance();
                if (distance != null && distance != 0.) {
                	utility += distance * scoringParams.getModes().get(mode).getMarginalUtilityOfDistance();
                	utility += distance * scoringParams.getModes().get(mode).getMonetaryDistanceRate() * marginalUtilityOfMoney;
                }
                utility += scoringParams.getModes().get(mode).getConstant();
                
             // account for drt fares

				if(drtConfigGroup != null && drtConfigGroup.getDrtFareParams().isPresent()) {
					DrtFareParams fares = drtConfigGroup.getDrtFareParams().get();
                        double fare = 0.;
                		if (distance != null && distance != 0.) {
                        	fare += fares.getDistanceFare_m() * distance;
                        }
                                                
                        if (travelTime.isDefined()) {
                            fare += fares.getTimeFare_h() * travelTime.seconds() / 3600.;
                        }
                        
                        fare += fares.getBasefare();
                        fare = Math.max(fare, fares.getMinFarePerTrip());
                        utility += -1. * fare * scoringParams.getMarginalUtilityOfMoney();
                	}
            }
        }
        return new RIntermodalAccessEgress(legs, -utility, tTime, direction);
    }
}
