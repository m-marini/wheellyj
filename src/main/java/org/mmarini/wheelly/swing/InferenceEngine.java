package org.mmarini.wheelly.swing;

import org.mmarini.Tuple2;
import org.mmarini.wheelly.model.MotorCommand;
import org.mmarini.wheelly.model.ScannerMap;
import org.mmarini.wheelly.model.WheellyStatus;

/**
 * The inference engine processes the robot sensors and produces the command to the actuators.
 */
public interface InferenceEngine {
    /**
     * Returns the tuple with motor command and scanner direction
     *
     * @param data the data of robot
     */
    Tuple2<MotorCommand, Integer> process(Tuple2<WheellyStatus, ScannerMap> data);
}
