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
package org.matsim.prepare;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.prepare.drt.ShapeFileUtils;
import org.matsim.run.RunLosAngelesScenario;
import org.matsim.testcases.MatsimTestUtils;

/**
 * @author huajun
 *
 */
public class ReduceScenarioTest {
	
	@Rule public MatsimTestUtils utils = new MatsimTestUtils() ;

	// tests if the right agents are kept in the reduced scenario.
	// single iteration
	// simulated pt disabled
	@Test
	public final void test1() {
		try {
			String[] args = new String[] {"./scenarios/los-angeles-v1.1/input/los-angeles-WSC-reduced-v1.1-1pct.config.xml"};
			
			Config config = RunLosAngelesScenario.prepareConfig(args);
			config.controler().setLastIteration(0);
			config.global().setNumberOfThreads(1);
			config.controler().setOutputDirectory( utils.getOutputDirectory() );
			config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
			config.plans().setInputFile("../../../test/input/test-agents-for-reduced-scenario.xml");
			config.transit().setUseTransit(false);
			
			ShapeFileUtils shpUtils = new ShapeFileUtils("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/original-data/shp-data/WSC_Boundary_SCAG/WSC_Boundary_SCAG.shp");
			
			Scenario scenario = ReduceScenario.prepareScenario(config, shpUtils);
			
			// check if the total number of agents left is correct (# = 4)
			Assert.assertEquals("Wrong # of agents left in the reduced scenario.", 4, scenario.getPopulation().getPersons().size());
			
			// check if within agent is kept (person = 10000000)
			Assert.assertTrue("Agent traveling inside WSC is not left in the reduced scenario.", 
					scenario.getPopulation().getPersons().containsKey(Id.createPersonId("10000000")));
			
			// check if out agent is kept (person = 10000001)
			Assert.assertTrue("Agent travelling out from WSC is not left in the reduced scenario.", 
					scenario.getPopulation().getPersons().containsKey(Id.createPersonId("10000001")));
			
			// check if into agent is kept (person = 10000002)
			Assert.assertTrue("Agent travelling into WSC is not left in the reduced scenario.", 
					scenario.getPopulation().getPersons().containsKey(Id.createPersonId("10000002")));
			
			// check if thru agent is kept (person = 10000003)
			Assert.assertTrue("Agent travelling through WSC is not left in the reduced scenario.", 
					scenario.getPopulation().getPersons().containsKey(Id.createPersonId("10000003")));
			
			// check if unrelated agent is removed (person = 10000005)
			Assert.assertTrue("Agent unrelated to WSC is not removed in the reduced scenario.", 
					!scenario.getPopulation().getPersons().containsKey(Id.createPersonId("10000005")));
			
			Controler controler = RunLosAngelesScenario.prepareControler(scenario);
			controler.run();
			
			
		} catch ( Exception ee) {
			ee.printStackTrace();
			Logger.getLogger(this.getClass()).fatal("there was an exception: \n" + ee );
			Assert.fail();
		}
	}

}
