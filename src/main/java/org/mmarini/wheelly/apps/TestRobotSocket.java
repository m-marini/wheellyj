package org.mmarini.wheelly.apps;

import io.reactivex.Flowable;
import org.mmarini.wheelly.apis.LineSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class TestRobotSocket {
    private static final long CMD_INTERVAL = 800;
    private static final Logger logger = LoggerFactory.getLogger(TestRobotSocket.class);
    public static final String HOST = "192.168.1.43";
    public static final int PORT = 22;

    public static void main(String[] args) throws IOException {
        try (LineSocket robot = new LineSocket(HOST, PORT, 10000, 3000)) {
            logger.info("Connecting to robot...");
            robot.connect();

            robot.readLines()
                    .doOnNext(line -> logger.atInfo().log(line.value()))
                    .subscribe();

            Flowable.timer(CMD_INTERVAL, TimeUnit.MILLISECONDS)
                    .take(3)
                    .doOnNext(ignored -> {
                        logger.info("Move command");
                        robot.writeCommand("mv 0 1");
                    })
                    .blockingSubscribe();
        }
    }
}
