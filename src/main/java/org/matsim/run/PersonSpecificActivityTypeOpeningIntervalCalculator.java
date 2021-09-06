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

import java.util.Map;

import org.matsim.api.core.v01.population.Activity;
import org.matsim.core.scoring.functions.OpeningIntervalCalculator;
import org.matsim.core.utils.misc.OptionalTime;

/**
* @author ikaddoura
*/

public class PersonSpecificActivityTypeOpeningIntervalCalculator implements OpeningIntervalCalculator {

	private final Map<String, OptionalTime[]> baseType2openingInterval;

	public PersonSpecificActivityTypeOpeningIntervalCalculator(Map<String, OptionalTime[]> baseType2openingInterval) {
		this.baseType2openingInterval = baseType2openingInterval;
	}

	@Override
	public OptionalTime[] getOpeningInterval(final Activity act) {
		String baseActivityType = act.getType().split("_")[0];	
		if (baseActivityType.equals("home")) {
			
			// home activities should not have an opening or closing time
			return new OptionalTime[]{OptionalTime.undefined(), OptionalTime.undefined()};
		
		} else {
			
			// for all other activity (base) types use each person's initial
			// activity start and end times to approximate the opening interval.
			// In case an agent has different activities of the same type,
			// take the minimum opening time and the maximum closing time
			return baseType2openingInterval.get(baseActivityType);
		}		
	}

}

