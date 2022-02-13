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

import jssc.SerialPort;
import jssc.SerialPortException;
import jssc.SerialPortList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SerialBypass {
    private static final Logger logger = LoggerFactory.getLogger(SerialBypass.class);

    public static void main(String[] args) throws SerialPortException {
        logger.info("Bypass started.");
        String[] portList = SerialPortList.getPortNames();
        if (args.length < 2) {
            logger.info("Missing arguments.");
            logger.info("Usage:");
            logger.info("SerialBypass <port1> <port2>");
            logger.info("Available Ports:");
            for (String port : portList) {
                logger.info("  {}", port);
            }
            System.exit(-1);
        }
        RxSerialPort port1 = RxSerialPort.create(args[0], SerialPort.BAUDRATE_115200);
        RxSerialPort port2 = RxSerialPort.create(args[1], SerialPort.BAUDRATE_115200);

        port1.getLines()
                .doOnError(ex -> logger.error(ex.getMessage(), ex))
                .doOnNext(line -> logger.info("{}-->{} {}",
                        port1.getName(),
                        port2.getName(),
                        line))
                .subscribe();

        port2.getLines()
                .doOnError(ex -> logger.error(ex.getMessage(), ex))
                .doOnNext(line -> logger.info("{}-->{} {}",
                        port2.getName(),
                        port1.getName(),
                        line))
                .subscribe();

        try {
            port1.connect();
            port2.connect();
        } catch (SerialPortException e) {
            logger.error(e.getMessage(), e);
            System.exit(-1);
        }

        port1.getDataEvents()
                .map(RxSerialPort.RowEvent::getData)
                .doOnNext(port2::write)
                .subscribe();

        port2.getDataEvents()
                .map(RxSerialPort.RowEvent::getData)
                .doOnNext(port1::write)
                .subscribe();


        port1.getDataEvents().blockingSubscribe();
        port2.getDataEvents().blockingSubscribe();
        logger.info("Bypass completed.");
    }
}
