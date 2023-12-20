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
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Stores the dump record
 */
public abstract class DumpRecord {
    private static final Pattern RECORD_PATTERN = Pattern.compile("^(\\d*) ([><]) (.*)$");

    /**
     * Returns the dump record from string
     *
     * @param line the string
     */
    public static DumpRecord create(String line) {
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
        return WheellyMessage.fromLine(new Timed<>(data, timestamp.toEpochMilli(), TimeUnit.MILLISECONDS))
                .map(msg -> (DumpRecord) new MessageDumpRecord<>(timestamp, data, msg))
                .orElseGet(() -> new ReadDumpRecord(timestamp, data));
    }

    protected final Instant instant;
    protected final String data;

    /**
     * Creates the dump record
     *
     * @param instant the timestamp of record
     * @param data    the data
     */
    protected DumpRecord(Instant instant, String data) {
        this.instant = requireNonNull(instant);
        this.data = requireNonNull(data);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DumpRecord that = (DumpRecord) o;
        return instant.equals(that.instant) && data.equals(that.data);
    }

    /**
     * Returns the communication direction [RX, TX]
     */
    public abstract String getComDirection();

    /**
     * Returns the line data of record
     */
    public String getData() {
        return data;
    }

    /**
     * Returns the instant of record
     */
    public Instant getInstant() {
        return instant;
    }

    @Override
    public int hashCode() {
        return Objects.hash(instant, data);
    }

    /**
     * Stores the read dump record
     */
    public static class ReadDumpRecord extends DumpRecord {
        protected ReadDumpRecord(Instant timestamp, String data) {
            super(timestamp, data);
        }

        @Override
        public String getComDirection() {
            return "RX";
        }
    }

    public static class MessageDumpRecord<T extends WheellyMessage> extends ReadDumpRecord {
        private final T message;

        protected MessageDumpRecord(Instant timestamp, String data, T message) {
            super(timestamp, data);
            this.message = message;
        }

        public T getMessage() {
            return message;
        }
    }

    /**
     * Stores the written dump record
     */
    public static class WriteDumpRecord extends DumpRecord {
        protected WriteDumpRecord(Instant timestamp, String data) {
            super(timestamp, data);
        }

        @Override
        public String getComDirection() {
            return "TX";
        }
    }
}
