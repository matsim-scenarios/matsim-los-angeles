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

package org.matsim.analysis.moneyEventAnalysis;

import org.apache.log4j.Logger;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;

/**
* @author ikaddoura
*/

public class RunMoneyEventAnalysis {
	private static final Logger log = Logger.getLogger(RunMoneyEventAnalysis.class );

	public static void main(String[] args) {
		
		String runDirectory;
		String runId;
		String analysisOutputDirectory;
		
		if (args.length > 0) {
			runDirectory = args[0];
			runId = args[1];
			analysisOutputDirectory = args[2];
		} else {
			runDirectory = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/output/los-angeles-v1.1-1pct/";
			runId = "los-angeles-v1.1-1pct";
			analysisOutputDirectory = "./scenarios/money-event-analysis_" + runId;
		}
		
		if (!runDirectory.endsWith("/")) runDirectory = runDirectory + "/";
		
		log.info("run directory: " + runDirectory);
		log.info("run Id: " + runId);
		log.info("analysis output directory: " + analysisOutputDirectory);
		
		EventsManager events = EventsUtils.createEventsManager();
		MoneyEventAnalysis moneyEventAnalysis = new MoneyEventAnalysis();
		
		events.addHandler(moneyEventAnalysis);
		
		MatsimEventsReader reader = new MatsimEventsReader(events);
		reader.readFile(runDirectory + runId + ".output_events.xml.gz");
		
		moneyEventAnalysis.printResults(analysisOutputDirectory, runId);
	}

}

