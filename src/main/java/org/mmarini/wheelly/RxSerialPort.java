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

package org.mmarini.wheelly;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import jssc.SerialPort;
import jssc.SerialPortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RxSerialPort {
    private static final Logger logger = LoggerFactory.getLogger(RxSerialPort.class);

    private final String name;
    private final SerialPort port;
    private final PublishProcessor<RowEvent<byte[]>> dataEvents;

    protected RxSerialPort(String name) {
        this.name = name;
        this.port = new SerialPort(name);
        this.dataEvents = PublishProcessor.create();
    }

    private static String[] parseForLines(StringBuilder builder, byte[] buffer) {
        List<String> result = new ArrayList<>();
        for (byte b : buffer) {
            char ch = (char) b;
            switch (ch) {
                case '\r':
                    break;
                case '\n':
                    result.add(builder.toString());
                    builder.setLength(0);
                    break;
                default:
                    builder.append(ch);
            }
        }
        return result.toArray(String[]::new);
    }

    public static RxSerialPort create(String port) {
        return new RxSerialPort(port);
    }

    /**
     * @param cmd
     * @throws SerialPortException
     */
    public RxSerialPort write(String cmd) throws SerialPortException {
        logger.debug("--> {}", cmd);
        port.writeString(cmd);
        port.writeString("\n");
        return this;
    }

    /**
     *
     */
    public Flowable<RowEvent<byte[]>> getDataEvents() {
        return dataEvents;
    }

    /**
     *
     */
    public RxSerialPort disconnect() {
        logger.debug("Device {} disconnecting ...", name);
        try {
            port.removeEventListener();
            port.closePort();
            dataEvents.onComplete();
        } catch (SerialPortException e) {
            dataEvents.onError(e);
        }
        return this;
    }

    /**
     *
     */
    public Flowable<RowEvent<String>> getLines() {
        StringBuilder builder = new StringBuilder();
        return dataEvents
                .flatMap(event -> {
                    String[] data = parseForLines(builder, event.data);
                    return Flowable.fromArray(data).<RowEvent<String>>map(line ->
                            createEvent(event.time, line)
                    );
                });
    }

    public RxSerialPort connect() {
        try {
            port.openPort();
            port.setParams(SerialPort.BAUDRATE_115200,
                    SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);
            port.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN
                    | SerialPort.FLOWCONTROL_RTSCTS_OUT);
            port.addEventListener(event -> {
                if (event.isRXCHAR() && event.getEventValue() > 0) {
                    try {
                        dataEvents.onNext(
                                createEvent(System.nanoTime(),
                                        event.getPort().readBytes(event.getEventValue())));
                    } catch (SerialPortException e) {
                        dataEvents.onError(e);
                    }
                }
            }, SerialPort.MASK_RXCHAR);
        } catch (SerialPortException e) {
            dataEvents.onError(e);
        }
        return this;
    }

    public static <T> RowEvent createEvent(long time, T data) {
        return new RowEvent(time, data);
    }

    public static class RowEvent<T> {
        public final long time;
        public final T data;

        protected RowEvent(long time, T data) {
            this.time = time;
            this.data = data;
        }

        public long getTime() {
            return time;
        }

        public T getData() {
            return data;
        }

        @Override
        public String toString() {
            return String.valueOf(data);
        }
    }
}
