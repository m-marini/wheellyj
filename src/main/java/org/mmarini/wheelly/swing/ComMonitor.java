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

package org.mmarini.wheelly.swing;

public class ComMonitor extends MatrixTable {

    public static final String STATUS = "status";
    public static final String SCAN = "scan";
    public static final String MOVE = "move";
    public static final String OTHER = "other";
    public static final String CONFIG = "config";
    public static final String ERROR_KEY = "error";

    public ComMonitor() {
        addColumn(STATUS, Messages.getString("ComMonitor.status"), 77);
        addColumn(SCAN, Messages.getString("ComMonitor.scan"), 7);
        addColumn(MOVE, Messages.getString("ComMonitor.move"), 12);
        addColumn(CONFIG, Messages.getString("ComMonitor.config"), 36);
        addColumn(OTHER, Messages.getString("ComMonitor.other"), 35);
        addColumn(ERROR_KEY, Messages.getString("ComMonitor.error"), 50);
        setPrintTimestamp(false);
    }

    public void onError(Throwable err) {
        printf(ERROR_KEY, String.valueOf(err.getMessage()));
    }

    public void onReadLine(String line) {
        if (line.startsWith("st ")) {
            printf(STATUS, line);
        } else if (line.startsWith("ck ")
                || line.startsWith("// cc ")
                || line.startsWith("// cs")
                || line.startsWith("// ct")
                || line.startsWith("// cm")) {
            printf(CONFIG, line);
        } else {
            printf(OTHER, line);
        }
    }

    public void onWriteLine(String line) {
        if (line.equals("ha") || line.startsWith("mv ")) {
            printf(MOVE, line);
        } else if (line.startsWith("sc ")) {
            printf(SCAN, line);
        } else if (line.startsWith("ck ")
                || line.startsWith("cc ")
                || line.startsWith("ct ")
                || line.startsWith("cm ")
                || line.startsWith("cs")) {
            printf(CONFIG, line);
        } else {
            printf(OTHER, line);
        }
    }
}
