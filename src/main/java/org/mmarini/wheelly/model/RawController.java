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
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static io.reactivex.rxjava3.core.Flowable.interval;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 *
 */
public class RawController implements RobotController {

    private static final Logger logger = LoggerFactory.getLogger(RawController.class);

    /**
     * Returns the raw controller
     *
     * @param host                    the host name
     * @param port                    the port
     * @param numClockSamples         the number of samples to measure synchronized remote clock
     * @param retryConnectionInterval the retry connection interval (ms)
     * @param readTimeout             the read timeout
     * @param clockInterval           the interval between clock synchronization (ms)
     * @param clockTimeout            the clock response timeout (ms)
     * @param restartClockSyncDelay   the delay to send clock sync after restart
     * @param statusInterval          the queries' interval (ms)
     * @param startQueryDelay         the delay to send a start query delay
     */
    public static RawController create(String host, int port, int numClockSamples,
                                       long connectionTimeout, long retryConnectionInterval, long readTimeout,
                                       long clockInterval, long clockTimeout, long restartClockSyncDelay,
                                       long statusInterval, long startQueryDelay) {
        return new RawController(host, port, numClockSamples,
                connectionTimeout, retryConnectionInterval, readTimeout,
                clockInterval, clockTimeout, restartClockSyncDelay,
                statusInterval, startQueryDelay);
    }

    /**
     * Returns the raw controller
     *
     * @param configParams the config parameters
     */
    public static RobotController create(ConfigParameters configParams) {
        return new RawController(configParams.host, configParams.port, configParams.numClockSample,
                configParams.connectionTimeout, configParams.retryConnectionInterval, configParams.readTimeout,
                configParams.clockInterval, configParams.clockTimeout, configParams.restartClockSyncDelay,
                configParams.statusInterval, configParams.startQueryDelay);
    }

    private final PublishProcessor<Timed<WheellyStatus>> states;
    private final PublishProcessor<Timed<Integer>> cps;
    private final PublishProcessor<Timed<ProxySample>> proxy;
    private final PublishProcessor<Throwable> localErrors;
    private final RemoteClockController clockController;
    private final ReliableSocket socket;
    private final long startQueryDelay;

    /**
     * Creates a raw robot controller
     *
     * @param host                    the host name
     * @param port                    the port
     * @param numClockSamples         the number of samples to measure synchronized remote clock
     * @param connectionTimeout       the connection timeout
     * @param retryConnectionInterval the retry connection interval (ms)
     * @param readTimeout             the read timeout (ms)
     * @param clockInterval           the interval between clock synchronization (ms)
     * @param clockTimeout            the clock response timeout (ms)
     * @param restartClockSyncDelay   the delay to send clock sync after restart
     * @param statusInterval          the status interval (ms)
     * @param startQueryDelay         the delay to start query command after reconnection
     */
    protected RawController(String host, int port, int numClockSamples, long connectionTimeout, long retryConnectionInterval, long readTimeout, long clockInterval, long clockTimeout, long restartClockSyncDelay, long statusInterval, long startQueryDelay) {
        this.startQueryDelay = startQueryDelay;
        requireNonNull(host);
        this.socket = ReliableSocket.create(host, port, connectionTimeout, retryConnectionInterval, readTimeout);
        this.proxy = PublishProcessor.create();
        this.states = PublishProcessor.create();
        this.cps = PublishProcessor.create();
        this.localErrors = PublishProcessor.create();
        this.clockController = RemoteClockController.create(socket, numClockSamples, clockTimeout, restartClockSyncDelay);


        // Transforms the received line into assets
        socket.readLines()
                .map(Timed::value)
                .filter(line -> line.startsWith("st "))
                .withLatestFrom(readRemoteClock(), Tuple2::of)
                .subscribe(t -> {
                            try {
                                Timed<WheellyStatus> status = WheellyStatus.from(t._1, t._2);
                                states.onNext(status);
                            } catch (Throwable ex) {
                                this.localErrors.onNext(ex);
                            }
                        },
                        states::onError,
                        states::onComplete);

        socket.readLines()
                .map(Timed::value)
                .filter(line -> line.startsWith("cs "))
                .withLatestFrom(readRemoteClock(), Tuple2::of)
                .subscribe(t -> {
                            try {
                                String line = t._1;
                                String[] params = line.split(" ");
                                if (params.length != 3) {
                                    throw new IllegalArgumentException(format("Wrong cps command \"%d\"", line));
                                }

                                long sampleInstant = t._2.fromRemote(Long.parseLong(params[1]));
                                int cps = parseInt(params[2]);

                                this.cps.onNext(new Timed<>(cps, sampleInstant, TimeUnit.MILLISECONDS));
                            } catch (Throwable ex) {
                                this.localErrors.onNext(ex);
                            }
                        },
                        cps::onError,
                        cps::onComplete);


        // Start the clock controller sync
        socket.firstConnection()
                .subscribe(x ->
                        interval(clockInterval, TimeUnit.MILLISECONDS)
                                .subscribe(y -> clockController.start())
                );

        // Debugging flows
        if (logger.isDebugEnabled()) {
            proxy.subscribe(s -> logger.debug("Debug: proxy {}", s));
            states.subscribe(s -> logger.debug("Debug: status {}", s));
            localErrors.subscribe(s -> logger.debug("Debug: localErrors {}", s.getMessage()));
        }
    }

    @Override
    public RobotController action(Flowable<? extends WheellyCommand> data) {
        return sendCommands(data.map(WheellyCommand::getString));
    }

    @Override
    public RawController close() {
        clockController.close();
        localErrors.onComplete();
        socket.close();
        return this;
    }

    public Maybe<Boolean> firstConnection() {
        return socket.firstConnection();
    }

    @Override
    public Flowable<Boolean> readConnection() {
        return socket.readConnection();
    }

    @Override
    public Flowable<Timed<Integer>> readCps() {
        return cps;
    }


    @Override
    public Flowable<Throwable> readErrors() {
        return socket.readErrors().mergeWith(localErrors).mergeWith(clockController.readErrors());
    }

    /**
     * Returns the flow of remote clocks
     */
    Flowable<RemoteClock> readRemoteClock() {
        return clockController.readRemoteClocks();
    }

    @Override
    public Flowable<Timed<WheellyStatus>> readStatus() {
        return states;
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
        socket.connect();
        return this;
    }
}