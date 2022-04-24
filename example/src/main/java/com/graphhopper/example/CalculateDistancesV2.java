package com.graphhopper.example;

import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.shapes.GHPoint;
import com.opencsv.CSVWriter;

public class CalculateDistancesV2 {



	private static double dallas_min_lat = 32.45254249464805;
	private static double dallas_min_lon = -97.45273105748973;
	private static double dallas_max_lat = 33.24912437959046 ;
	private static double dallas_max_lon = -96.52214779947299;

	private static double latRange = (dallas_max_lat - dallas_min_lat) + 1;
	private static double lonRange = (dallas_max_lon - dallas_min_lon) + 1;

	public static void main(String[] args) throws InterruptedException, IOException {
		String relDir = args.length == 1 ? args[0] : "";
		relDir = "C:\\Users\\pandi\\Documents\\repository\\graphhopper\\";
		GraphHopper hopper = createGraphHopperInstance(relDir + "core\\files\\texas-latest.osm.pbf");

		Set<Distance> dists= Collections.synchronizedSet(new HashSet<Distance>());

		ArrayList<Location> allLocs = new ArrayList<Location>();

		long id = 1;
		while(allLocs.size()<1000) {
			double srcLat = (double)(Math.random() * latRange) + dallas_min_lat;
			double srcLon = (double)(Math.random() * lonRange) + dallas_min_lon;
			double dstLat = (double)(Math.random() * latRange) + dallas_min_lat;
			double dstLon = (double)(Math.random() * lonRange) + dallas_min_lon;
			GHResponse rsp = routing(hopper, srcLat, srcLon, dstLat, dstLon);

			if(!rsp.hasErrors()) {
				Location srcLoc = new Location("H"+id,srcLat,srcLon);
				id = id+1;
				Location dstLoc = new Location("H"+id,dstLat,dstLon);
				id = id+1;
				ResponsePath path = rsp.getBest();
				double distanceValue = path.getDistance();
				Distance distance = new Distance(srcLoc.getId(),dstLoc.getId(),distanceValue);
				allLocs.add(srcLoc);
				allLocs.add(dstLoc);
				dists.add(distance);

			}else {
				List<Throwable> errors = rsp.getErrors();
				List<Integer> idxs = Collections.synchronizedList(new ArrayList<Integer>());
				boolean invalidSrc = false;
				boolean invalidDst = false;
				errors.forEach(error -> {
					if(error instanceof PointNotFoundException) {
						PointNotFoundException exc = (PointNotFoundException) error;
						int idx = (int) exc.getDetails().get("point_index");
						idxs.add(idx);
					}
				});
				for (Iterator iterator = idxs.iterator(); iterator.hasNext();) {
					Integer integer = (Integer) iterator.next();
					if(integer.intValue() == 0) {
						invalidSrc = true;
					}
					if(integer.intValue() == 1) {
						invalidDst = true;
					}
				}

				if(!invalidSrc) {
					Location srcLoc = new Location("H"+id,srcLat,srcLon);
					id = id+1;
					allLocs.add(srcLoc);
				}
				if(!invalidDst) {
					Location dstLoc = new Location("H"+id,dstLat,dstLon);
					id = id+1;
					allLocs.add(dstLoc);
				}
			}
		}
		System.out.println("All Locations 1 : "+allLocs.size());
		System.out.println(" Distances 1 :"+ dists.size());
		
		addDCdata(allLocs);
		
		System.out.println("All Locations 2 : "+allLocs.size());
		System.out.println(" Distances 2 :"+ dists.size());
		
		@SuppressWarnings("unchecked")
		ArrayList<Location> destLocs = (ArrayList<Location>) allLocs.clone(); 
		allLocs.parallelStream().forEach(srcLoc -> {
			destLocs.parallelStream().forEach(dstLoc -> {
				if( srcLoc.getId() != dstLoc.getId()) {
					double srcLat = srcLoc.getLat();
					double srcLon = srcLoc.getLon();
					double dstLat = dstLoc.getLat();
					double dstLon = dstLoc.getLon();
					GHResponse rsp = routing(hopper, srcLat, srcLon, dstLat, dstLon);
					if(!rsp.hasErrors()) {
						ResponsePath path = rsp.getBest();
						double distanceValue = path.getDistance();
						Distance distance = new Distance(srcLoc.getId(),dstLoc.getId(),distanceValue);
						dists.add(distance);
						if(dists.size()%10000 == 0) {
							System.out.println("CalculateDistancesV2.main() processing :"+ dists.size());
						}
					}else {
						System.out.println("CalculateDistancesV2.main() "+ rsp.getErrors());
					}
				}
			});
		});
		hopper.close();
		System.out.println("All Locations 3: "+allLocs.size());
		System.out.println(" Distances 3:"+ dists.size());

		writeLocationData(allLocs);
		writeDistanceData(dists);
	}
	
	private static void writeLocationData(List<Location> allLocs) throws IOException {
		//Instantiating the CSVWriter class
				CSVWriter writerAllLocs = new CSVWriter(new FileWriter("C:\\Users\\pandi\\Documents\\repository\\UpGradDS\\MS\\RL\\WorkingModels\\allLocs.csv"));
				//Writing data to a csv file
				String line1[] = {"id", "lat", "lon"};
				//Instantiating the List Object
				List list = Collections.synchronizedList(new ArrayList<Integer>());
				list.add(line1);
				allLocs.parallelStream().forEach(lc -> {
					String[] data = {lc.getId(), String.valueOf(lc.getLat()),String.valueOf(lc.getLon())};
					list.add(data);
				});
				//Writing data to the csv file
				writerAllLocs.writeAll(list);
				writerAllLocs.flush();
				System.out.println("Location Data written");
	}
	private static void writeDistanceData(Set<Distance> distances) throws IOException {
		CSVWriter writerAllLocs = new CSVWriter(new FileWriter("C:\\Users\\pandi\\Documents\\repository\\UpGradDS\\MS\\RL\\WorkingModels\\distances.csv"));
		//Writing data to a csv file
		String line1[] = {"srcId", "dstId", "distance"};
		//Instantiating the List Object
		List list = Collections.synchronizedList(new ArrayList<Integer>());
		list.add(line1);
		distances.parallelStream().forEach(dst -> {
			String[] data = {dst.getSrcId(), dst.getDstId(),String.valueOf(dst.getDistance())};
			list.add(data);
		});
		//Writing data to the csv file
		writerAllLocs.writeAll(list);
		writerAllLocs.flush();
		System.out.println("Distance Data written");
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

	public static GHResponse routing(GraphHopper hopper,double srcLat,double srcLon,double dstLat, double dstLon) {
		// simple configuration of the request object
		GHRequest req = new GHRequest(srcLat, srcLon, dstLat, dstLon).
				// note that we have to specify which profile we are using even when there is only one like here
				setProfile("car").
				// define the language for the turn instructions
				setLocale(Locale.US);
		GHResponse rsp = hopper.route(req);

		return rsp;
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
	
	private static void addDCdata(List<Location> allLocs) throws NumberFormatException, IOException {
		String file_name = "C:\\Users\\pandi\\Documents\\repository\\UpGradDS\\MS\\RL\\WorkingModels\\dallas_amazon_warehouse_all.csv";  
		String line = "";  
		String splitBy = ",";  
		BufferedReader br = new BufferedReader(new FileReader(file_name));
		long counter = 0;
		while ((line = br.readLine()) != null && counter <10)   //returns a Boolean value  
		{
			String[] data = line.split(splitBy);
			if(!data[0].equals("id")) {
				Location dcLoc = new Location(data[0],Double.valueOf(data[1]),Double.valueOf(data[2]));
				allLocs.add(dcLoc);
			}
		}
	}
}
