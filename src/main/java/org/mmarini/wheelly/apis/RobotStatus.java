/*
 * Copyright (c) 2023 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.apis;

import java.awt.geom.Point2D;
import java.util.Optional;
import java.util.StringJoiner;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.Utils.linear;
import static org.mmarini.wheelly.apis.Utils.normalizeDegAngle;

public class RobotStatus {
    public static final int PULSES_PER_ROOT = 40;
    public static final double WHEEL_DIAMETER = 0.067;
    public static final double DISTANCE_PER_PULSE = WHEEL_DIAMETER * PI / PULSES_PER_ROOT;
    public static final float DISTANCE_SCALE = 1F / 5882;
    public static final float OBSTACLE_SIZE = 0.2f;
    public static final int FRONT_LEFT = 8;
    public static final int FRONT_RIGHT = 4;
    public static final int REAR_LEFT = 2;
    public static final int REAR_RIGHT = 1;
    public static final int NO_CONTACT = 0;
    private static final RobotStatus DEFAULT_ROBOT_STATUS = new RobotStatus(WheellyStatus.create(), 0, null);
    private static final int[] CONTACT_THRESHOLDS = new int[]{205, 512, 677};
    private static final int MIN_SUPPLY_SIGNAL = 720;
    private static final int MAX_SUPPLY_SIGNAL = 823;
    private static final double MIN_SUPPLY_VOLTAGE = 10.9;
    private static final double MAX_SUPPLY_VOLTAGE = 12.1;

    /**
     * Returns the default robot status
     */
    public static RobotStatus create() {
        return DEFAULT_ROBOT_STATUS;
    }

    /**
     * Returns the contacts code from the contact sensors signals
     *
     * @param frontSignal the front signal
     * @param rearSignal  the rear signal
     */
    private static int decodeContacts(int frontSignal, int rearSignal) {
        return decodeContacts(frontSignal) * 4 + decodeContacts(rearSignal);
    }

    /**
     * Returns the contacts code from the contact sensors signals
     * <p>
     * Return value are:
     * <ul>
     *     <li>0 - no contact</li>
     *     <li>1 - right contact</li>
     *     <li>2 - left contact</li>
     *     <li>3 - both side contacts</li>
     * </ul>
     * </p>
     *
     * @param signal the signal
     */
    private static int decodeContacts(int signal) {
        for (int i = 0; i < CONTACT_THRESHOLDS.length; i++) {
            if (signal < CONTACT_THRESHOLDS[i]) {
                return i;
            }
        }
        return CONTACT_THRESHOLDS.length;
    }

    /**
     * Returns the supply voltage (V) from supply signal
     *
     * @param signal the signal
     */
    private static double decodeSupplyVoltage(int signal) {
        return linear(signal, MIN_SUPPLY_SIGNAL, MAX_SUPPLY_SIGNAL, MIN_SUPPLY_VOLTAGE, MAX_SUPPLY_VOLTAGE);
    }

    /**
     * Returns the signal for a contact
     *
     * @param contacts the contacts
     */
    private static int encodeContacts(int contacts) {
        if (contacts == 0) {
            return 0;
        }
        return CONTACT_THRESHOLDS[contacts - 1];
    }

    /**
     * Returns the location from pulse coordinate
     *
     * @param xPulses the x pulse coordinate
     * @param yPulses the y pulse coordinate
     */
    public static Point2D pulses2Location(double xPulses, double yPulses) {
        return new Point2D.Double(xPulses * DISTANCE_PER_PULSE, yPulses * DISTANCE_PER_PULSE);
    }

    private final WheellyStatus wheellyStatus;
    private final long resetTime;
    private final RadarMap radarMap;

    /**
     * Creates the robot status
     *
     * @param wheellyStatus the wheelly status
     * @param resetTime     the reset time (ms)
     * @param radarMap      the radar map
     */
    public RobotStatus(WheellyStatus wheellyStatus, long resetTime, RadarMap radarMap) {
        this.wheellyStatus = requireNonNull(wheellyStatus);
        this.resetTime = resetTime;
        this.radarMap = radarMap;
    }

    public boolean canMoveBackward() {
        return wheellyStatus.getCanMoveBackward();
    }

    public boolean canMoveForward() {
        return wheellyStatus.canMoveForward();
    }

    public int getContacts() {
        return decodeContacts(wheellyStatus.getFrontSensors(), wheellyStatus.getRearSensors());
    }

    /**
     * Returns the robot status by setting the contacts signals
     *
     * @param contacts the contacts code
     */
    public RobotStatus setContacts(int contacts) {
        return setWheellyStatus(wheellyStatus.setSensors(
                encodeContacts(contacts / 4),
                encodeContacts(contacts & 3)));
    }

    public int getDirection() {
        return wheellyStatus.getDirection();
    }

    public RobotStatus setDirection(int direction) {
        return setWheellyStatus(wheellyStatus.setDirection(direction));
    }

    public double getEchoDistance() {
        return wheellyStatus.getEchoTime() * DISTANCE_SCALE;
    }

    /**
     * Returns the robot status by setting the echo distance
     *
     * @param echoDistance the echo distance (m)
     */
    public RobotStatus setEchoDistance(double echoDistance) {
        return setWheellyStatus(wheellyStatus.setEchoTime(round(echoDistance / DISTANCE_SCALE)));
    }

    public long getElapsed() {
        return wheellyStatus.getTime() - resetTime;
    }

    public int getImuFailure() {
        return wheellyStatus.getImuFailure();
    }

    public RobotStatus setImuFailure(int imuFailure) {
        return setWheellyStatus(wheellyStatus.setImuFailure(imuFailure));
    }

    public double getLeftPps() {
        return wheellyStatus.getLeftPps();
    }

    public RobotStatus setLeftPps(double left) {
        return setWheellyStatus(wheellyStatus.setLeftPps(left));
    }

    public Point2D getLocation() {
        return pulses2Location(wheellyStatus.getXPulses(), wheellyStatus.getYPulses());
    }

    /**
     * Returns the robot status by setting the robot location
     *
     * @param location the robot location
     */
    public RobotStatus setLocation(Point2D location) {
        return setWheellyStatus(wheellyStatus.setPulses(
                location.getX() / DISTANCE_PER_PULSE,
                location.getY() / DISTANCE_PER_PULSE));
    }

    public RadarMap getRadarMap() {
        return radarMap;
    }

    public RobotStatus setRadarMap(RadarMap radarMap) {
        return new RobotStatus(wheellyStatus, resetTime, radarMap);
    }

    public double getRightPps() {
        return wheellyStatus.getRightPps();
    }

    public RobotStatus setRightPps(double rightPps) {
        return setWheellyStatus(wheellyStatus.setRightPps(rightPps));
    }

    public int getSensorDirection() {
        return wheellyStatus.getSensorDirection();
    }

    public RobotStatus setSensorDirection(int dir) {
        return setWheellyStatus(wheellyStatus.setSensorDirection(dir));
    }

    /**
     * Returns the obstacle location
     */
    public Optional<Point2D> getSensorObstacle() {
        double sampleDistance = getEchoDistance();
        if (sampleDistance > 0) {
            float d = (float) (sampleDistance + OBSTACLE_SIZE / 2);
            double angle = toRadians(90 - wheellyStatus.getDirection() - wheellyStatus.getSensorDirection());
            Point2D location = getLocation();
            float x = (float) (d * cos(angle) + location.getX());
            float y = (float) (d * sin(angle) + location.getY());
            return Optional.of(new Point2D.Float(x, y));
        } else {
            return Optional.empty();
        }
    }

    public double getSupplyVoltage() {
        return decodeSupplyVoltage(wheellyStatus.getSupplySensor());
    }

    public long getTime() {
        return wheellyStatus.getTime();
    }

    public RobotStatus setTime(long time) {
        return setWheellyStatus(wheellyStatus.setTime(time));
    }

    public WheellyStatus getWheellyStatus() {
        return wheellyStatus;
    }

    public RobotStatus setWheellyStatus(WheellyStatus wheellyStatus) {
        return new RobotStatus(wheellyStatus, resetTime, radarMap);
    }

    public boolean isHalt() {
        return wheellyStatus.isHalt();
    }

    public RobotStatus setHalt(boolean halt) {
        return setWheellyStatus(wheellyStatus.setHalt(halt));
    }

    public RobotStatus setCanMoveBackward(boolean canMoveBackward) {
        return setWheellyStatus(wheellyStatus.setCanMoveBackward(canMoveBackward));
    }

    public RobotStatus setCanMoveForward(boolean canMoveForward) {
        return setWheellyStatus(wheellyStatus.setCanMoveForward(canMoveForward));
    }

    public RobotStatus setResetTime(long resetTime) {
        return new RobotStatus(wheellyStatus, resetTime, radarMap);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", RobotStatus.class.getSimpleName() + "[", "]")
                .add("wheellyStatus=" + wheellyStatus)
                .add("resetTime=" + resetTime)
                .add("radarMap=" + radarMap)
                .toString();
    }

    /**
     * Returns the robot status with radar map updated
     *
     * @param radarReceptiveDistance the radar receptive distance (m)
     */
    public RobotStatus update(double radarReceptiveDistance) {
        if (radarMap != null) {
            // Updates the radar map
            double distance = getEchoDistance();
            Point2D location = getLocation();
            long time = wheellyStatus.getTime();
            RadarMap.SensorSignal signal = new RadarMap.SensorSignal(location,
                    normalizeDegAngle(wheellyStatus.getDirection() + wheellyStatus.getSensorDirection()),
                    (float) distance, time);
            RadarMap newRadarMap = radarMap.update(signal, radarReceptiveDistance);
            if (getContacts() != 0 || !wheellyStatus.getCanMoveBackward() || !wheellyStatus.canMoveForward()) {
                newRadarMap = newRadarMap.setContactsAt(location, radarReceptiveDistance, time);
            }
            return setRadarMap(newRadarMap);
        } else {
            return this;
        }
    }
}