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

import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import org.glassfish.jersey.client.rx.rxjava2.RxFlowableInvoker;
import org.glassfish.jersey.client.rx.rxjava2.RxFlowableInvokerProvider;
import org.glassfish.jersey.logging.LoggingFeature;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 *
 */
public class RxController {

    /**
     * @param baseUrl
     * @return
     */
    public static RxController create(String baseUrl) {
        return new RxController(baseUrl);
    }

    /**
     * @param body
     * @param clock
     */
    public static WheellyStatus toStatus(StatusBody body, RemoteClock clock) {
        return WheellyStatus.from(body.getStatus(), clock);
    }

    /**
     *
     */
    private static Client createClient() {
        return ClientBuilder.newClient()
                .register(RxFlowableInvokerProvider.class)
                .register(new LoggingFeature(java.util.logging.Logger.getLogger(RxController.class.getName()), Level.FINE, null, null));
    }

    private final String baseUrl;

    /**
     * @param baseUrl the base url
     */
    protected RxController(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     *
     */
    private WebTarget createWebTarget() {
        return createClient().target(baseUrl);
    }

    /**
     *
     */
    Flowable<ClockSyncEvent> clockSync() {
        long time = Instant.now().toEpochMilli();
        return createWebTarget().path("clock")
                .queryParam("ck", String.valueOf(time))
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .rx(RxFlowableInvoker.class)
                .get(ClockBody.class)
                .map(body -> {
                    long destinationTimestamp = Instant.now().toEpochMilli();
                    String data = body.getClock();
                    return ClockSyncEvent.from(data, destinationTimestamp);
                });
    }

    /**
     * @param noSamples number of samples
     */
    public Flowable<RemoteClock> remoteClock(int noSamples) {
        return Flowable.range(1, noSamples)
                .observeOn(Schedulers.io())
                .flatMap(i -> clockSync())
                .map(ClockSyncEvent::getRemoteOffset)
                .reduce(Long::sum)
                .map(x -> RemoteClock.create((x + noSamples / 2) / noSamples))
                .toFlowable();
    }

    /**
     * @param period    period
     * @param unit      unit
     * @param noSamples no samples
     */
    public Flowable<RemoteClock> remoteClock(long period, TimeUnit unit, int noSamples) {
        return Flowable.interval(0, period, unit)
                .flatMap(x -> remoteClock(noSamples));
    }

    /**
     * @param respFlowable the response flowable
     * @param clock        the clock
     */
    Flowable<WheellyStatus> manageStatusResponse(Flowable<Response> respFlowable, RemoteClock clock) {
        return respFlowable.map(
                resp -> {
                    if (resp.getStatus() != 200) {
                        throw new IllegalArgumentException("HTTP ERROR " + resp.getStatus());
                    }
                    StatusBody body = resp.readEntity(StatusBody.class);
                    return toStatus(body, clock);
                }
        );
    }

    /**
     * @param left    left speed
     * @param right   right speed
     * @param validTo expiration in remote clock
     */
    public Flowable<StatusBody> moveTo(int left, int right, long validTo) {
        MoveToBody reqBody = new MoveToBody(left, right, validTo);
        return createWebTarget().path("direction")
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .rx(RxFlowableInvoker.class)
                .post(Entity.json(reqBody))
                .map(resp -> resp.readEntity(StatusBody.class));
    }

    /**
     *
     */
    public Flowable<StatusBody> scan() {
        return createWebTarget().path("scan")
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .rx(RxFlowableInvoker.class)
                .post(Entity.json(null))
                .map(resp -> resp.readEntity(StatusBody.class));
    }

    /**
     *
     */
    public Flowable<StatusBody> status() {
        return createWebTarget().path("status")
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .rx(RxFlowableInvoker.class)
                .get(StatusBody.class);
    }
}