/* *********************************************************************** *
 * project: org.matsim.*
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

/**
 * 
 */
package org.matsim.prepare;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.algorithms.NetworkSimplifier;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.io.OsmNetworkReader;
import org.opengis.feature.simple.SimpleFeature;


/**
 * 
 * requires the following:
 * 
 * 1) extract LA region from SouthCalifornia file: 
 * osmosis --rb file=socal-latest-2019-09-22.osm.pbf  --bounding-box left=-118.66 bottom=33.44 right=-116.95 top=34.33 --wb file=/Users/ihab/Desktop/socal-latest-2019-09-22_extracted-LA.osm.pbf
 *
 * 2) filter LA region osm file (detailed network)
 * osmosis --rb file=socal-latest-2019-09-22_extracted-LA.osm.pbf --tf accept-ways highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,tertiary,motorway_junction,residential,unclassified,living_street --used-node --wb LA-region-2019-09-22_incl-residential-and-living-street.osm.pbf
 *
 * 3) filter south california osm file (coarse network)
 * osmosis --rb file=socal-latest-2019-09-22.osm.pbf --tf accept-ways highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,tertiary,motorway_junction --used-node --wb socal-2019-09-22_incl-tertiary.osm.pbf
 * 
 * 4) merge coarse and detailed network
 * osmosis --rb file=socal-2019-09-22_incl-tertiary.osm.pbf --rb LA-region-2019-09-22_incl-residential-and-living-street.osm.pbf --merge --wx socal-LA-network_2019-09-22.osm
 * 
 * @author ikaddoura
 *
 */
public class CreateNetwork {
	
	private final static Logger log = Logger.getLogger(CreateNetwork.class);
	
	private final static CSVFormat csvFormat = CSVFormat.DEFAULT.withFirstRecordAsHeader();

	private final String INPUT_OSMFILE ;
	private final String outputDir;
	private final String networkCS ;

	private Network network = null;
	private String outnetworkPrefix ;
	
	private static Collection<SimpleFeature> features = null;
	private static Map<String, Map<String, Double>> tier2TAZ2PCosts = new HashMap<>();
	
	public static void main(String[] args) throws NumberFormatException, IOException {
		String rootDirectory = null;
		
		if (args.length == 1) {
			rootDirectory = args[0];
		} else {
			throw new RuntimeException("Please set the root directory (the directory above 'scag_model'). Aborting...");
		}
		
		if (!rootDirectory.endsWith("/")) rootDirectory = rootDirectory + "/";
		
		String osmfile = rootDirectory + "osm-data/socal-LA-network_2019-09-22.osm";
		String shapefile = rootDirectory + "shp-files/Tier_2_Transportation_Analysis_Zones_TAZs_in_SCAG_EPSG3310/Tier_2_Transportation_Analysis_Zones_TAZs_in_SCAG_EPSG3310.shp";
		// TODO: check if external taz has parking cost??
//		String shapefileExternal = rootDirectory + "shp-files/SCAG_T1_external_Airport_Seaport/SCAG_T1_external_Airport_Seaport.shp";
		String sedfile = rootDirectory + "LA012.2013-20_SCAG/Zonal SED/sed_data.csv";
		
		String prefix = "scag-network_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		String outDir = rootDirectory + "/matsim-input-files/network/";

		String crs = "EPSG:3310";
		CreateNetwork networkCreator = new CreateNetwork(osmfile, crs , outDir, prefix);

		boolean keepPaths = false;
		boolean clean = true;
		boolean simplify = false;
		
		features = ShapeFileReader.getAllFeatures(shapefile);
		
		log.info("Reading sed_data...");
		for (CSVRecord csvRecord : new CSVParser(Files.newBufferedReader(Paths.get(sedfile)), csvFormat)) {
			String tier2TAZId = csvRecord.get(1);
			double dailyPCost = Double.valueOf(csvRecord.get(57));
			double oneHourPCost = Double.valueOf(csvRecord.get(58));
			double extraHourPCost = Double.valueOf(csvRecord.get(59));
			double maxDailyPCost = Double.valueOf(csvRecord.get(60));
			
			Map<String, Double> pCosts = new HashMap<>();
			pCosts.put("dailyPCost", dailyPCost);
			pCosts.put("oneHourPCost", oneHourPCost);
			pCosts.put("extraHourPCost", extraHourPCost);
			pCosts.put("maxDailyPCost", maxDailyPCost);
			
			tier2TAZ2PCosts.put(tier2TAZId, pCosts);		
		}
		log.info("Reading sed_data done!...");
		
		networkCreator.createNetwork(keepPaths, simplify, clean);
		networkCreator.adjustNetwork();		
		networkCreator.writeNetwork();
	}
	
	private void adjustNetwork() {
		for (Link link : network.getLinks().values()) {
			if (link.getAllowedModes().contains("car")) {
				Set<String> modes = new HashSet<>();
				modes.add("car");
				modes.add("freight");
				modes.add("ride");
				modes.add("ride_taxi");
				modes.add("ride_school_bus");
				link.setAllowedModes(modes);
			}
		}
		
		// TODO: further modifications?
		int externalLinkCounter = 0;
		int linkCounter = 0;
		for (Link link: network.getLinks().values()) {
			Coord coord = link.getCoord();
			Point point = MGC.coord2Point(coord);
			boolean foundFeature = false;
			for (SimpleFeature feature : features ) {
				String tier2TAZId = feature.getAttribute("Tier2").toString();
				Geometry geometry = (Geometry) feature.getDefaultGeometry();
				if (geometry.contains(point)) {
					link.getAttributes().putAttribute("dailyPCost", tier2TAZ2PCosts.get(tier2TAZId).get("dailyPCost"));
					link.getAttributes().putAttribute("oneHourPCost", tier2TAZ2PCosts.get(tier2TAZId).get("oneHourPCost"));
					link.getAttributes().putAttribute("extraHourPCost", tier2TAZ2PCosts.get(tier2TAZId).get("extraHourPCost"));
					link.getAttributes().putAttribute("maxDailyPCost", tier2TAZ2PCosts.get(tier2TAZId).get("maxDailyPCost"));
//					log.info("Link " + link.getId() + " found within TAZ map...");
					foundFeature = true;
					break;
				}
			}
			if (!foundFeature) {
				if (externalLinkCounter <=5) log.info("Link " + link.getId() + " is not within TAZ map...");
				if (externalLinkCounter == 5) log.info("Further types of this warning will not be printed.");
				externalLinkCounter++;
			}
			if (linkCounter % 100000 == 0) {
				log.info(linkCounter + " links have been processed.");
			}
			linkCounter++;
		}
	}

	public CreateNetwork(String inputOSMFile, String networkCoordinateSystem, String outputDir, String prefix) {
		this.INPUT_OSMFILE = inputOSMFile;
		this.networkCS = networkCoordinateSystem;
		this.outputDir = outputDir.endsWith("/")?outputDir:outputDir+"/";
		this.outnetworkPrefix = prefix;
				
		OutputDirectoryLogging.catchLogEntries();
		try {
			OutputDirectoryLogging.initLoggingWithOutputDirectory(outputDir);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		log.info("--- set the coordinate system for network to be created to " + this.networkCS + " ---");
	}

	public void writeNetwork(){
		String outNetwork = this.outputDir+outnetworkPrefix+"_network.xml.gz";
		log.info("Writing network to " + outNetwork);
		new NetworkWriter(network).write(outNetwork);
		log.info("... done.");
	}
	
	public void createNetwork(boolean keepPaths, boolean doSimplify, boolean doCleaning){
		CoordinateTransformation ct =
			 TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, networkCS);
		
		if(this.network == null) {
			Config config = ConfigUtils.createConfig();
			Scenario scenario = ScenarioUtils.createScenario(config);
			network = scenario.getNetwork();

			log.info("start parsing from osm file " + INPUT_OSMFILE);
	
			OsmNetworkReader networkReader = new OsmNetworkReader(network,ct, true, true);
						
			if (keepPaths) {
				networkReader.setKeepPaths(true);
			} else {
				networkReader.setKeepPaths(false);
			}

			networkReader.parse(INPUT_OSMFILE);
			log.info("finished parsing osm file");
		}	
		
		if (doSimplify){
			outnetworkPrefix += "_simplified";
			log.info("number of nodes before simplifying:" + network.getNodes().size());
			log.info("number of links before simplifying:" + network.getLinks().size());
			log.info("start simplifying the network");
			/*
			 * simplify network: merge links that are shorter than the given threshold
			 */

			NetworkSimplifier simp = new NetworkSimplifier();
			simp.setMergeLinkStats(false);
			simp.run(network);
			
			log.info("number of nodes after simplifying:" + network.getNodes().size());
			log.info("number of links after simplifying:" + network.getLinks().size());
		}
		
		if (doCleaning){
//			outnetworkPrefix += "_cleaned";
				/*
				 * Clean the Network. Cleaning means removing disconnected components, so that afterwards there is a route from every link
				 * to every other link. This may not be the case in the initial network converted from OpenStreetMap.
				 */
			log.info("number of nodes before cleaning:" + network.getNodes().size());
			log.info("number of links before cleaning:" + network.getLinks().size());
			log.info("attempt to clean the network");
			new NetworkCleaner().run(network);
		}
		
		log.info("number of nodes after cleaning:" + network.getNodes().size());
		log.info("number of links after cleaning:" + network.getLinks().size());
		
		log.info("checking if all count nodes are in the network..");		
	}
}
