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
import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 */
public interface AsyncSocket {
    Logger logger = LoggerFactory.getLogger(AsyncSocket.class);

    /**
     * @param bfr the buffer
     */
    static Timed<String> buffer2String(Timed<ByteBuffer> bfr) {
        byte[] bytes = new byte[bfr.value().limit()];
        bfr.value().get(bytes);
        return new Timed<>(new String(bytes, StandardCharsets.UTF_8), bfr.time(), bfr.unit());
    }

    /**
     * @param host the host name
     * @param port the ip port
     * @throws IOException in case of error
     */
    static AsyncSocket create(String host, int port) throws IOException {
        return new AsyncSocketImpl(new InetSocketAddress(host, port));
    }

    /**
     * @param acc  the accumulator
     * @param data the data
     */
    static Tuple2<String, List<Timed<String>>> scanWith(
            Tuple2<String, List<Timed<String>>> acc,
            Timed<String> data) {
        String[] fragments = (acc._1 + data.value()).split("\\r\\n", -1);
        String tail = fragments[fragments.length - 1];
        List<Timed<String>> init = Arrays.stream(fragments).limit(fragments.length - 1)
                .map(line -> new Timed<>(line, data.time(), data.unit()))
                .collect(Collectors.toList());
        return Tuple2.of(tail, init);
    }

    /**
     * @param flow the flow
     */
    static Flowable<Timed<String>> toLines(Flowable<Timed<String>> flow) {
        return flow.scanWith(
                        () -> Tuple2.of("", List.of()),
                        AsyncSocket::scanWith)
                .concatMap(acc -> Flowable.fromIterable(acc._2));
    }

    /**
     *
     */
    Completable close();

    /**
     * @param dataFlow the data flow
     */
    Completable print(Flowable<String> dataFlow);

    /**
     * @param data the string
     */
    default Completable print(String data) {
        return print(Flowable.just(data));
    }

    /**
     * @param data the string
     */
    default Completable println(String data) {
        return println(Flowable.just(data));
    }

    /**
     * @param dataFlow the data flow
     */
    default Completable println(Flowable<String> dataFlow) {
        return print(dataFlow.map(x -> x + "\n"));
    }

    /**
     *
     */
    Flowable<Timed<String>> readLines();
}
