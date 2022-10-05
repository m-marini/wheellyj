package org.mmarini.wheelly.apps;

import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.wheelly.apis.RobotSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TestRobotSocket {
    private static final long TEST_DURATION = 2000;
    private static final long CMD_INTERVAL = 800;
    private static final Logger logger = LoggerFactory.getLogger(TestRobotSocket.class);

    public static void main(String[] args) throws IOException {
        try (RobotSocket robot = new RobotSocket("192.168.1.11", 22, 10000, 3000)) {
            logger.info("Connecting to robot...");
            robot.connect();
            long test_timeout = System.currentTimeMillis() + TEST_DURATION;
            long cmd_timeout = 0;
            while (System.currentTimeMillis() < test_timeout) {
                long t = System.currentTimeMillis();
                if (t >= cmd_timeout) {
                    logger.info("Move command");
                    robot.writeCommand("mv 0 1");
                }
                cmd_timeout = t + CMD_INTERVAL;
                Timed<String> line = robot.readLine();
                if (line != null) {
                    logger.info("{}", line.value());
                }
            }
        }
    }
}
