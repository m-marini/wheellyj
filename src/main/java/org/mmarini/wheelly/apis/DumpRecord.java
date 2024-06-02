/*
 * Copyright (c) 2023 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.apis;

import io.reactivex.rxjava3.schedulers.Timed;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stores the dump record
 */
public interface DumpRecord {
    Pattern RECORD_PATTERN = Pattern.compile("^(\\d*) ([><]) (.*)$");

    /**
     * Returns the dump record from string
     *
     * @param line the string
     */
    static DumpRecord create(String line) {
        Matcher matcher = RECORD_PATTERN.matcher(line);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("wrong record format");
        }
        Instant timestamp = Instant.ofEpochMilli(Long.parseLong(matcher.group(1)));
        String dir = matcher.group(2);
        String data = matcher.group(3);
        if (">".equals(dir)) {
            return new WriteDumpRecord(timestamp, data);
        }
        return WheellyMessage.fromLine(new Timed<>(data, timestamp.toEpochMilli(), TimeUnit.MILLISECONDS), ClockConverter.identity())
                .map(msg -> (DumpRecord) new MessageDumpRecord<>(timestamp, data, msg))
                .orElseGet(() -> new ReadDumpRecord(timestamp, data));
    }

    /**
     * Returns the communication direction [RX, TX]
     */
    String comDirection();

    /**
     * Returns the line data of record
     */
    String data();

    /**
     * Returns the instant of record
     */
    Instant instant();

    /**
     * Stores the read dump record
     */
    interface ReadDumpRecordIntf extends DumpRecord {

        @Override
        default String comDirection() {
            return "RX";
        }
    }

    record MessageDumpRecord<T extends WheellyMessage>(Instant instant, String data,
                                                       T message) implements ReadDumpRecordIntf {
    }

    record ReadDumpRecord(Instant instant, String data) implements ReadDumpRecordIntf {
    }

    /**
     * Stores the written dump record
     */
    record WriteDumpRecord(Instant instant, String data) implements DumpRecord {

        @Override
        public String comDirection() {
            return "TX";
        }
    }
}
