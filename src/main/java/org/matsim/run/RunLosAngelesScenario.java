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

import java.io.IOException;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.matsim.analysis.IKAnalysisRunLA;
import org.matsim.analysis.moneyEventAnalysis.RunMoneyEventAnalysis;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.drt.routing.DrtRoute;
import org.matsim.contrib.drt.routing.DrtRouteFactory;
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
import org.matsim.core.population.routes.RouteFactories;
import org.matsim.core.router.AnalysisMainModeIdentifier;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.parkingCost.ParkingCostConfigGroup;
import org.matsim.parkingCost.ParkingCostModule;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
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
			args = new String[] {"./scenarios/los-angeles-v1.1/input/los-angeles-v1.1-0.1pct.config.xml"}  ;
		}
				
		Config config = prepareConfig( args ) ;
		Scenario scenario = prepareScenario( config ) ;
		Controler controler = prepareControler( scenario ) ;
		controler.run() ;
				
		// run analysis
		runAnalysis(config);
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
		
		// use scoring parameters for intermodal PT routing
		controler.addOverridingModule( new AbstractModule() {
			@Override
			public void install() {
				bind(RaptorIntermodalAccessEgress.class).to(LosAngelesRaptorIntermodalAccessEgress.class);
			}
		} );
		
		// use our own Analysis(Main-)ModeIdentifier
		controler.addOverridingModule( new AbstractModule() {
			@Override
			public void install() {
				// mainly relevant for DRT applications:
				bind(MainModeIdentifier.class).to(LosAngelesIntermodalPtDrtRouterModeIdentifier.class);
				// in order to look into the different types of intermodal pt trips:
				bind(AnalysisMainModeIdentifier.class).to(LosAngelesIntermodalPtDrtRouterAnalysisModeIdentifier.class);
			}
		} );
		
		// use income dependent marginal utility of money
		LosAngelesPlanScoringFunctionFactory initialPlanScoringFunctionFactory = new LosAngelesPlanScoringFunctionFactory(controler.getScenario());
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
		
		/*
		 * We need to set the DrtRouteFactory before loading the scenario. Otherwise DrtRoutes in input plans are loaded
		 * as GenericRouteImpls and will later cause exceptions in DrtRequestCreator. So we do this here, although this
		 * class is also used for runs without drt.
		 */
		final Scenario scenario = ScenarioUtils.createScenario( config );

		RouteFactories routeFactories = scenario.getPopulation().getFactory().getRouteFactories();
		routeFactories.setRouteFactory(DrtRoute.class, new DrtRouteFactory());
		
		ScenarioUtils.loadScenario( scenario );
		
		// make sure we start with selected plans only
		for( Person person : scenario.getPopulation().getPersons().values() ){
			person.getPlans().removeIf( (plan) -> plan!=person.getSelectedPlan() ) ;
		}
		
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
		config.plansCalcRoute().removeModeRoutingParams(TransportMode.ride); // since we are using the (congested) car travel time
		config.plansCalcRoute().removeModeRoutingParams(TransportMode.pt); // since we are using simulated public transit
		config.plansCalcRoute().removeModeRoutingParams("undefined"); // since we don't have such a mode
	
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
			config.planCalcScore().addActivityParams( new ActivityParams( "school_" + ii ).setTypicalDuration( ii ) );
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
	
	public static void runAnalysis(Config config) {
		
		log.info("Running analysis...");
		
		String scaleFactor = null;
		if (config.plans().getInputFile().contains("0.1pct")) {
			scaleFactor = "1000";
		} else if (config.plans().getInputFile().contains("1pct")) {
			scaleFactor = "100";
		} else if (config.plans().getInputFile().contains("5pct")) {
			scaleFactor = "20";
		} else if (config.plans().getInputFile().contains("10pct")) {
			scaleFactor = "10";
		} else if (config.plans().getInputFile().contains("25pct")) {
			scaleFactor = "4";
		} else if (config.plans().getInputFile().contains("100pct")) {
			scaleFactor = "1";
		} else {
			log.warn("Unknown sample size. Using default value of 1.");
			scaleFactor = "1";
		}
		
		String[] args1 = new String[] {
				config.controler().getOutputDirectory(),
				config.controler().getRunId(),
				"null", // TODO: reference run, hard to automate
				"null", // TODO: reference run, hard to automate
				config.global().getCoordinateSystem(),
				"https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/original-data/shp-data/hexagon-grid-7500/hexagon-grid-7500.shp",
				"EPSG:3310",
				"ID",
				"https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/original-data/shp-data/WSC_Boundary_SCAG/WSC_Boundary_SCAG.shp", // shapeFileFilterBerlinZone
				"EPSG:3310",
				"https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/us/los-angeles/los-angeles-v1.0/original-data/shp-data/WSC-LA-planning-area_without-WSC/WSC-LA-planning-area_without-WSC.shp", // shapeFileFilterBerlinZone
				"EPSG:3310",
				scaleFactor,
				"null", // visualizationScriptInputDirectory
				};
		
		try {
			IKAnalysisRunLA.main(args1);
		} catch (IOException e) {
			log.error(e.getStackTrace());
			throw new RuntimeException(e.getMessage());
		}
		
		// also run the money events analysis
		String[] args2 = new String[] { config.controler().getOutputDirectory(),
				config.controler().getRunId(),
				config.controler().getOutputDirectory()
				};
		
		RunMoneyEventAnalysis.main(args2);
		
		log.info("Running analysis... Done.");
	}	
}
