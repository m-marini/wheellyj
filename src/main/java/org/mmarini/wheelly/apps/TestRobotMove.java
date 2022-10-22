package org.mmarini.wheelly.apps;

import org.mmarini.wheelly.apis.Robot;
import org.mmarini.wheelly.apis.WheellyStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Run a test to check for Robot moving.
 * The test run for 2 secs turning the Roboto to 0 DEG and turning the sensro to 90 DEG
 */
public class TestRobotMove {
    private static final long TEST_DURATION = 2000;
    private static final long CMD_INTERVAL = 800;
    private static final Logger logger = LoggerFactory.getLogger(TestRobotMove.class);

    /**
     * @param args command line arguments
     * @throws IOException in case of error
     */
    public static void main(String[] args) throws IOException {
        try (Robot robot = Robot.create("192.168.1.11", 22)) {
            logger.info("Connecting to robot...");
            robot.start();
            long dt = 100;
            robot.tick(dt);
            long test_timeout = System.currentTimeMillis() + TEST_DURATION;
            long cmd_timeout = 0;
            long prev = System.currentTimeMillis();
            while (System.currentTimeMillis() < test_timeout) {
                long t = System.currentTimeMillis();
                if (t >= cmd_timeout) {
                    logger.info("Move command");
                    robot.move(0, 0);
                    robot.scan(90);
                }
                cmd_timeout = t + CMD_INTERVAL;
                robot.tick(dt);
                WheellyStatus s = robot.getStatus();
                if (s != null) {
                    logger.info("Status: {}", s);
                }
            }
            robot.halt();
        }
    }
}
