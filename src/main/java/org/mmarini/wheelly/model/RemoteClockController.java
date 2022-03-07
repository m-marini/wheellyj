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
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static io.reactivex.rxjava3.core.Flowable.*;
import static java.time.Instant.now;
import static java.util.Objects.requireNonNull;

/**
 * The remote clock controller generate a flow of remote clock
 * by sending clock sync messages and measuring the remote clock offset.
 * The flow starts to emit remote clocks after <pre>start</pre> method invocation
 */
public class RemoteClockController {
    private static final Logger logger = LoggerFactory.getLogger(RemoteClockController.class);

    /**
     * Returns the remote clock controller
     *
     * @param socket          the socket
     * @param numClockSamples the number of clock sync samples to average
     * @param clockInterval   the remote clock interval
     * @param clockTimeout    the clock timeout when reading response
     */
    public static RemoteClockController create(AsyncSocket socket, int numClockSamples, long clockInterval, long clockTimeout) {
        return new RemoteClockController(socket, numClockSamples, clockInterval, clockTimeout);
    }

    private final AsyncSocket socket;
    private final PublishProcessor<Throwable> errors;
    private final int numClockSamples;
    private final long clockInterval;
    private final long clockTimeout;
    private final BehaviorProcessor<RemoteClock> remoteClocks;

    /**
     * Creates the remote clock controller
     *
     * @param socket          the socket
     * @param numClockSamples the number of clock sync samples to average
     * @param clockInterval   the remote clock interval
     * @param clockTimeout    the clock timeout when reading response
     */
    protected RemoteClockController(AsyncSocket socket, int numClockSamples, long clockInterval, long clockTimeout) {
        this.socket = requireNonNull(socket);
        this.numClockSamples = numClockSamples;
        this.clockInterval = clockInterval;
        this.clockTimeout = clockTimeout;
        remoteClocks = BehaviorProcessor.create();
        this.errors = PublishProcessor.create();
    }

    /**
     * Closes the controller
     */
    public RemoteClockController close() {
        remoteClocks.onComplete();
        return this;
    }

    /**
     * Returns the flow of a single averaged remote clock
     */
    private Flowable<RemoteClock> readAveragedClock() {
        return Flowable.range(0, numClockSamples)
                .doOnNext(i -> logger.debug("Clock sync measure {}", i))
                .concatMap(i -> readClockSync())
                .map(ClockSyncEvent::getRemoteOffset)
                .reduce(Long::sum)
                .map(t -> RemoteClock.create((t + numClockSamples / 2) / numClockSamples))
                .toFlowable();
    }

    /**
     * Returns the clock sync message from robot for a given timestamp
     *
     * @param originatedTimestamp the originated timestamp
     */
    Flowable<Timed<String>> readClock(long originatedTimestamp) {
        String pattern = "ck " + originatedTimestamp + " ";
        return socket.readLines().filter(line -> line.value().startsWith(pattern))
                .timeout(this.clockTimeout, TimeUnit.MILLISECONDS)
                .firstElement()
                .toFlowable();
    }

    /**
     * Returns the clock sync event generating a clock sync command
     */
    private Flowable<ClockSyncEvent> readClockSync() {
        return just(1)
                .concatMap(n -> {
                    long originateTimestamp = now().toEpochMilli();
                    logger.debug("sending clock sync with timestamp {}", originateTimestamp);
                    Flowable<Timed<String>> clockMsg = readClock(originateTimestamp);
                    writeClock(originateTimestamp);
                    return clockMsg;
                }).map(data ->
                        ClockSyncEvent.from(data.value(), data.time(TimeUnit.MILLISECONDS)));
    }

    /**
     *
     */
    public Flowable<Throwable> readErrors() {
        return errors;
    }

    /**
     *
     */
    public Flowable<RemoteClock> readRemoteClocks() {
        return remoteClocks;
    }

    /**
     * Start the flow
     */
    public RemoteClockController start() {
        // Wait for connection and start
        socket.readConnection().filter(x -> x)
                .firstElement()
                .toFlowable()
                .concatMap(x -> interval(0, clockInterval, TimeUnit.MILLISECONDS))
                .concatMap(x -> readAveragedClock())
                .doOnNext(remoteClocks::onNext)
                .doOnError(errors::onNext)
                .onErrorResumeWith(empty())
                .subscribe();
        return this;
    }

    /**
     * Sends the clock synchronization command
     *
     * @param originatedTimestamp the originated timestamp (ms)
     */
    private RemoteClockController writeClock(long originatedTimestamp) {
        socket.println(just("ck " + originatedTimestamp));
        return this;
    }
}
