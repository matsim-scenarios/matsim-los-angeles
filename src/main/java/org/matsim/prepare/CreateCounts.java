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

package org.matsim.prepare;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.counts.Counts;
import org.matsim.counts.CountsWriter;

/**
* @author ikaddoura
*/

public class CreateCounts {
	private static final Logger log = Logger.getLogger(CreateCounts.class);

	public static void main(String[] args) throws IOException {
		
		String rootDirectory = null;
		
		if (args.length == 1) {
			rootDirectory = args[0];
		} else {
			throw new RuntimeException("Please set the root directory (the directory above 'scag_model'). Aborting...");
		}
		
		if (!rootDirectory.endsWith("/")) rootDirectory = rootDirectory + "/";
		
		final String prefix = "scag-counts_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		final String outputDirectory = rootDirectory + "matsim-input-files/counts/";
		final String countId2matsimLinkIdcsvFile = rootDirectory + "calibration-data/PEMS/Station_Matched.csv";
		final String countsDataDirectory = rootDirectory + "calibration-data/PEMS/";	
		final String year = "2016";
		final String countsDataDescription = "PEMS_http://pems.dot.ca.gov";

		OutputDirectoryLogging.catchLogEntries();
		try {
			OutputDirectoryLogging.initLoggingWithOutputDirectory(outputDirectory);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		// read count ID to link Id file
		Map<String, Id<Link>> countId2linkId = new HashMap<>();
		for (CSVRecord csvRecord : new CSVParser(Files.newBufferedReader(Paths.get(countId2matsimLinkIdcsvFile)), CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(','))) {	
			String countId = csvRecord.get(0);
			String linkId = csvRecord.get(5);
			countId2linkId.put(countId,Id.createLinkId(linkId));
		}	
		
		// unzip
		String decompressedCountsDataDirectory = countsDataDirectory + "decompressed/";	
		for (File file : getFilesInDirectory(new File(countsDataDirectory), new ArrayList<>())) {		
			if (file.getName().endsWith(".zip")) {
				log.info("Unzipping file " + file.toString());
				unzip(file, decompressedCountsDataDirectory);		
			} else {
				log.warn("Skipping file " + file.toString());
			}
		}
		
		// read counts data
		Map<String, Map<Integer, Double>> countId2time2trafficVolume = new HashMap<>();
		for (File file : getFilesInDirectory(new File(decompressedCountsDataDirectory), new ArrayList<>())) {		
			if (file.getName().contains("month_hours") // the file we decided to use
					&& file.getName().endsWith("_01.txt.gz") // since we have AADT and '_01' means from January to January, we don't need to average over several months
					&& file.getName().contains(year)
					) {			
				log.info("Reading file " + file.toString());			
				InputStream fileStream = new FileInputStream(file);
		        InputStream gzipStream = new GZIPInputStream(fileStream);
		        Reader decoder = new InputStreamReader(gzipStream);	        
		        for (CSVRecord csvRecord : new CSVParser(decoder, CSVFormat.DEFAULT.withDelimiter(','))) {	       	
		        	String countId = csvRecord.get(1);
		        	if (countId2time2trafficVolume.get(countId) == null) {
		        		countId2time2trafficVolume.put(countId, new HashMap<>());
		        	}
		        	
		        	if (csvRecord.get(11).equals("1") || // Monday
		        			csvRecord.get(11).equals("2") || // Tuesday
		        			csvRecord.get(11).equals("3") || // Wednesday
		        			csvRecord.get(11).equals("4") // Thursday
		        			) { 
		        		String hourOfDayString = csvRecord.get(10);
		        		Integer hourOfDay = Integer.valueOf(hourOfDayString);
		        		
		        		String volumeString = csvRecord.get(12);
		        		Double volume = 0.;
		        		if (!volumeString.isEmpty()) {
		        			volume = Double.valueOf(volumeString);
		        		}		        		
			        	countId2time2trafficVolume.get(countId).put(hourOfDay, volume);
		        	}
				}	
			}
		}
		
		// Write out the MATSim counts file
		int noCountIdFoundCounter = 0;
		int countIdFoundCounter = 0;
		Counts<Link> outputCounts = new Counts<>();
		outputCounts.setDescription(countsDataDescription);
		outputCounts.setName(prefix);
		outputCounts.setYear(Integer.valueOf(year));
		for (String countId : countId2time2trafficVolume.keySet()) {					
			Id<Link> linkId = null;
			if (countId2linkId.get(countId) == null) {
//				log.warn("Counting station ID " + countId + " not found in file: " + countId2matsimLinkIdcsvFile.toString());
				noCountIdFoundCounter++;
			} else {
				countIdFoundCounter++;
				linkId = countId2linkId.get(countId);			
				outputCounts.createAndAddCount(linkId, countId);
				for (int h = 0; h <= 23; h++) {
					double volume = 0;
					if (countId2time2trafficVolume.get(countId).get(h) != null) {
						volume = countId2time2trafficVolume.get(countId).get(h);
					} else {
						log.warn("No volume given for hour " + h);
					}
					// MATSim interprets the hours slightly different
					outputCounts.getCount(linkId).createVolume(h + 1, volume);
				}
			}			
		}
		log.info("Counts without MATSim link ID: " + noCountIdFoundCounter);
		log.info("Counts with MATSim link ID written into counts file: " + countIdFoundCounter);

		CountsWriter countsWriter = new CountsWriter(outputCounts);		
		String countsOutputFile = outputDirectory + prefix + ".xml.gz";
		countsWriter.write(countsOutputFile);		
		log.info("Counts file written to " + countsOutputFile);
	}

	private static void unzip(File file, String directory) throws ZipException, IOException {
		java.util.zip.ZipFile zipFile = new ZipFile(file);
		try {
		  Enumeration<? extends ZipEntry> entries = zipFile.entries();
		  while (entries.hasMoreElements()) {
		    ZipEntry entry = entries.nextElement();
		    File entryDestination = new File(directory,  entry.getName());
		    if (entry.isDirectory()) {
		        entryDestination.mkdirs();
		    } else {
		        entryDestination.getParentFile().mkdirs();
		        InputStream in = zipFile.getInputStream(entry);
		        OutputStream out = new FileOutputStream(entryDestination);
		        IOUtils.copy(in, out);
		        IOUtils.closeQuietly(in);
		        out.close();
		    }
		  }
		} finally {
		  zipFile.close();
		}
	}

	public static List<File> getFilesInDirectory(final File folder, List<File> files) {
		for (final File fileEntry : folder.listFiles()) {
	        if (fileEntry.isDirectory()) {
	            getFilesInDirectory(fileEntry, files);
	        } else {
	        	files.add(fileEntry);
	        }
	    }
		return files;
	}

}

