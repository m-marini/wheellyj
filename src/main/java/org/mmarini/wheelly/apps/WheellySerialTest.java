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

package org.mmarini.wheelly.apps;

import jssc.SerialPort;
import jssc.SerialPortException;
import org.mmarini.wheelly.model.RxSerialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WheellySerialTest {
    private static final Logger logger = LoggerFactory.getLogger(WheellySerialTest.class);

    public static void main(String[] args) throws SerialPortException {
        logger.info("Wheely started.");
        RxSerialPort port = RxSerialPort.create("COM4", SerialPort.BAUDRATE_115200);
        port.getLines()
                .doOnError(ex -> logger.error(ex.getMessage(), ex))
                .doOnNext(line -> logger.debug("<--{}", line))
                .take(4)
                .doOnComplete(port::disconnect)
                .subscribe();

        port.getLines()
                .map(RxSerialPort.RowEvent::getData)
                .filter("ha"::equals)
                .firstElement()
                .doOnSuccess(x -> port.write("sc"))
                .subscribe();

        port.connect();


        port.getLines().blockingSubscribe();
        logger.info("Wheely completed.");
    }
}
