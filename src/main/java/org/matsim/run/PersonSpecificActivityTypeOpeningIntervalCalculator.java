
/* *********************************************************************** *
 * project: org.matsim.*
 * ActivityTypeOpeningIntervalCalculator.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2019 by the members listed in the COPYING,        *
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

import org.matsim.api.core.v01.population.Activity;
import org.matsim.core.scoring.functions.ActivityUtilityParameters;
import org.matsim.core.scoring.functions.OpeningIntervalCalculator;
import org.matsim.core.scoring.functions.ScoringParameters;

public class PersonSpecificActivityTypeOpeningIntervalCalculator implements OpeningIntervalCalculator {
	private final ScoringParameters params;
	private final double tolerance = 900.;

	public PersonSpecificActivityTypeOpeningIntervalCalculator(ScoringParameters params) {
		this.params = params;
	}

	@Override
	public double[] getOpeningInterval(final Activity act) {

		ActivityUtilityParameters actParams = this.params.utilParams.get(act.getType());
		if (actParams == null) {
			throw new IllegalArgumentException("acttype \"" + act.getType() + "\" is not known in utility parameters " +
					"(module name=\"planCalcScore\" in the config file).");
		}
		
		double openingTime = actParams.getOpeningTime();
		double closingTime = actParams.getClosingTime();
		
		if (act.getAttributes().getAttribute("initialStartTime") != null && act.getAttributes().getAttribute("initialEndTime") != null) {
			// Consider the initial start and end time given in the activity attributes as opening and closing times.
			// This will fix the agents' activity start and end times close to the initial times but still allow for some flexibility
			
			openingTime = (double) act.getAttributes().getAttribute("initialStartTime") - tolerance;
			closingTime = (double) act.getAttributes().getAttribute("initialEndTime") + tolerance;
		}

		// openInterval has two values
		// openInterval[0] will be the opening time
		// openInterval[1] will be the closing time

		return new double[]{openingTime, closingTime};
	}
}
