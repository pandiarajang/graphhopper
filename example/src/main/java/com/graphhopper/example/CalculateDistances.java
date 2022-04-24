package com.graphhopper.example;


import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.GHPoint;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;

public class CalculateDistances {
	public static void main(String[] args) throws InterruptedException {
		String relDir = args.length == 1 ? args[0] : "";
		relDir = "C:\\Users\\pandi\\Documents\\repository\\graphhopper\\";
		GraphHopper hopper = createGraphHopperInstance(relDir + "core\\files\\texas-latest.osm.pbf");
		
		double randomLat = Math.random();
		
		String file_name = "C:\\Users\\pandi\\Documents\\repository\\UpGradDS\\MS\\RL\\WorkingModels\\source_data.csv";  
		String line = "";  
		String splitBy = ",";  

		List<Location> srclocs = new ArrayList<Location>();
		List<Location> dstlocs = new ArrayList<Location>();
		try   
		{  
			try (//parsing a CSV file into BufferedReader class constructor  
					BufferedReader br = new BufferedReader(new FileReader(file_name))) {
				long counter = 0;
				while ((line = br.readLine()) != null && counter <1000)   //returns a Boolean value  
				{
					counter = counter+1;
					Location srcloc = new Location(null,0.0,0.0);
					String[] data = line.split(splitBy);    // use comma as separator  
					if(!data[1].equals("id")) {
						srcloc.setId(data[1]);
						srcloc.setLat(Double.valueOf(data[2]));
						srcloc.setLon(Double.valueOf(data[3]));
						srclocs.add(srcloc);
						Location dstloc = new Location(data[1],Double.valueOf(data[2]),Double.valueOf(data[2]));
						dstlocs.add(dstloc);
					}
				}
			}  
		}   
		catch (IOException e)   
		{  
			e.printStackTrace();  
		}
		List<Distance> dists= Collections.synchronizedList(new ArrayList<Distance>());

		List<Location[]> failedLocPairs = new ArrayList<Location[]>();

		System.out.println("CalculateDistance.main() lenght of src locs + "+srclocs.size());
		System.out.println("CalculateDistance.main() lenght of dst locs + "+dstlocs.size());
		ExecutorService service = Executors.newCachedThreadPool();
		srclocs.parallelStream().forEach(srcLoc -> service.execute(
				() -> {
					dstlocs.forEach(dstLoc -> {
						if( srcLoc.getId() != dstLoc.getId()) {
							try {
								double srcLat = srcLoc.getLat();
								double srcLon = srcLoc.getLon();
								double dstLat = dstLoc.getLat();
								double dstLon = dstLoc.getLon();
								double distValue = 0.0;

								distValue = routing(hopper, srcLat,srcLon,dstLat,dstLon); 
								Distance distance = new Distance(srcLoc.getId(), dstLoc.getId(), distValue);
								dists.add(distance);
								if(dists.size()%100 == 0) {
									System.out.println("CalculateDistance.main() processed :"+dists.size() + " src :" +srcLoc.getId() +": dst :" + dstLoc.getId());
								}
							}catch (Exception e) {

								Location[] failedLocPair = new Location[] {
										srcLoc,dstLoc
								};
								failedLocPairs.add(failedLocPair);
								e.printStackTrace();
								}
						}
					});
				}
				));
		service.shutdown();
		service.awaitTermination(1, TimeUnit.DAYS); // Arbitrary value
		System.out.println("CalculateDistance.main() :" + dists.size());
		System.out.println("CalculateDistance.main() failed loc pair " +failedLocPairs.size() );

		//speedModeVersusFlexibleMode(hopper);
		// headingAndAlternativeRoute(hopper);
		//customizableRouting(relDir + "core/files/north-america-latest.osm.pbf");

		// release resources to properly shutdown or start a new instance
		hopper.close();
	}

	static GraphHopper createGraphHopperInstance(String ghLoc) {
		GraphHopper hopper = new GraphHopper();
		hopper.setOSMFile(ghLoc);
		// specify where to store graphhopper files
		hopper.setGraphHopperLocation("target/routing-graph-cache");

		// see docs/core/profiles.md to learn more about profiles
		hopper.setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest").setTurnCosts(false));

		// this enables speed mode for the profile we called car
		hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));

		// now this can take minutes if it imports or a few seconds for loading of course this is dependent on the area you import
		hopper.importOrLoad();
		return hopper;
	}

	public static double routing(GraphHopper hopper,double srcLat,double srcLon,double dstLat, double dstLon) {
		// simple configuration of the request object
		GHRequest req = new GHRequest(srcLat, srcLon, dstLat, dstLon).
				// note that we have to specify which profile we are using even when there is only one like here
				setProfile("car").
				// define the language for the turn instructions
				setLocale(Locale.US);
		
		GHResponse rsp = hopper.route(req);

		// handle errors
		if (rsp.hasErrors())
			throw new RuntimeException(rsp.getErrors().toString());

		// use the best path, see the GHResponse class for more possibilities.
		ResponsePath path = rsp.getBest();

		// points, distance in meters and time in millis of the full path
		PointList pointList = path.getPoints();
		double distance = path.getDistance();
		long timeInMs = path.getTime();
		//System.out.println("RoutingExample.routing() + "+distance);
		Translation tr = hopper.getTranslationMap().getWithFallBack(Locale.UK);
		InstructionList il = path.getInstructions();
		// iterate over all turn instructions
		for (Instruction instruction : il) {
			// System.out.println("distance " + instruction.getDistance() + " for instruction: " + instruction.getTurnDescription(tr));
		}
		assert il.size() == 6;
		assert Helper.round(path.getDistance(), -2) == 900;
		return distance;
	}

	public static void speedModeVersusFlexibleMode(GraphHopper hopper) {
		GHRequest req = new GHRequest(42.508552, 1.532936, 42.507508, 1.528773).
				setProfile("car").setAlgorithm(Parameters.Algorithms.ASTAR_BI).putHint(Parameters.CH.DISABLE, true);
		GHResponse res = hopper.route(req);
		if (res.hasErrors())
			throw new RuntimeException(res.getErrors().toString());
		assert Helper.round(res.getBest().getDistance(), -2) == 900;
	}

	public static void headingAndAlternativeRoute(GraphHopper hopper) {
		// define a heading (direction) at start and destination
		GHRequest req = new GHRequest().setProfile("car").
				addPoint(new GHPoint(42.508774, 1.535414)).addPoint(new GHPoint(42.506595, 1.528795)).
				setHeadings(Arrays.asList(180d, 90d)).
				// use flexible mode (i.e. disable contraction hierarchies) to make heading and pass_through working
				putHint(Parameters.CH.DISABLE, true);
		// if you have via points you can avoid U-turns there with
		// req.getHints().putObject(Parameters.Routing.PASS_THROUGH, true);
		GHResponse res = hopper.route(req);
		if (res.hasErrors())
			throw new RuntimeException(res.getErrors().toString());
		assert res.getAll().size() == 1;
		assert Helper.round(res.getBest().getDistance(), -2) == 800;

		// calculate alternative routes between two points (supported with and without CH)
		req = new GHRequest().setProfile("car").
				addPoint(new GHPoint(42.502904, 1.514714)).addPoint(new GHPoint(42.511953, 1.535914)).
				setAlgorithm(Parameters.Algorithms.ALT_ROUTE);
		req.getHints().putObject(Parameters.Algorithms.AltRoute.MAX_PATHS, 3);
		res = hopper.route(req);
		if (res.hasErrors())
			throw new RuntimeException(res.getErrors().toString());
		assert res.getAll().size() == 2;
		assert Helper.round(res.getBest().getDistance(), -2) == 2300;
	}

	public static void customizableRouting(String ghLoc) {
		GraphHopper hopper = new GraphHopper();
		hopper.setOSMFile(ghLoc);
		hopper.setGraphHopperLocation("target/routing-custom-graph-cache");
		hopper.setProfiles(new CustomProfile("car_custom").setCustomModel(new CustomModel()).setVehicle("car"));

		// The hybrid mode uses the "landmark algorithm" and is up to 15x faster than the flexible mode (Dijkstra).
		// Still it is slower than the speed mode ("contraction hierarchies algorithm") ...
		hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("car_custom"));
		hopper.importOrLoad();

		// ... but for the hybrid mode we can customize the route calculation even at request time:
		// 1. a request with default preferences
		GHRequest req = new GHRequest().setProfile("car_custom").
				addPoint(new GHPoint(42.506472, 1.522475)).addPoint(new GHPoint(42.513108, 1.536005));

		GHResponse res = hopper.route(req);
		if (res.hasErrors())
			throw new RuntimeException(res.getErrors().toString());

		assert Math.round(res.getBest().getTime() / 1000d) == 96;

		// 2. now avoid primary roads and reduce maximum speed, see docs/core/custom-models.md for an in-depth explanation
		// and also the blog posts https://www.graphhopper.com/?s=customizable+routing
		CustomModel model = new CustomModel();
		model.addToPriority(If("road_class == PRIMARY", MULTIPLY, 0.5));

		// unconditional limit to 100km/h
		model.addToPriority(If("true", LIMIT, 100));

		req.setCustomModel(model);
		res = hopper.route(req);
		if (res.hasErrors())
			throw new RuntimeException(res.getErrors().toString());

		assert Math.round(res.getBest().getTime() / 1000d) == 165;
	}
}
