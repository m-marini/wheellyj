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

import static io.reactivex.rxjava3.core.Flowable.just;
import static java.time.Instant.now;
import static java.util.Objects.requireNonNull;

/**
 * The remote clock controller generate a flow of remote clock
 * by sending clock sync messages and measuring the remote clock offset.
 * The clock offset will be sent by invoking start method
 */
public class RemoteClockController {
    private static final Logger logger = LoggerFactory.getLogger(RemoteClockController.class);

    /**
     * Returns the remote clock controller
     *
     * @param socket          the socket
     * @param numClockSamples the number of clock sync samples to average
     * @param clockTimeout    the clock timeout when reading response
     * @param restartDelay    the restart delay to run a clock sync after reconnection
     */
    public static RemoteClockController create(AsyncSocket socket, int numClockSamples, long clockTimeout, long restartDelay) {
        return new RemoteClockController(socket, numClockSamples, clockTimeout, restartDelay);
    }

    private final AsyncSocket socket;
    private final int numClockSamples;
    private final long clockTimeout;
    private final BehaviorProcessor<RemoteClock> remoteClocks;
    private final PublishProcessor<Throwable> errors;
    private final PublishProcessor<Object> trigger;

    /**
     * Creates the remote clock controller
     *
     * @param socket          the socket
     * @param numClockSamples the number of clock sync samples to average
     * @param clockTimeout    the clock timeout when reading response
     * @param restartDelay    the restart delay to run a clock sync after reconnection
     */
    protected RemoteClockController(AsyncSocket socket, int numClockSamples, long clockTimeout, long restartDelay) {
        this.socket = requireNonNull(socket);
        this.numClockSamples = numClockSamples;
        this.clockTimeout = clockTimeout;
        remoteClocks = BehaviorProcessor.create();
        errors = PublishProcessor.create();
        this.trigger = PublishProcessor.create();
        socket.readConnection().filter(x -> x)
                .delay(restartDelay, TimeUnit.MILLISECONDS)
                .subscribe(trigger::onNext);
        this.trigger.subscribe(x ->
                        readAveragedClock().subscribe(
                                remoteClocks::onNext,
                                errors::onNext),
                ex -> logger.error("Unexpected error", ex),
                () -> {
                    remoteClocks.onComplete();
                    errors.onComplete();
                }
        );
    }

    /**
     * Closes the controller
     */
    public RemoteClockController close() {
        this.trigger.onComplete();
        return this;
    }

    /**
     * Returns the flow of a single averaged remote clock
     */
    private Flowable<RemoteClock> readAveragedClock() {
        return Flowable.range(0, numClockSamples)
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
        long originateTimestamp = now().toEpochMilli();
        logger.debug("sending clock sync with timestamp {}", originateTimestamp);
        Flowable<ClockSyncEvent> clockMsg = readClock(originateTimestamp)
                .map(data ->
                        ClockSyncEvent.from(data.value(), data.time(TimeUnit.MILLISECONDS)));
        writeClock(originateTimestamp);
        return clockMsg;
    }

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
        trigger.onNext(true);
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
