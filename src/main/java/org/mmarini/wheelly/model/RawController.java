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

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static io.reactivex.rxjava3.core.Flowable.empty;
import static io.reactivex.rxjava3.core.Flowable.interval;
import static java.util.Objects.requireNonNull;

/**
 *
 */
public class RawController implements RobotController {
    public static final int NUM_CLOCK_SAMPLES = 10;
    public static final long RETRY_CONNECTION_INTERVAL = 3000; // millis
    public static final long CLOCK_TIMEOUT = 3000; // millis
    public static final long REMOTE_CLOCK_PERIOD = 300000; // millis
    private static final long START_QUERY_INTERVAL = 300000; // (millis)
    private static final long QUERIES_INTERVAL = 300;

    private static final Logger logger = LoggerFactory.getLogger(RawController.class);

    /**
     * Returns the raw controller
     *
     * @param host                    the host name
     * @param port                    the port
     * @param numClockSamples         the number of samples to measure synchronized remote clock
     * @param retryConnectionInterval the retry connection interval (ms)
     * @param clockInterval           the interval between clock synchronization (ms)
     * @param clockTimeout            the clock response timeout (ms)
     * @param startQueryInterval      the start query interval (ms)
     * @param queriesInterval         the queries' interval (ms)
     */
    public static RawController create(String host, int port, int numClockSamples,
                                       long retryConnectionInterval, long clockInterval,
                                       long clockTimeout, long startQueryInterval, long queriesInterval) {
        return new RawController(host, port, numClockSamples, retryConnectionInterval, clockInterval, clockTimeout, startQueryInterval, queriesInterval);
    }

    /**
     * @param host the host name
     * @param port the port
     */
    public static RawController create(String host, int port) {
        return create(host, port, NUM_CLOCK_SAMPLES,
                RETRY_CONNECTION_INTERVAL,
                REMOTE_CLOCK_PERIOD,
                CLOCK_TIMEOUT, START_QUERY_INTERVAL, QUERIES_INTERVAL);
    }

    private final PublishProcessor<WheellyStatus> states;
    private final PublishProcessor<Timed<ProxySample>> proxy;
    private final PublishProcessor<Throwable> localErrors;
    private final RemoteClockController clockController;
    private final ReliableSocket socket;
    private final long startQueryInterval;
    private final long queriesInterval;

    /**
     * Creates a raw robot controller
     *
     * @param host                    the host name
     * @param port                    the port
     * @param numClockSamples         the number of samples to measure synchronized remote clock
     * @param retryConnectionInterval the retry connection interval (ms)
     * @param clockInterval           the interval between clock synchronization (ms)
     * @param clockTimeout            the clock response timeout (ms)
     * @param startQueryInterval      the start query interval (ms)
     * @param queriesInterval         the queries interval (ms)
     */
    protected RawController(String host, int port, int numClockSamples, long retryConnectionInterval, long clockInterval, long clockTimeout, long startQueryInterval, long queriesInterval) {
        this.startQueryInterval = startQueryInterval;
        this.queriesInterval = queriesInterval;
        requireNonNull(host);
        this.socket = ReliableSocket.create(host, port, retryConnectionInterval);
        this.proxy = PublishProcessor.create();
        this.states = PublishProcessor.create();
        this.localErrors = PublishProcessor.create();
        this.clockController = RemoteClockController.create(socket, numClockSamples, clockInterval, clockTimeout);
    }

    @Override
    public RawController activateMotors(Flowable<MotorCommand> data) {
        return sendCommands(data.map(cmd -> "mt " + cmd.leftPower + " " + cmd.rightPower));
    }

    @Override
    public RawController close() {
        clockController.close();
        proxy.onComplete();
        states.onComplete();
        localErrors.onComplete();
        socket.close();
        return this;
    }

    @Override
    public Flowable<Boolean> readConnection() {
        return socket.readConnection();
    }


    @Override
    public Flowable<Throwable> readErrors() {
        return socket.readErrors().mergeWith(localErrors);
    }

    @Override
    public Flowable<Timed<ProxySample>> readProxy() {
        return this.proxy;
    }

    /**
     * Returns the flow of remote clocks
     */
    Flowable<RemoteClock> readRemoteClock() {
        return clockController.readRemoteClocks();
    }

    @Override
    public Flowable<WheellyStatus> readStatus() {
        return states;
    }

    @Override
    public RawController scan(Flowable<?> commands) {
        return sendCommands(commands.map(x -> "sc"));
    }

    /**
     * @param cmds the commands
     */
    private RawController sendCommands(Flowable<String> cmds) {
        socket.println(cmds);
        return this;
    }

    @Override
    public RawController start() {

        // Transforms the received line into assets
        socket.readLines()
                .map(Timed::value)
                .filter(line -> line.startsWith("pr "))
                .withLatestFrom(readRemoteClock(), Tuple2::of)
                .doOnNext(t -> logger.debug("Read asset {}", t._1))
                .map(t -> ProxySample.from(t._1, t._2))
                .onErrorResumeNext(ex -> {
                    this.localErrors.onNext(ex);
                    return empty();
                })
                .subscribe(proxy);

        // Transforms the received line into states
        socket.readLines()
                .map(Timed::value)
                .filter(line -> line.startsWith("st "))
                .withLatestFrom(readRemoteClock(), Tuple2::of)
                .doOnNext(t -> logger.debug("Read state {}", t._1))
                .map(t -> WheellyStatus.from(t._1, t._2))
                .onErrorResumeNext(ex -> {
                    this.localErrors.onNext(ex);
                    return empty();
                })
                .subscribe(states);

        // Flows of query command
        sendCommands(readRemoteClock()
                .firstElement()
                .toFlowable()
                .concatMap(n -> {
                    logger.debug("Ready to start query commands");
                    return interval(0, startQueryInterval, TimeUnit.MILLISECONDS)
                            .map(x -> "sq " + queriesInterval);
                }));

        // Debugging flows
        if (logger.isDebugEnabled()) {
            proxy.subscribe(s -> logger.debug("Debug: proxy {}", s));
            states.subscribe(s -> logger.debug("Debug: status {}", s));
            localErrors.subscribe(s -> logger.debug("Debug: localErrors {}", s.getMessage()));
        }
        // Start the clock controller
        clockController.start();
        socket.connect();
        return this;
    }
}