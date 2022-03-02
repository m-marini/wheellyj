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

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static io.reactivex.rxjava3.core.Flowable.*;
import static java.time.Instant.now;
import static java.util.Objects.requireNonNull;

/**
 *
 */
public class RawController implements RobotController {
    public static final int NUM_CLOCK_SAMPLES = 10;
    public static final long CLOCK_TIMEOUT = 30000; // millis
    private static final long REMOTE_CLOCK_PERIOD = 10; // Seconds
    private static final long STATUS_INTERVAL = 300; // Millis
    private static final Logger logger = LoggerFactory.getLogger(RawController.class);

    /**
     * @param host
     * @param port
     * @param numClockSamples
     * @param clockInterval
     * @param statusInterval
     * @throws IOException in case of error
     */
    public static RawController create(String host, int port, int numClockSamples, Duration clockInterval, Duration statusInterval) throws IOException {
        AsyncSocket socket = AsyncSocket.create(host, port);
        return new RawController(socket, numClockSamples, clockInterval, statusInterval);
    }

    /**
     * @param host
     * @param port
     * @throws IOException in case of error
     */
    public static RawController create(String host, int port) throws IOException {
        return create(host, port, NUM_CLOCK_SAMPLES,
                Duration.ofSeconds(REMOTE_CLOCK_PERIOD),
                Duration.ofMillis(STATUS_INTERVAL));
    }

    private final AsyncSocket socket;
    private final PublishProcessor<Throwable> errors;
    private final int numClockSamples;
    private final Duration clockInterval;
    private final Duration statusInterval;

    /**
     * @param socket
     * @param numClockSamples
     * @param clockInterval
     * @param statusInterval
     */
    protected RawController(AsyncSocket socket, int numClockSamples, Duration clockInterval, Duration statusInterval) {
        this.socket = requireNonNull(socket);
        this.numClockSamples = numClockSamples;
        this.clockInterval = clockInterval;
        this.statusInterval = statusInterval;
        this.errors = PublishProcessor.create();

        Flowable<String> queryStatusCmd = interval(0, statusInterval.toMillis(), TimeUnit.MILLISECONDS)
                .map(x -> "qs");
        Flowable<String> queryAssetCmd = interval(0, statusInterval.toMillis(), TimeUnit.MILLISECONDS)
                .map(x -> "qa");
        Flowable<String> cmd = Flowable.merge(queryStatusCmd, queryAssetCmd);
        socket.println(cmd).subscribe();
    }

    @Override
    public Completable activateMotors(Flowable<MotorCommand> data) {
        return socket.println(data.map(cmd ->
                "mt " + cmd.leftPower + " " + cmd.rightPower
        ));
    }

    @Override
    public Completable close() {
        return socket.close();
    }

    @Override
    public Flowable<Timed<RobotAsset>> readAsset() {
        return socket.readLines()
                .map(Timed::value)
                .filter(line -> line.startsWith("as "))
                .withLatestFrom(readRemoteClock(), Tuple2::of)
                .map(t -> RobotAsset.from(t._1, t._2));
    }

    /**
     *
     */
    Maybe<RemoteClock> readAveragedClock() {
        return Flowable.range(0, numClockSamples)
                .concatMap(originateTimestamp ->
                        readClockSync().toFlowable()
                )
                .map(ClockSyncEvent::getRemoteOffset)
                .reduce(Long::sum)
                .map(t -> RemoteClock.create((t + numClockSamples / 2) / numClockSamples));
    }

    /**
     * @param originateTimestamp
     */
    Single<Timed<String>> readClock(long originateTimestamp) {
        String pattern = "ck " + originateTimestamp + " ";
        return socket.readLines()
                .doOnNext(x -> logger.debug("readCLock: {}", x))
                .filter(line -> line.value().startsWith(pattern))
                .timeout(CLOCK_TIMEOUT, TimeUnit.MILLISECONDS)
                .firstElement()
                .doOnSuccess(x -> logger.debug("readCLock: {}", x))
                .toSingle();
    }

    /**
     *
     */
    Single<ClockSyncEvent> readClockSync() {
        long originateTimestamp = now().toEpochMilli();
        Single<Timed<String>> readClock = readClock(originateTimestamp);
        writeClock(originateTimestamp);
        return readClock.map(data ->
                ClockSyncEvent.from(data.value(), data.time(TimeUnit.MILLISECONDS)));
    }

    @Override
    public Flowable<Boolean> readConnection() {
        return Flowable.concat(just(true), never());
    }

    @Override
    public Flowable<Throwable> readErrors() {
        return never();
    }

    /**
     *
     */
    Flowable<RemoteClock> readRemoteClock() {
        return interval(0, clockInterval.toMillis(), TimeUnit.MILLISECONDS)
                .onBackpressureDrop()
                .concatMap(x -> readAveragedClock().toFlowable())
                .share();

    }

    @Override
    public Flowable<WheellyStatus> readStatus() {
        return socket.readLines()
                .map(Timed::value)
                .filter(line -> line.startsWith("st "))
                .withLatestFrom(readRemoteClock(), Tuple2::of)
                .map(t -> WheellyStatus.from(t._1, t._2));
    }

    @Override
    public Completable scan(Flowable<?> commands) {
        return socket.println(commands.map(x -> "sc"));
    }

    /**
     * @param originateTimestamp
     */
    Completable writeClock(long originateTimestamp) {
        return socket.println("ck " + originateTimestamp);
    }
}