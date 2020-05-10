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

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.analysis.ScoreStatsControlerListener.ScoreItem;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.testcases.MatsimTestUtils;

/**
 * @author nagel
 *
 */
public class RunLosAngelesScenarioTest {
	
	@Rule public MatsimTestUtils utils = new MatsimTestUtils() ;

	// runs test with several test-agents
	// three iterations
	// simulated pt enabled
	@Test
	public final void test1() {
		try {			
			String[] args = new String[] { "./scenarios/los-angeles-v1.1/input/los-angeles-WSC-reduced-v1.1-1pct.config.xml" };
			
			Config config = RunLosAngelesScenario.prepareConfig(args);
			config.controler().setLastIteration(2);
			config.controler().setWriteEventsInterval(0); // don't write events files
			config.global().setNumberOfThreads(1); // only one thread available on travis
			config.controler().setOutputDirectory( utils.getOutputDirectory() );
			config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
			config.plans().setInputFile("../../../test/input/test-agents.xml.gz");
			
			Scenario scenario = RunLosAngelesScenario.prepareScenario(config);
			Controler controler = RunLosAngelesScenario.prepareControler(scenario);
			controler.run();
		
		} catch ( Exception ee ) {
			ee.printStackTrace();
			Logger.getLogger(this.getClass()).fatal("there was an exception: \n" + ee ) ;
			Assert.fail();
		}
	}
	
	// tests the score of a specific agent (ride user), see xlsx file for a manual computation of the score
	// single iteration
	// simulated pt disabled
	@Test
	public final void test2() {
		try {			
			String[] args = new String[] { "./scenarios/los-angeles-v1.1/input/los-angeles-WSC-reduced-v1.1-1pct.config.xml" };
			
			Config config = RunLosAngelesScenario.prepareConfig(args);
			config.controler().setLastIteration(0);
			config.global().setNumberOfThreads(1);
			config.controler().setOutputDirectory( utils.getOutputDirectory() );
			config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
			config.plans().setInputFile("../../../test/input/test-agent_10000099.xml");
			config.transit().setUseTransit(false);
			
			Scenario scenario = RunLosAngelesScenario.prepareScenario(config);
			Controler controler = RunLosAngelesScenario.prepareControler(scenario);
			controler.run();
			
			Assert.assertEquals("Wrong score in iteration 0.", 137.00979198644234, controler.getScoreStats().getScoreHistory().get(ScoreItem.executed).get(0), MatsimTestUtils.EPSILON);

		} catch ( Exception ee ) {
			ee.printStackTrace();
			Logger.getLogger(this.getClass()).fatal("there was an exception: \n" + ee ) ;
			Assert.fail();
		}
	}
}
