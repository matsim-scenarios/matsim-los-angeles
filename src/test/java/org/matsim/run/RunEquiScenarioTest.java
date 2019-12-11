/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
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

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.VspExperimentalConfigGroup.VspDefaultsCheckingLevel;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.testcases.MatsimTestUtils;

/**
 * @author ikaddoura
 *
 */
public class RunEquiScenarioTest {
	
	@Rule public MatsimTestUtils utils = new MatsimTestUtils() ;

	@Test
	public final void test() {
		try {			
			String[] args = new String[] { "scenarios/equil/config.xml" };
			Config config = RunLosAngelesScenario.prepareConfig(args);
			config.controler().setWriteEventsInterval(1);
			config.controler().setLastIteration(1);
			config.controler().setOutputDirectory( utils.getOutputDirectory() );
			config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);	
			config.vspExperimental().setVspDefaultsCheckingLevel(VspDefaultsCheckingLevel.warn);
			
			Scenario scenario = RunLosAngelesScenario.prepareScenario(config);
			
			for (Link link : scenario.getNetwork().getLinks().values()) {
				link.getAttributes().putAttribute("dailyPCost", 8.5);
				link.getAttributes().putAttribute("oneHourPCost", 1.37);
				link.getAttributes().putAttribute("extraHourPCost", 2.55);
				link.getAttributes().putAttribute("maxDailyPCost", 4.888);
			}
			
			Controler controler = RunLosAngelesScenario.prepareControler(scenario);
			
			controler.run();
		
		} catch ( Exception ee ) {
			ee.printStackTrace();
			Assert.fail();
		}


	}

}
