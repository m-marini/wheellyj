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
import org.glassfish.jersey.client.rx.rxjava2.RxFlowableInvoker;
import org.glassfish.jersey.client.rx.rxjava2.RxFlowableInvokerProvider;
import org.mmarini.Tuple2;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.time.Instant;

/**
 *
 */
public class RxController {

    /**
     * @param baseUrl the base URL
     */
    public static RxController create(String baseUrl) {
        return new RxController(baseUrl);
    }

    /**
     *
     */
    private static Client createClient() {
        return ClientBuilder.newClient()
                .register(RxFlowableInvokerProvider.class)
//                .register(new LoggingFeature(java.util.logging.Logger.getLogger(RxController.class.getName()), Level.INFO, null, null))
                ;
    }

    /**
     * @param tuple the tuple with status message and remote clock
     */
    public static WheellyStatus toStatus(Tuple2<StatusBody, RemoteClock> tuple) {
        return WheellyStatus.from(tuple._1.getStatus(), tuple._2);
    }

    private final String baseUrl;

    /**
     * @param baseUrl the base url
     */
    protected RxController(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Returns the clock message from wheelly
     */
    public Flowable<ClockBody> clock() {
        return createWebTarget().path("clock")
                .queryParam("ck", String.valueOf(Instant.now().toEpochMilli()))
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .rx(RxFlowableInvoker.class)
                .get(ClockBody.class);
    }

    /**
     * Creates the web target
     */
    private WebTarget createWebTarget() {
        return createClient().target(baseUrl);
    }

    /**
     * @param left    left speed
     * @param right   right speed
     * @param validTo expiration in remote clock
     */
    public Flowable<StatusBody> moveTo(int left, int right, long validTo) {
        MoveToBody reqBody = new MoveToBody(left, right, validTo);
        return createWebTarget().path("motors")
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