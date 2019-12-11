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

import static org.matsim.core.config.groups.ControlerConfigGroup.RoutingAlgorithmType.FastAStarLandmarks;

import java.util.Arrays;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.QSimConfigGroup.TrafficDynamics;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.parkingCost.ParkingCostConfigGroup;
import org.matsim.parkingCost.ParkingCostModule;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;

/**
 * @author nagel, ikaddoura
 *
 */
public class RunLosAngelesScenario {
	private static final Logger log = Logger.getLogger(RunLosAngelesScenario.class );

	public static void main(String[] args) {
		
		for (String arg : args) {
			log.info( arg );
		}
		
		if ( args.length==0 ) {
			args = new String[] {"./scenarios/los-angeles-v1.0/input/los-angeles-v1.0-0.1pct.config.xml"}  ;
		}
				
		Config config = prepareConfig( args ) ;
		Scenario scenario = prepareScenario( config ) ;
		Controler controler = prepareControler( scenario ) ;
		controler.run() ;
	}
	
	public static Controler prepareControler( Scenario scenario ) {		
		Gbl.assertNotNull(scenario);
		
		final Controler controler = new Controler( scenario );
		
		if (controler.getConfig().transit().isUsingTransitInMobsim()) {
			// use the sbb pt raptor router
			controler.addOverridingModule( new AbstractModule() {
				@Override
				public void install() {
					install( new SwissRailRaptorModule() );
				}
			} );
		} else {
			log.warn("Public transit will be teleported and not simulated in the mobsim! "
					+ "This will have a significant effect on pt-related parameters (travel times, modal split, and so on). "
					+ "Should only be used for testing or car-focused studies with a fixed modal split.  ");
		}
		
		// use the (congested) car travel time for the teleported ride modes
		controler.addOverridingModule( new AbstractModule() {
			@Override
			public void install() {
				addTravelTimeBinding( TransportMode.ride ).to( networkTravelTime() );
				addTravelDisutilityFactoryBinding( TransportMode.ride ).to( carTravelDisutilityFactoryKey() );
				
				addTravelTimeBinding( "ride_taxi" ).to( networkTravelTime() );
				addTravelDisutilityFactoryBinding( "ride_taxi" ).to( carTravelDisutilityFactoryKey() );
				
				addTravelTimeBinding( "ride_school_bus" ).to( networkTravelTime() );
				addTravelDisutilityFactoryBinding( "ride_school_bus" ).to( carTravelDisutilityFactoryKey() );
			}
		} );
		
		// use income dependent marginal utility of money
		IncomeDependentPlanScoringFunctionFactory initialPlanScoringFunctionFactory = new IncomeDependentPlanScoringFunctionFactory(controler.getScenario());
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				this.bindScoringFunctionFactory().toInstance(initialPlanScoringFunctionFactory);
			}
		});
		
		// use parking cost module
		controler.addOverridingModule(new ParkingCostModule());

		return controler;
	}
	
	public static Scenario prepareScenario( Config config ) {
		Gbl.assertNotNull( config );
		final Scenario scenario = ScenarioUtils.loadScenario( config );
		return scenario;
	}
	
	public static Config prepareConfig(String [] args, ConfigGroup... customModules) {
		OutputDirectoryLogging.catchLogEntries();
		
		String[] typedArgs = Arrays.copyOfRange( args, 1, args.length );
		
		ConfigGroup[] customModulesToAdd = new ConfigGroup[]{ new SwissRailRaptorConfigGroup(), new ParkingCostConfigGroup() };
		ConfigGroup[] customModulesAll = new ConfigGroup[customModules.length + customModulesToAdd.length];
		
		int counter = 0;
		for (ConfigGroup customModule : customModules) {
			customModulesAll[counter] = customModule;
			counter++;
		}
		
		for (ConfigGroup customModule : customModulesToAdd) {
			customModulesAll[counter] = customModule;
			counter++;
		}
		
		final Config config = ConfigUtils.loadConfig( args[ 0 ], customModulesAll );
		
		config.controler().setRoutingAlgorithmType( FastAStarLandmarks );
		
		config.subtourModeChoice().setProbaForRandomSingleTripMode( 0.5 );
		
		config.plansCalcRoute().setRoutingRandomness( 3. );
		config.plansCalcRoute().removeModeRoutingParams(TransportMode.ride);
		config.plansCalcRoute().removeModeRoutingParams(TransportMode.pt);
		config.plansCalcRoute().removeModeRoutingParams("undefined");
	
		config.qsim().setInsertingWaitingVehiclesBeforeDrivingVehicles( true );
				
		// vsp defaults
		config.vspExperimental().setVspDefaultsCheckingLevel( VspExperimentalConfigGroup.VspDefaultsCheckingLevel.abort );
		config.plansCalcRoute().setInsertingAccessEgressWalk( true );
		config.qsim().setUsingTravelTimeCheckInTeleportation( true );
		config.qsim().setTrafficDynamics( TrafficDynamics.kinematicWaves );
				
		// activities:
		for ( long ii = 600 ; ii <= 97200; ii+=600 ) {
			config.planCalcScore().addActivityParams( new ActivityParams( "home_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "work_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "university_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "school_" + ii ).setTypicalDuration( ii ).setOpeningTime(8*3600.).setClosingTime(18*3600.) );
			config.planCalcScore().addActivityParams( new ActivityParams( "escort_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "schoolescort_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "schoolpureescort_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "schoolridesharing_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "non-schoolescort_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "shop_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "maintenance_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "HHmaintenance_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "personalmaintenance_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "eatout_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "eatoutbreakfast_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "eatoutlunch_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "eatoutdinner_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "visiting_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "discretionary_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "specialevent_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "atwork_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "atworkbusiness_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "atworklunch_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "atworkother_" + ii ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "business_" + ii ).setTypicalDuration( ii ) );	
		}
		config.planCalcScore().addActivityParams( new ActivityParams( "freightStart" ).setTypicalDuration( 12.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "freightEnd" ).setTypicalDuration( 12.*3600. ) );

		ConfigUtils.applyCommandline( config, typedArgs ) ;

		return config ;
	}
	
}
