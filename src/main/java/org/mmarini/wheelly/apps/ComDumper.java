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

package org.mmarini.wheelly.apps;

import java.io.Closeable;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Dumps the line comunications with robot
 */
public class ComDumper implements Closeable {

    /**
     * Returns the dumper to file
     *
     * @param file the filename
     */
    public static ComDumper fromFile(String file) throws IOException {
        return new ComDumper(new PrintWriter(new FileWriter(file)));
    }

    private final PrintWriter writer;

    /**
     * Creates the dumper
     *
     * @param writer the writer
     */
    public ComDumper(PrintWriter writer) {
        this.writer = writer;
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    /**
     * Returns the dumper after dumping the received line
     *
     * @param line the received line
     */
    public ComDumper dumpReadLine(String line) {
        writer.print(System.currentTimeMillis());
        writer.print(" < ");
        writer.print(line);
        writer.println();
        return this;
    }

    /**
     * Returns the dumper after dumping the written line
     *
     * @param line the written line
     */
    public ComDumper dumpWrittenLine(String line) {
        writer.print(System.currentTimeMillis());
        writer.print(" > ");
        writer.print(line);
        writer.println();
        return this;
    }
}
