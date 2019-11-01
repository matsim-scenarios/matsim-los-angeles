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

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

/**
* @author ikaddoura
*/

public class CreateVehiclesForSchedule {

	private final TransitSchedule schedule;
	private final Vehicles vehicles;
	private final int seats;
	private final String idPrefix;

	public CreateVehiclesForSchedule(final TransitSchedule schedule, final Vehicles vehicles, final int seats, final String idprefix) {
		this.schedule = schedule;
		this.vehicles = vehicles;
		this.seats = seats;
		this.idPrefix = idprefix;
	}

	public void run() {
		VehiclesFactory vehFactotry = this.vehicles.getFactory();
		VehicleType vehicleType = vehFactotry.createVehicleType(Id.create(idPrefix + "defaultTransitVehicleType", VehicleType.class));
		vehicleType.getCapacity().setSeats( seats );
		vehicleType.getCapacity().setStandingRoom( 0 );
		vehicleType.setPcuEquivalents(0.);
		this.vehicles.addVehicleType(vehicleType);

		long vehId = 0;
		for (TransitLine line : this.schedule.getTransitLines().values()) {
			for (TransitRoute route : line.getRoutes().values()) {
				for (Departure departure : route.getDepartures().values()) {
					Vehicle veh = vehFactotry.createVehicle(Id.create(idPrefix + Long.toString(vehId++), Vehicle.class), vehicleType);
					this.vehicles.addVehicle(veh);
					departure.setVehicleId(veh.getId());
				}
			}
		}
	}
}

