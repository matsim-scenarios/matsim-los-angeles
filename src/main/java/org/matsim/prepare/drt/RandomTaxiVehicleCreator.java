/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
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

package org.matsim.prepare.drt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.DvrpVehicleSpecification;
import org.matsim.contrib.dvrp.fleet.FleetWriter;
import org.matsim.contrib.dvrp.fleet.ImmutableDvrpVehicleSpecification;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;


/**
 * @author  ikaddoura
 *
 */
public class RandomTaxiVehicleCreator {
	private static final Logger log = Logger.getLogger(RandomTaxiVehicleCreator.class);

	private final String vehiclesFilePrefix;
	private final CoordinateTransformation ct;
	private final String vehiclePrefix;

	private final Scenario scenario ;
	private final Random random = MatsimRandom.getRandom();
	private final ShapeFileUtils shpUtils;
	private final Network drtNetwork;

	public static void main(String[] args) {

		String networkFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/input/los-angeles-v1.0-network_2019-12-10.xml.gz";
		String drtServiceAreaShapeFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/original-data/shp-data/WSC_Boundary_SCAG/WSC_Boundary_SCAG.shp";
	    CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation("EPSG:3310", "EPSG:3310"); 

	    String vehiclePrefix = "drt2";
		String vehiclesFilePrefix = "LA-WSC-" + vehiclePrefix + "-";
	    int numberOfVehicles = 1000;
	    int seats = 4;
	    
		RandomTaxiVehicleCreator tvc = new RandomTaxiVehicleCreator(networkFile, drtServiceAreaShapeFile, vehiclesFilePrefix, vehiclePrefix, ct);
		
		tvc.run(numberOfVehicles, seats);
}

	public RandomTaxiVehicleCreator(String networkfile, String drtServiceAreaShapeFile, String vehiclesFilePrefix, String vehiclePrefix, CoordinateTransformation ct) {
		this.vehiclesFilePrefix = vehiclesFilePrefix;
		this.vehiclePrefix = vehiclePrefix;
		this.ct = ct;
		
		Config config = ConfigUtils.createConfig();
		config.global().setCoordinateSystem("EPSG:3310");
		config.network().setInputFile(networkfile);
		this.scenario = ScenarioUtils.loadScenario(config);
		
		shpUtils = new ShapeFileUtils(drtServiceAreaShapeFile);
		
		drtNetwork = NetworkUtils.createNetwork();
		Set<String> filterTransportModes = new HashSet<>();
		filterTransportModes.add(TransportMode.car);
		new TransportModeNetworkFilter(scenario.getNetwork()).filter(drtNetwork, filterTransportModes);
	}
	
	public final void run(int amount, int seats) {

		List<DvrpVehicleSpecification> vehicles = new ArrayList<>();

		for (int i = 0 ; i< amount; i++) {
			Link link = null;
			
			while (link == null) {
				Point p = shpUtils.getRandomPointInServiceArea(random);
				link = NetworkUtils.getNearestLinkExactly(drtNetwork, ct.transform( MGC.point2Coord(p)));
				if (shpUtils.isCoordInDrtServiceArea(link.getFromNode().getCoord()) && shpUtils.isCoordInDrtServiceArea(link.getToNode().getCoord())) {
					if (link.getAllowedModes().contains(TransportMode.car)) {
						// ok
					} else {
						link = null;
					}
					// ok, the link is within the shape file
				} else {
					link = null;
				}
			}
			
			if (i%5000 == 0) log.info("#"+i);

			vehicles.add(ImmutableDvrpVehicleSpecification.newBuilder().id(Id.create(this.vehiclePrefix + i, DvrpVehicle.class))
					.startLinkId(link.getId())
					.capacity(seats)
					.serviceBeginTime(Math.round(1))
					.serviceEndTime(Math.round(30 * 3600))
					.build());
		}
		new FleetWriter(vehicles.stream()).write(vehiclesFilePrefix + amount + "veh_" + seats + "seats.xml.gz");
	}

}
