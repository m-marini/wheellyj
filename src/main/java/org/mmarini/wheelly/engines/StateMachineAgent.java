/*
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.wheelly.engines;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.wheelly.apis.RadarMap;
import org.mmarini.wheelly.apis.RobotApi;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.envs.PolarMap;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.mmarini.yaml.schema.Validator.*;

/**
 * State machine agent acts the robot basing on state machine flow.
 * <p>
 * The main method is <code>tick</code> which for the duration of the reaction time checks the I/O to the robot.<br>
 * <ul>
 *     <li>The <code>interval</code> parameter defines the interval (ms) between the read robot status.</li>
 *     <li>The <code>commandInterval</code> parameter defines the interval (ms) between sending output command to the robot.</li></7LU>
 *     <li>The <code>reactionInterval</code> parameter defines the interval (ms) between each state transition</li>
 * </ul>
 * </p>
 */
public class StateMachineAgent implements Closeable {
    public static final Validator AGENT_SPEC = objectPropertiesRequired(Map.of(
            "flow", StateFlow.STATE_FLOW_SPEC,
            "interval", positiveInteger(),
            "commandInterval", positiveInteger(),
            "reactionInterval", positiveInteger(),
            "numRadarSectors", integer(minimum(2)),
            "maxRadarDistance", positiveNumber()
    ), List.of(
            "flow", "interval", "commandInterval", "reactionInterval",
            "numRadarSectors", "maxRadarDistance"
    ));

    /**
     * Returns the agent from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of agent specification in the document
     * @param robot   the robot api
     */
    public static StateMachineAgent create(JsonNode root, Locator locator, RobotApi robot) {
        AGENT_SPEC.apply(locator).accept(root);
        StateFlow flow = StateFlow.create(root, locator.path("flow"));
        long interval = locator.path("interval").getNode(root).asLong();
        long commandInterval = locator.path("commandInterval").getNode(root).asLong();
        long reactionInterval = locator.path("reactionInterval").getNode(root).asLong();
        float maxRadarDistance = (float) locator.path("maxRadarDistance").getNode(root).asDouble();
        int numRadarSectors = locator.path("numRadarSectors").getNode(root).asInt();
        PolarMap polarMap = PolarMap.create(numRadarSectors);
        return new StateMachineAgent(interval, commandInterval, reactionInterval, maxRadarDistance, polarMap, robot, new ProcessorContext(flow));
    }

    private final RobotApi robot;
    private final ProcessorContext context;
    private final long interval;
    private final long commandInterval;
    private final long reactionInterval;
    private final float maxRadarDistance;
    private PolarMap polarMap;
    private long lastMoveTime;
    private long lastScanTime;
    private int lastScanDir;
    private boolean halted;
    private int lastDirection;
    private float lastSpeed;

    /**
     * Creates the agent
     *
     * @param interval         the time interval between status scan (ms)
     * @param commandInterval  the time interval between commands (ms)
     * @param reactionInterval the time interval between reaction (ms)
     * @param maxRadarDistance the max radar distance (m)
     * @param polarMap         the polar map
     * @param robot            the robot api
     * @param context          the processor context
     */
    public StateMachineAgent(long interval, long commandInterval, long reactionInterval, float maxRadarDistance, PolarMap polarMap, RobotApi robot, ProcessorContext context) {
        this.commandInterval = commandInterval;
        this.reactionInterval = reactionInterval;
        this.maxRadarDistance = maxRadarDistance;
        this.robot = requireNonNull(robot);
        this.context = context;
        this.interval = interval;
        this.polarMap = polarMap;
    }

    @Override
    public void close() throws IOException {

    }

    public float getMaxRadarDistance() {
        return this.maxRadarDistance;
    }

    /**
     * Returns the polar map
     */
    public PolarMap getPolarMap() {
        return polarMap;
    }

    /**
     * Returns the radar map
     */
    public RadarMap getRadarMap() {
        return robot.getStatus().getRadarMap();
    }

    /**
     * Initializes the agent.
     * <p>
     * Initializes the robot and state machine context from the entry state
     */
    public void init() {
        robot.start();
        robot.reset();
        readStatus(0L);
        context.setRobotStatus(robot.getStatus());
        context.init();
        lastMoveTime = lastScanTime = robot.getStatus().getTime();
        lastScanDir = context.getSensorDirection();
        halted = true;
        robot.halt();
        robot.scan(lastScanDir);
    }

    /**
     * Processes the command to the robot.
     * Sends the commands to the robot if command interval has elapsed since last command sent
     * or if there have been changes of action to the robot.
     */
    private void processCommand() {
        int scan = context.getSensorDirection();
        long time = robot.getStatus().getTime();
        if (this.lastScanDir != scan || time >= lastScanTime + commandInterval) {
            robot.scan(scan);
            lastScanDir = scan;
            this.lastScanTime = time;
        }
        if (!context.isHalt()) {
            int dir = context.getRobotDirection();
            float speed = context.getSpeed();
            if (halted || lastDirection != dir || lastSpeed != speed ||
                    time >= lastMoveTime + commandInterval) {
                robot.move(dir, speed);
                halted = false;
                lastDirection = dir;
                lastSpeed = speed;
                lastMoveTime = time;
            }
        } else if (!halted) {
            robot.halt();
            halted = true;
            lastMoveTime = time;
        }
    }

    /**
     * Returns the status read from robot
     * Reads the state of the robot for a given time interval, processing the sending of commands if necessary.
     *
     * @param time the time to read
     */
    private void readStatus(long time) {
        RobotStatus status = robot.getStatus();
        long timeout = status.getTime() + time;
        do {
            robot.tick(interval);
            status = robot.getStatus();
            processCommand();
        } while (!(status != null && status.getTime() >= timeout));
        context.setRobotStatus(status);
        //polarMap.update(status.getRadarMap(), status.getLocation(), status.getDirection(), maxRadarDistance);
        polarMap = polarMap.update(status.getRadarMap(), status.getLocation(), status.getDirection(), maxRadarDistance);
    }

    /**
     * Executes a processing step of the state machine.
     * Reads the state of the robot and processes the state machine generating the required behavior.
     */
    public void step() {
        readStatus(reactionInterval);
        context.setRobotStatus(robot.getStatus());
        context.setPolarMap(polarMap);
        context.step();
        processCommand();
    }
}
