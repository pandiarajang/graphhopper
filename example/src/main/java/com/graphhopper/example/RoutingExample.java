package com.graphhopper.example;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DirectedEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.weighting.custom.CustomProfile;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.*;
import com.graphhopper.util.exceptions.PointNotFoundException;
import com.graphhopper.util.shapes.GHPoint;

import java.util.Arrays;
import java.util.Locale;

import static com.graphhopper.json.Statement.If;
import static com.graphhopper.json.Statement.Op.LIMIT;
import static com.graphhopper.json.Statement.Op.MULTIPLY;

public class RoutingExample {
    public static void main(String[] args) {
        String relDir = args.length == 1 ? args[0] : "";
        relDir = "C:\\Users\\pandi\\Documents\\repository\\graphhopper\\";
        //Download texas-latest.osm.pbf before executing the program from graphhopper public library. 
        GraphHopper hopper = createGraphHopperInstance(relDir + "core\\files\\texas-latest.osm.pbf");
        routing(hopper); 
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
    
    public static void routing(GraphHopper hopper) {
        // simple configuration of the request object
    	
    	double[] src = new double[] {
    			33.224815511733425,-97.42255264701932
    	};
    	
    	double[] dst = new double[] {
    			33.24912437959046, -96.52214779947299
    	};
    	
    	
    	Snap snap = hopper.getLocationIndex().findClosest(src[0], src[1], EdgeFilter.ALL_EDGES);
    	
    	if(snap.isValid()) {
    		System.out.println("RoutingExample.routing()");
    	}
    	
    	snap.getClosestNode();
    	
    	double lat1 = hopper.getGraphHopperStorage().getNodeAccess().getLat(snap.getClosestNode());
    	double lon1 = hopper.getGraphHopperStorage().getNodeAccess().getLon(snap.getClosestNode());
    	Snap snap2 =new Snap(lat1, lon1);
    	
    	GHPoint srcPoint = hopper.getLocationIndex().findClosest(src[0], src[1], EdgeFilter.ALL_EDGES).getSnappedPoint();
    	GHPoint dstPoint = hopper.getLocationIndex().findClosest(dst[0], dst[1], EdgeFilter.ALL_EDGES).getSnappedPoint();
    	
    	Snap snap3 =new Snap(srcPoint.lat, srcPoint.lon);
    	Snap snap4 =new Snap(33.225371, -97.42242);
    	
    	//GHRequest req = new GHRequest(srcPoint.lat,srcPoint.lon,dstPoint.lat,dstPoint.lon).
    	GHRequest req = new GHRequest(src[0], src[1],dst[0], dst[1]).
                // note that we have to specify which profile we are using even when there is only one like here
                        setProfile("car").
                // define the language for the turn instructions
                        setLocale(Locale.US);                       ;
        GHResponse rsp = hopper.route(req);

        // handle errors
        if (rsp.hasErrors()) {
        	PointNotFoundException exc = (PointNotFoundException) rsp.getErrors().get(0);
        	int idx = (int) exc.getDetails().get("point_index");
        //	int idx = ((PointNotFoundException)rsp.getErrors().get(0)).
        	throw new RuntimeException(rsp.getErrors().toString());
        }
            

        // use the best path, see the GHResponse class for more possibilities.
        ResponsePath path = rsp.getBest();

        // points, distance in meters and time in millis of the full path
        PointList pointList = path.getPoints();
        double distance = path.getDistance();
        long timeInMs = path.getTime();
        System.out.println("RoutingExample.routing() + "+distance);
        Translation tr = hopper.getTranslationMap().getWithFallBack(Locale.UK);
        InstructionList il = path.getInstructions();
        // iterate over all turn instructions
        for (Instruction instruction : il) {
            // System.out.println("distance " + instruction.getDistance() + " for instruction: " + instruction.getTurnDescription(tr));
        }
        assert il.size() == 6;
        assert Helper.round(path.getDistance(), -2) == 900;
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
