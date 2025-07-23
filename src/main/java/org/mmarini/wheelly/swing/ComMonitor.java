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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class ComMonitor extends MatrixTable {

    public static final String MOTION = "motion";
    public static final String PROXY = "proxy";
    public static final String CAMERA = "camera";
    public static final String CONTACTS = "contacts";
    public static final String SUPPLY = "supply";
    public static final String SCAN = "scan";
    public static final String MOVE = "move";
    public static final String OTHER = "other";
    public static final String CONFIG = "config";
    public static final String ERROR_KEY = "error";
    public static final String CONTROLLER_KEY = "controller";
    private static final String CONFIG_COMMANDS_REGEX = "(cs|cc|ct|cl|cr|cm|ci|fl|fr|tcsl|tcsr) .*";

    private static final Predicate<String> CONFIG_COMMANDS = Pattern.compile(CONFIG_COMMANDS_REGEX).asMatchPredicate();
    private static final Predicate<String> CONFIG_COMMANDS_ACK = Pattern.compile("// " + CONFIG_COMMANDS_REGEX).asMatchPredicate();
    private static final Logger logger = LoggerFactory.getLogger(ComMonitor.class);

    public ComMonitor() {
        addColumn(CONTROLLER_KEY, Messages.getString("ComMonitor.controller"), 3).setScrollOnChange(true);
        addColumn(MOTION, Messages.getString("ComMonitor.motion"), 70);
        addColumn(PROXY, Messages.getString("ComMonitor.proxy"), 40);
        addColumn(CAMERA, Messages.getString("ComMonitor.camera"), 80);
        addColumn(CONTACTS, Messages.getString("ComMonitor.contacts"), 20);
        addColumn(SUPPLY, Messages.getString("ComMonitor.supply"), 16);
        addColumn(MOVE, Messages.getString("ComMonitor.move"), 11);
        addColumn(SCAN, Messages.getString("ComMonitor.scan"), 6);
        addColumn(CONFIG, Messages.getString("ComMonitor.config"), 36);
        addColumn(OTHER, Messages.getString("ComMonitor.other"), 35);
        addColumn(ERROR_KEY, Messages.getString("ComMonitor.error"), 50);
        setPrintTimestamp(false);
    }

    public JFrame createFrame() {
        return createFrame(Messages.getString("ComMonitor.title"));
    }

    public void onControllerStatus(String status) {
        printf(CONTROLLER_KEY, status);
    }

    public void onError(Throwable err) {
        printf(ERROR_KEY, String.valueOf(err.getMessage()));
        logger.atError().setCause(err).log("Error message");
    }

    public void onReadLine(String line) {
        String messageClass = switch (line) {
            case String l when l.startsWith("mt ") -> MOTION;
            case String l when l.startsWith("px ") -> PROXY;
            case String l when l.startsWith("ct ") -> CONTACTS;
            case String l when l.startsWith("sv ") -> SUPPLY;
            case String l when l.startsWith("ck ") -> CONFIG;
            case String l when l.startsWith("qr ") -> CAMERA;
            case String l when CONFIG_COMMANDS_ACK.test(l) -> CONFIG;
            case String l when l.startsWith("!!") -> ERROR_KEY;
            default -> OTHER;
        };
        printf(messageClass, " %s", line);
        if (line.startsWith("!!")) {
            logger.atError().log(line);
        }
    }

    public void onWriteLine(String line) {
        String messageClass = switch (line) {
            case String l when l.startsWith("ha") -> MOVE;
            case String l when l.startsWith("mv ") -> MOVE;
            case String l when l.startsWith("sc ") -> SCAN;
            case String l when l.startsWith("ck ") -> CONFIG;
            case String l when CONFIG_COMMANDS.test(l) -> CONFIG;
            default -> OTHER;
        };
        printf(messageClass, " %s", line);
    }
}
