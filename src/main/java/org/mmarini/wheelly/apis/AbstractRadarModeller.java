/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
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

/**
 * Creates and updates the radar maps
 */
public interface AbstractRadarModeller extends RadarModeller {

    @Override
    default RadarMap clean(RadarMap radarMap, long time) {
        if (time >= radarMap.cleanTimestamp() + cleanInterval()) {
            long echoLimit = time - echoPersistence();
            long contactLimit = time - contactPersistence();
            return radarMap.map(m ->
                            m.clean(echoLimit, contactLimit))
                    .setCleanTimestamp(time);
        } else {
            return radarMap;
        }
    }

    /**
     * Returns the clean interval (ms)
     */
    long cleanInterval();

    /**
     * Returns the contact persistence (ms)
     */
    long contactPersistence();

    /**
     * Returns the hasObstacle persistence (ms)
     */
    long echoPersistence();

    /**
     * Returns the radar map updated with the radar status.
     * It uses the localTime and signals from robot to update the status of radar map
     *
     * @param map    the radar map
     * @param status the robot status
     */
    default RadarMap update(RadarMap map, RobotStatus status) {
        // Updates the radar map
        RobotSpec robotSpec = status.robotSpec();
        long time = status.simulationTime();
        SensorSignal frontSignal = new SensorSignal(status.frontLidarLocation(),
                status.headAbsDirection(),
                status.frontDistance(), time
        );
        SensorSignal rearSignal = new SensorSignal(status.rearLidarLocation(),
                status.headAbsDirection().opposite(),
                status.rearDistance(), time
        );
        RadarMap hinderedMap = update(map, frontSignal, robotSpec);
        hinderedMap = update(hinderedMap, rearSignal, robotSpec);

        boolean frontContact = !status.frontSensor();
        boolean rearContact = !status.rearSensor();
        double contactRadius = robotSpec.contactRadius();
        RadarMap contactMap = frontContact || rearContact
                ? hinderedMap.setContactsAt(status.location(), status.direction(), frontContact, rearContact,
                contactRadius, time)
                : hinderedMap;
        return clean(contactMap, time);
    }

    /**
     * Updates the map with a sensor signal
     *
     * @param map       the radar map
     * @param signal    the sensor signal
     * @param robotSpec the robot specification
     */
    RadarMap update(RadarMap map, SensorSignal signal, RobotSpec robotSpec);
}