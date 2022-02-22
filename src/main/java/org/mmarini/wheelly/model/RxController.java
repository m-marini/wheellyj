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
import io.reactivex.rxjava3.processors.AsyncProcessor;
import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reactive controller generates the API requests to Wheelly.
 * The requests are queued and processed one by one.
 */
public class RxController {
    private static final Logger logger = LoggerFactory.getLogger(RxController.class);
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();

    /**
     * Returns the controller
     *
     * @param baseUrl the base URL the base URL
     */
    public static RxController create(String baseUrl) {
        return new RxController(baseUrl);
    }

    /**
     * Returns a web client
     */
    private static Client createClient() {
        return ClientBuilder.newClient()
//                .register(new LoggingFeature(java.util.logging.Logger.getLogger(RxController.class.getName()), Level.INFO, null, null))
                ;
    }

    /**
     * Returns the Wheelly status from status body and remote clock
     *
     * @param tuple the tuple with status message and remote clock
     */
    public static WheellyStatus toStatus(Tuple2<StatusBody, RemoteClock> tuple) {
        return WheellyStatus.from(tuple._1.getStatus(), tuple._2);
    }

    private final String baseUrl;

    /**
     * Creates an RxController
     *
     * @param baseUrl the base url
     */
    protected RxController(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Returns the clock message from Wheelly
     */
    public Flowable<ClockBody> clock() {
        AsyncProcessor<ClockBody> processor = AsyncProcessor.create();
        Runnable task = () -> {
            try {
                logger.debug("Creating clock request ...");
                ClockBody body = createWebTarget().path("clock")
                        .queryParam("ck", String.valueOf(Instant.now().toEpochMilli()))
                        .request()
                        .accept(MediaType.APPLICATION_JSON)
                        .get(ClockBody.class);
                processor.onNext(body);
                processor.onComplete();
            } catch (Throwable ex) {
                processor.onError(ex);
            }
        };
        worker.submit(task);
        return processor;
    }

    /**
     * Returns the web target
     */
    private WebTarget createWebTarget() {
        return createClient().target(baseUrl);
    }

    /**
     * Returns the status message by invoking a move command
     *
     * @param left    left speed
     * @param right   right speed
     * @param validTo expiration in remote clock
     */
    public Flowable<StatusBody> moveTo(int left, int right, long validTo) {
        AsyncProcessor<StatusBody> result = AsyncProcessor.create();
        Runnable task = () -> {
            try {
                logger.debug("Creating move request {} {} ...", left, right);
                MoveToBody reqBody = new MoveToBody(left, right, validTo);
                StatusBody resBody = createWebTarget().path("motors")
                        .request()
                        .accept(MediaType.APPLICATION_JSON)
                        .post(Entity.json(reqBody))
                        .readEntity(StatusBody.class);
                result.onNext(resBody);
                result.onComplete();
            } catch (Throwable ex) {
                result.onError(ex);
            }
        };
        worker.submit(task);
        return result;
    }

    /**
     * Returns the status message by invoking a scan command
     */
    public Flowable<StatusBody> scan() {
        AsyncProcessor<StatusBody> result = AsyncProcessor.create();
        Runnable task = () -> {
            try {
                logger.debug("Creating scan request ...");
                StatusBody body = createWebTarget().path("scan")
                        .request()
                        .accept(MediaType.APPLICATION_JSON)
                        .post(Entity.json(null))
                        .readEntity(StatusBody.class);
                result.onNext(body);
                result.onComplete();
            } catch (Throwable ex) {
                result.onError(ex);
            }
        };
        worker.submit(task);
        return result;
    }

    /**
     * Returns the status message by invoking a query status command
     */
    public Flowable<StatusBody> status() {
        AsyncProcessor<StatusBody> result = AsyncProcessor.create();
        Runnable task = () -> {
            try {
                StatusBody body = createWebTarget().path("status")
                        .request()
                        .accept(MediaType.APPLICATION_JSON)
                        .get(StatusBody.class);
                result.onNext(body);
                result.onComplete();
            } catch (Throwable ex) {
                result.onError(ex);
            }
        };
        worker.submit(task);
        return result;
    }
}