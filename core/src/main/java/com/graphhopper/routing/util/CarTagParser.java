/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Defines bit layout for cars. (speed, access, ferries, ...)
 *
 * @author Peter Karich
 * @author Nop
 */
public class CarTagParser extends VehicleTagParser {
    protected final Map<String, Integer> trackTypeSpeedMap = new HashMap<>();
    protected final Set<String> badSurfaceSpeedMap = new HashSet<>();
    // This value determines the maximal possible on roads with bad surfaces
    protected int badSurfaceSpeed;

    /**
     * A map which associates string to speed. Get some impression:
     * http://www.itoworld.com/map/124#fullscreen
     * http://wiki.openstreetmap.org/wiki/OSM_tags_for_routing/Maxspeed
     */
    protected final Map<String, Integer> defaultSpeedMap = new HashMap<>();

    public CarTagParser() {
        this(new PMap());
    }

    public CarTagParser(int speedBits, double speedFactor, int maxTurnCosts) {
        this("car", speedBits, speedFactor, maxTurnCosts);
    }

    public CarTagParser(String name, int speedBits, double speedFactor, int maxTurnCosts) {
        this(name, speedBits, speedFactor, maxTurnCosts, false);
    }

    public CarTagParser(int speedBits, double speedFactor, int maxTurnCosts, boolean speedTwoDirections) {
        this("car", speedBits, speedFactor, maxTurnCosts, speedTwoDirections);
    }

    public CarTagParser(String name, int speedBits, double speedFactor, int maxTurnCosts, boolean speedTwoDirections) {
        this(new PMap().putObject("name", name).putObject("speed_bits", speedBits).putObject("speed_factor", speedFactor).
                putObject("max_turn_costs", maxTurnCosts).putObject("speed_two_directions", speedTwoDirections));
    }

    public CarTagParser(PMap properties) {
        super(properties.getString("name", "car"),
                properties.getInt("speed_bits", 5),
                properties.getDouble("speed_factor", 5),
                properties.getBool("speed_two_directions", false),
                properties.getInt("max_turn_costs", properties.getBool("turn_costs", false) ? 1 : 0));

        restrictedValues.add("agricultural");
        restrictedValues.add("forestry");
        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("delivery");
        restrictedValues.add("military");
        restrictedValues.add("emergency");
        restrictedValues.add("private");

        blockPrivate(properties.getBool("block_private", true));
        blockFords(properties.getBool("block_fords", false));

        intendedValues.add("yes");
        intendedValues.add("designated");
        intendedValues.add("permissive");

        barriers.add("kissing_gate");
        barriers.add("fence");
        barriers.add("bollard");
        barriers.add("stile");
        barriers.add("turnstile");
        barriers.add("cycle_barrier");
        barriers.add("motorcycle_barrier");
        barriers.add("block");
        barriers.add("bus_trap");
        barriers.add("sump_buster");

        badSurfaceSpeedMap.add("cobblestone");
        badSurfaceSpeedMap.add("grass_paver");
        badSurfaceSpeedMap.add("gravel");
        badSurfaceSpeedMap.add("sand");
        badSurfaceSpeedMap.add("paving_stones");
        badSurfaceSpeedMap.add("dirt");
        badSurfaceSpeedMap.add("ground");
        badSurfaceSpeedMap.add("grass");
        badSurfaceSpeedMap.add("unpaved");
        badSurfaceSpeedMap.add("compacted");

        // autobahn
        defaultSpeedMap.put("motorway", 100);
        defaultSpeedMap.put("motorway_link", 70);
        defaultSpeedMap.put("motorroad", 90);
        // bundesstraße
        defaultSpeedMap.put("trunk", 70);
        defaultSpeedMap.put("trunk_link", 65);
        // linking bigger town
        defaultSpeedMap.put("primary", 65);
        defaultSpeedMap.put("primary_link", 60);
        // linking towns + villages
        defaultSpeedMap.put("secondary", 60);
        defaultSpeedMap.put("secondary_link", 50);
        // streets without middle line separation
        defaultSpeedMap.put("tertiary", 50);
        defaultSpeedMap.put("tertiary_link", 40);
        defaultSpeedMap.put("unclassified", 30);
        defaultSpeedMap.put("residential", 30);
        // spielstraße
        defaultSpeedMap.put("living_street", 5);
        defaultSpeedMap.put("service", 20);
        // unknown road
        defaultSpeedMap.put("road", 20);
        // forestry stuff
        defaultSpeedMap.put("track", 15);

        trackTypeSpeedMap.put("grade1", 20); // paved
        trackTypeSpeedMap.put("grade2", 15); // now unpaved - gravel mixed with ...
        trackTypeSpeedMap.put("grade3", 10); // ... hard and soft materials
        trackTypeSpeedMap.put(null, defaultSpeedMap.get("track"));

        // limit speed on bad surfaces to 30 km/h
        badSurfaceSpeed = 30;
        maxPossibleSpeed = avgSpeedEnc.getNextStorableValue(properties.getDouble("max_speed", 140));
    }

    @Override
    public TransportationMode getTransportationMode() {
        return TransportationMode.CAR;
    }

    protected double getSpeed(ReaderWay way) {
        String highwayValue = way.getTag("highway");
        if (!Helper.isEmpty(highwayValue) && way.hasTag("motorroad", "yes")
                && !"motorway".equals(highwayValue) && !"motorway_link".equals(highwayValue)) {
            highwayValue = "motorroad";
        }
        Integer speed = defaultSpeedMap.get(highwayValue);
        if (speed == null)
            throw new IllegalStateException(getName() + ", no speed found for: " + highwayValue + ", tags: " + way);

        if (highwayValue.equals("track")) {
            String tt = way.getTag("tracktype");
            if (!Helper.isEmpty(tt)) {
                Integer tInt = trackTypeSpeedMap.get(tt);
                if (tInt != null)
                    speed = tInt;
            }
        }

        return speed;
    }

    @Override
    public EncodingManager.Access getAccess(ReaderWay way) {
        // TODO: Ferries have conditionals, like opening hours or are closed during some time in the year
        String highwayValue = way.getTag("highway");
        String firstValue = way.getFirstPriorityTag(restrictions);
        if (highwayValue == null) {
            if (way.hasTag("route", ferries)) {
                if (restrictedValues.contains(firstValue))
                    return EncodingManager.Access.CAN_SKIP;
                if (intendedValues.contains(firstValue) ||
                        // implied default is allowed only if foot and bicycle is not specified:
                        firstValue.isEmpty() && !way.hasTag("foot") && !way.hasTag("bicycle"))
                    return EncodingManager.Access.FERRY;
            }
            return EncodingManager.Access.CAN_SKIP;
        }
        
        if ("service".equals(highwayValue) && "emergency_access".equals(way.getTag("service"))) {
            return EncodingManager.Access.CAN_SKIP;
        }

        if ("track".equals(highwayValue) && trackTypeSpeedMap.get(way.getTag("tracktype")) == null)
            return EncodingManager.Access.CAN_SKIP;

        if (!defaultSpeedMap.containsKey(highwayValue))
            return EncodingManager.Access.CAN_SKIP;

        if (way.hasTag("impassable", "yes") || way.hasTag("status", "impassable"))
            return EncodingManager.Access.CAN_SKIP;

        // multiple restrictions needs special handling compared to foot and bike, see also motorcycle
        if (!firstValue.isEmpty()) {
            if (restrictedValues.contains(firstValue) && !getConditionalTagInspector().isRestrictedWayConditionallyPermitted(way))
                return EncodingManager.Access.CAN_SKIP;
            if (intendedValues.contains(firstValue))
                return EncodingManager.Access.WAY;
        }

        // do not drive street cars into fords
        if (isBlockFords() && ("ford".equals(highwayValue) || way.hasTag("ford")))
            return EncodingManager.Access.CAN_SKIP;

        if (getConditionalTagInspector().isPermittedWayConditionallyRestricted(way))
            return EncodingManager.Access.CAN_SKIP;
        else
            return EncodingManager.Access.WAY;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way) {
        EncodingManager.Access access = getAccess(way);
        if (access.canSkip())
            return edgeFlags;

        if (!access.isFerry()) {
            // get assumed speed from highway type
            double speed = getSpeed(way);
            speed = applyMaxSpeed(way, speed);

            speed = applyBadSurfaceSpeed(way, speed);

            setSpeed(false, edgeFlags, speed);
            if (avgSpeedEnc.isStoreTwoDirections())
                setSpeed(true, edgeFlags, speed);

            boolean isRoundabout = roundaboutEnc.getBool(false, edgeFlags);
            if (isOneway(way) || isRoundabout) {
                if (isForwardOneway(way))
                    accessEnc.setBool(false, edgeFlags, true);
                if (isBackwardOneway(way))
                    accessEnc.setBool(true, edgeFlags, true);
            } else {
                accessEnc.setBool(false, edgeFlags, true);
                accessEnc.setBool(true, edgeFlags, true);
            }

        } else {
            double ferrySpeed = ferrySpeedCalc.getSpeed(way);
            accessEnc.setBool(false, edgeFlags, true);
            accessEnc.setBool(true, edgeFlags, true);
            setSpeed(false, edgeFlags, ferrySpeed);
            if (avgSpeedEnc.isStoreTwoDirections())
                setSpeed(true, edgeFlags, ferrySpeed);
        }

        return edgeFlags;
    }

    /**
     * @param way   needed to retrieve tags
     * @param speed speed guessed e.g. from the road type or other tags
     * @return The assumed speed.
     */
    protected double applyMaxSpeed(ReaderWay way, double speed) {
        double maxSpeed = getMaxSpeed(way);
        // We obey speed limits
        if (isValidSpeed(maxSpeed)) {
            // We assume that the average speed is 90% of the allowed maximum
            return maxSpeed * 0.9;
        }
        return speed;
    }

    /**
     * make sure that isOneway is called before
     */
    protected boolean isBackwardOneway(ReaderWay way) {
        return way.hasTag("oneway", "-1")
                || way.hasTag("vehicle:forward", restrictedValues)
                || way.hasTag("motor_vehicle:forward", restrictedValues);
    }

    /**
     * make sure that isOneway is called before
     */
    protected boolean isForwardOneway(ReaderWay way) {
        return !way.hasTag("oneway", "-1")
                && !way.hasTag("vehicle:forward", restrictedValues)
                && !way.hasTag("motor_vehicle:forward", restrictedValues);
    }

    protected boolean isOneway(ReaderWay way) {
        return way.hasTag("oneway", oneways)
                || way.hasTag("vehicle:backward", restrictedValues)
                || way.hasTag("vehicle:forward", restrictedValues)
                || way.hasTag("motor_vehicle:backward", restrictedValues)
                || way.hasTag("motor_vehicle:forward", restrictedValues);
    }

    /**
     * @param way   needed to retrieve tags
     * @param speed speed guessed e.g. from the road type or other tags
     * @return The assumed speed
     */
    protected double applyBadSurfaceSpeed(ReaderWay way, double speed) {
        // limit speed if bad surface
        if (badSurfaceSpeed > 0 && isValidSpeed(speed) && speed > badSurfaceSpeed && way.hasTag("surface", badSurfaceSpeedMap))
            speed = badSurfaceSpeed;
        return speed;
    }
}
