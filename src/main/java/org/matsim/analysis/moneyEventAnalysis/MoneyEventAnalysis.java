/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.events.PersonMoneyEvent;
import org.matsim.api.core.v01.events.handler.PersonMoneyEventHandler;

/**
* @author ikaddoura
*/

public class MoneyEventAnalysis implements PersonMoneyEventHandler {
	private static final Logger log = Logger.getLogger(MoneyEventAnalysis.class );

	double totalFareAmountsDrt1 = 0.;
	double totalFareAmountsDrt2 = 0.;
	double totalParkingAmounts = 0.;
	
	int warnCnt = 0;
	
	@Override
	public void reset(int iteration) {
		this.totalFareAmountsDrt1 = 0.;
		this.totalFareAmountsDrt2 = 0.;
		this.totalParkingAmounts = 0.;
	}

	@Override
	public void handleEvent(PersonMoneyEvent event) {
		
		if (event.getPersonId().toString().startsWith("drt")) {
			log.warn("Skipping person money events by drt 'drivers': " + event.toString());
		} else {
			if (event.getAmount() > 0.) throw new RuntimeException("The amount is expected to be negative. A positive amount means the agent 'earns' money. " + event.toString());
			
			if (event.getTransactionPartner() == null || event.getTransactionPartner().equals("")) {
				// in all previous runs there is no specific information in the parking cost money events.
				// So we have to assume that all money events without information about the transaction partner and purpose are parking costs.
				// (In all future runs there will be additional information in the parking cost money events.)
				if (warnCnt < 1) log.warn("Assuming money events without purpose and transaction partner to be parking costs.");
				warnCnt++;
				
				totalParkingAmounts += event.getAmount();
				
			} else if (event.getTransactionPartner().equals("drt1")) {
				totalFareAmountsDrt1 += event.getAmount();
			
			} else if (event.getTransactionPartner().equals("drt2")) {
				totalFareAmountsDrt2 += event.getAmount();
			
			} else if (event.getPurpose().equals("parking")) {
				totalParkingAmounts += event.getAmount();
			
			} else {
				throw new RuntimeException("Unknown money event: " + event.toString());
			}
		}
	}
	
	public void printResults(String analysisOutputDirectory, String runId) {
		
		if (!analysisOutputDirectory.endsWith("/")) analysisOutputDirectory = analysisOutputDirectory + "/";
		File folder = new File(analysisOutputDirectory);			
		folder.mkdirs();
		
		log.info("total fares (drt1): " + -1 * totalFareAmountsDrt1);
		log.info("total fares (drt2): " + -1 * totalFareAmountsDrt2);
		log.info("total parking costs: " + -1 * totalParkingAmounts);
		
		String fileName = analysisOutputDirectory + runId + ".monetary-payments-analysis.csv";
		File file = new File(fileName);

		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(file));

			bw.write("total fares (drt1);" + -1 * totalFareAmountsDrt1);
			bw.newLine();
			bw.write("total fares (drt2);" + -1 * totalFareAmountsDrt2);
			bw.newLine();
			bw.write("total parking costs;" + -1 * totalParkingAmounts);
			bw.newLine();
			bw.close();

			log.info("Output written to " + fileName);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

