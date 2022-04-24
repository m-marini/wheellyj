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

package org.mmarini.wheelly.apps;

import io.reactivex.rxjava3.schedulers.Timed;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import org.mmarini.wheelly.model.ReliableSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static io.reactivex.rxjava3.core.Flowable.just;

public class MotorFunction {
    public static final String MOTOR_DATA = "data/motorData.csv";
    public static final String HOST = "192.168.1.11";
    private static final long NO_SAMPLES = 100;
    private static final Logger logger = LoggerFactory.getLogger(MotorFunction.class);

    public static void main(String[] args) throws InterruptedException, IOException {
        logger.info("Started");
        new MotorFunction().start().blockingAwait();
        logger.info("Completed");
    }

    private final ReliableSocket socket;
    private final PrintWriter outFile;
    private final CompletableSubject completed;

    public MotorFunction() throws IOException {
        this.socket = ReliableSocket.create(HOST, 22, 3000, 3000, 3000);
        this.outFile = new PrintWriter(new FileWriter(MOTOR_DATA));
        this.completed = CompletableSubject.create();
        socket.readLines()
                .doOnError(ex -> logger.error("Error reading socket", ex))
                .map(Timed::value)
                .filter(line -> line.startsWith("sa "))
                .take(NO_SAMPLES)
                .doOnNext(this::handleData)
                .doOnComplete(() -> {
                    logger.info("Stopping ...");
                    socket.println(just("stop"))
                            .doOnComplete(() -> {
                                logger.info("Closing ...");
                                socket.close();
                                outFile.close();
                                completed.onComplete();
                            }).subscribe();
                })
                .subscribe();

    }

    private void handleData(String s) {
        String[] data = s.split(" ");
        String changed = Arrays.stream(data).skip(1).reduce((a, b) -> a + "," + b).orElse("");
        outFile.println(changed);
        logger.info(changed);
    }

    private CompletableSubject start() throws IOException {
        socket.connect();
        socket.println(just("start")
                .delay(1000, TimeUnit.MILLISECONDS)
                .doOnNext(x -> logger.info("Starting ...")));
        return completed;
    }
}
