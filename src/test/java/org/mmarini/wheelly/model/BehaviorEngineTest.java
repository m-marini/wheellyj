/*
 *
 * Copyright (c) )2022 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.model;

import org.mmarini.Tuple2;
import org.mmarini.wheelly.swing.InferenceEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.TimeUnit;

class BehaviorEngineTest {
    public static final String HOST = "192.168.1.11";
    public static final int PORT = 22;
    public static final int NUM_SAMPLES = 10;
    public static final int CONNECTION_TIMEOUT = 3000;
    public static final int RETRY_CONNECTION_INTERVAL = 3000;
    public static final int CLOCK_INTERVAL = 30000;
    public static final int CLOCK_TIMEOUT = 1000;
    public static final int QUERIES_INTERVAL = 300;
    public static final int SCAN_COMMAND_INTERVAL = 700;
    public static final int MOTOR_COMMAND_INTERVAL = 300;
    public static final int START_QUERY_DELAY = 150;
    public static final int RESTART_CLOCK_SYNC_DELAY = 100;
    static final Logger logger = LoggerFactory.getLogger(BehaviorEngineTest.class);
    private static final long READ_TIMEOUT = 1000;

    public static void main(String[] args) throws InterruptedException {
        RawController controller = RawController.create(HOST, PORT,
                NUM_SAMPLES,
                CONNECTION_TIMEOUT,
                RETRY_CONNECTION_INTERVAL,
                READ_TIMEOUT, CLOCK_INTERVAL,
                CLOCK_TIMEOUT,
                RESTART_CLOCK_SYNC_DELAY, QUERIES_INTERVAL, START_QUERY_DELAY);
        Random random = new Random();
        MotorCommand motorCommand = MotorCommand.create(0, 0);

        InferenceEngine inferenceEngine = new InferenceEngine() {
            long timeout;
            int angle;

            @Override
            public Tuple2<MotorCommand, Integer> process(Tuple2<WheellyStatus, ScannerMap> data) {
                long now = System.currentTimeMillis();
                if (now > timeout) {
                    angle = random.nextInt(180) - 90;
                    timeout = now + 2000;
                }
                return Tuple2.of(motorCommand, angle);
            }
        };
        BehaviorEngine engine = BehaviorEngine.create(controller, inferenceEngine, MOTOR_COMMAND_INTERVAL, SCAN_COMMAND_INTERVAL);

        engine.readConnection().subscribe(
                data -> logger.debug("Connection {}", data),
                ex -> logger.error("Error connection", ex),
                () -> logger.debug("Connection closed"));

        engine.readErrors().subscribe(
                data -> logger.error("Errors", data),
                ex -> logger.error("Error on errors", ex),
                () -> logger.debug("Errors closed"));

        engine.readMapFlow().sample(1, TimeUnit.SECONDS).subscribe(
                data -> logger.debug("Map {}", data),
                ex -> logger.error("Error on map", ex),
                () -> logger.debug("Map closed"));

        engine.readStatus().sample(1, TimeUnit.SECONDS).subscribe(
                data -> logger.debug("Status {}", data),
                ex -> logger.error("Error on status", ex),
                () -> logger.debug("Status closed"));

        engine.readProxy().sample(1, TimeUnit.SECONDS).subscribe(
                data -> logger.debug("Proxy {}", data),
                ex -> logger.error("Error on Proxy", ex),
                () -> logger.debug("Proxy closed"));

        engine.start();

        Thread.sleep(60000);
        controller.close();
    }

}