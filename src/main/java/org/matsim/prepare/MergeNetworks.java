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
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;


/**
 * 
 * Merges several network files...
 * 
 * @author ikaddoura
 *
 */
public class MergeNetworks {
	
	private final Logger log = Logger.getLogger(MergeNetworks.class);

	private final String outputDir;
	private final String networkCS ;

	private String outnetworkPrefix ;
	
	public static void main(String[] args) {
		String rootDirectory = null;
		
		if (args.length == 1) {
			rootDirectory = args[0];
		} else {
			throw new RuntimeException("Please set the root directory (the directory above 'scag_model'). Aborting...");
		}
		
		if (!rootDirectory.endsWith("/")) rootDirectory = rootDirectory + "/";
				
		String prefix = "scag-network_with-merged-pt_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		String outDir = rootDirectory + "/matsim-input-files/network-with-pt/";
		String networkFile0 = rootDirectory + "/matsim-input-files/network/scag-network_2019-12-10_network.xml.gz";
		String networkFile1 = rootDirectory + "/matsim-input-files/pt/LA_DOT_2019-07-12/scag-network-with-pt_2019-11-01.xml.gz";
		String networkFile2 = rootDirectory + "/matsim-input-files/pt/LA_GO_BUS_2019-10-02/scag-network-with-pt_2019-11-01.xml.gz";
		String networkFile3 = rootDirectory + "/matsim-input-files/pt/LA_METRO_BUS_2019-10-04/scag-network-with-pt_2019-11-01.xml.gz";
		String networkFile4 = rootDirectory + "/matsim-input-files/pt/LA_METRO_RAIL_2019-10-29/scag-network-with-pt_2019-11-01.xml.gz";
		String networkFile5 = rootDirectory + "/matsim-input-files/pt/METROLINK_2019-10-15/scag-network-with-pt_2019-11-19.xml.gz";

		String crs = "EPSG:3310";
		MergeNetworks networkMerger = new MergeNetworks(crs, outDir, prefix);
		
		Network baseNetwork = networkMerger.createNetwork(networkFile0);
		
		MergeNetworks.merge(baseNetwork, "", networkMerger.createNetwork(networkFile1));
		MergeNetworks.merge(baseNetwork, "", networkMerger.createNetwork(networkFile2));
		MergeNetworks.merge(baseNetwork, "", networkMerger.createNetwork(networkFile3));
		MergeNetworks.merge(baseNetwork, "", networkMerger.createNetwork(networkFile4));
		MergeNetworks.merge(baseNetwork, "", networkMerger.createNetwork(networkFile5));

		networkMerger.writeNetwork(baseNetwork);
	}
	
	private Network createNetwork(String networkFile) {
		Config config = ConfigUtils.createConfig();
		config.network().setInputFile(networkFile);
		config.network().setInputCRS(networkCS);
		config.global().setCoordinateSystem(networkCS);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Network network = scenario.getNetwork();
		return network;
	}
	
	/**
	 * Merges two networks into one, by copying all nodes and links from the addNetworks to the baseNetwork.
	 *
	 * @param baseNetwork
	 * @param addNetwork
	 */
	public static void merge(final Network baseNetwork, final String addPrefix, final Network addNetwork) {
		double capacityFactor = baseNetwork.getCapacityPeriod() / addNetwork.getCapacityPeriod();
		NetworkFactory factory = baseNetwork.getFactory();
		for (Node node : addNetwork.getNodes().values()) {
			Node node2 = (Node) factory.createNode(Id.create(addPrefix + node.getId().toString(), Node.class), node.getCoord());
			baseNetwork.addNode(node2);
		}
		for (Link link : addNetwork.getLinks().values()) {
			Id<Node> fromNodeId = Id.create(addPrefix + link.getFromNode().getId().toString(), Node.class);
			Id<Node> toNodeId = Id.create(addPrefix + link.getToNode().getId().toString(), Node.class);
			Node fromNode = baseNetwork.getNodes().get(fromNodeId);
			Node toNode = baseNetwork.getNodes().get(toNodeId);
			Link link2 = factory.createLink(Id.create(addPrefix + link.getId().toString(), Link.class),
					fromNode, toNode);
			link2.setAllowedModes(link.getAllowedModes());
			link2.setCapacity(link.getCapacity() * capacityFactor);
			link2.setFreespeed(link.getFreespeed());
			link2.setLength(link.getLength());
			link2.setNumberOfLanes(link.getNumberOfLanes());
			baseNetwork.addLink(link2);
		}
	}

	public MergeNetworks(String networkCoordinateSystem, String outputDir, String prefix) {
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

	public void writeNetwork(Network network){
		String outNetwork = this.outputDir + outnetworkPrefix + "_network.xml.gz";
		log.info("Writing network to " + outNetwork);
		new NetworkWriter(network).write(outNetwork);
		log.info("... done.");
	}
}
