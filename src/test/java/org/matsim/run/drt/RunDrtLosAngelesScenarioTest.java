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
package org.matsim.run.drt;

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
public class RunDrtLosAngelesScenarioTest {
	
	@Rule public MatsimTestUtils utils = new MatsimTestUtils() ;
	
	// tests the score of a specific agent (drt user)
	@Test
	public final void test1() {
		try {			
			String[] args = new String[] { "./scenarios/los-angeles-v1.1/input/drt/los-angeles-drt-v1.1-0.1pct.config.xml" };
			
			Config config = RunDrtLosAngelesScenario.prepareConfig(args);
			config.controler().setLastIteration(0);
			config.global().setNumberOfThreads(1);
			config.controler().setOutputDirectory( utils.getOutputDirectory() );
			config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
			config.plans().setInputFile("../../../../test/input/drt/test-drt-agent.xml");
			config.transit().setUseTransit(false);
			
			Scenario scenario = RunDrtLosAngelesScenario.prepareScenario(config);
			Controler controler = RunDrtLosAngelesScenario.prepareControler(scenario);
			controler.run();
			
			Assert.assertEquals("Wrong score in iteration 0.", 146.44725057267536, controler.getScoreStats().getScoreHistory().get(ScoreItem.executed).get(0), MatsimTestUtils.EPSILON);

		} catch ( Exception ee ) {
			ee.printStackTrace();
			Logger.getLogger(this.getClass()).fatal("there was an exception: \n" + ee ) ;
			Assert.fail();
		}

	}
	
	@Test
	public final void test2() {
		try {			
			String[] args = new String[] { "./scenarios/los-angeles-v1.1/input/drt/los-angeles-drt-v1.1-0.1pct.config.xml" };
			
			Config config = RunDrtLosAngelesScenario.prepareConfig(args);
			config.controler().setLastIteration(0);
			config.global().setNumberOfThreads(1);
			config.controler().setOutputDirectory( utils.getOutputDirectory() );
			config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
			config.plans().setInputFile("../../../../test/input/drt/test-pt-agent.xml");
			config.transit().setUseTransit(true);
			
			Scenario scenario = RunDrtLosAngelesScenario.prepareScenario(config);
			Controler controler = RunDrtLosAngelesScenario.prepareControler(scenario);
			controler.run();
			
			Assert.assertEquals("Wrong score in iteration 0.", 82.26476760813718, controler.getScoreStats().getScoreHistory().get(ScoreItem.executed).get(0), MatsimTestUtils.EPSILON);

		} catch ( Exception ee ) {
			ee.printStackTrace();
			Logger.getLogger(this.getClass()).fatal("there was an exception: \n" + ee ) ;
			Assert.fail();
		}

	}

}
