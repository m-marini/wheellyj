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

import org.mmarini.wheelly.apis.RobotController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class ComMonitor extends MatrixTable {

    public static final String STATUS = "status";
    public static final String SCAN = "scan";
    public static final String MOVE = "move";
    public static final String OTHER = "other";
    public static final String CONFIG = "config";
    public static final String ERROR_KEY = "error";
    public static final String CONTROLLER_KEY = "controller";
    private static final String CONFIG_COMMANDS_REGEX = "(cs|cc|ct|cl|cr|cm) .*";

    private static final Predicate<String> CONFIG_COMMANDS = Pattern.compile(CONFIG_COMMANDS_REGEX).asMatchPredicate();
    private static final Predicate<String> CONFIG_COMMANDS_ACK = Pattern.compile("// " + CONFIG_COMMANDS_REGEX).asMatchPredicate();
    private static final Logger logger = LoggerFactory.getLogger(ComMonitor.class);
    private static final Map<String, String> CONTROLLER_STATUS_MAP = Map.of(
            RobotController.CONFIGURING, "cfg",
            RobotController.CONNECTING, "con",
            RobotController.CLOSING, "cls",
            RobotController.WAITING_RETRY, "wtr",
            RobotController.SCAN, "act",
            RobotController.MOVE, "act",
            RobotController.WAIT_COMMAND_INTERVAL, "act"
    );
    private String prevController;

    public ComMonitor() {
        addColumn(CONTROLLER_KEY, Messages.getString("ComMonitor.controller"), 3);
        addColumn(STATUS, Messages.getString("ComMonitor.status"), 77);
        addColumn(MOVE, Messages.getString("ComMonitor.move"), 11);
        addColumn(SCAN, Messages.getString("ComMonitor.scan"), 6);
        addColumn(CONFIG, Messages.getString("ComMonitor.config"), 36);
        addColumn(OTHER, Messages.getString("ComMonitor.other"), 35);
        addColumn(ERROR_KEY, Messages.getString("ComMonitor.error"), 50);
        setPrintTimestamp(false);
    }

    public void onControllerStatus(String status) {
        String stat = CONTROLLER_STATUS_MAP.getOrDefault(status, status);
        if (!stat.equals(prevController)) {
            prevController = stat;
            printf(CONTROLLER_KEY, stat);
        }
    }

    public void onError(Throwable err) {
        printf(ERROR_KEY, String.valueOf(err.getMessage()));
        logger.atError().setCause(err).log();
    }

    public void onReadLine(String line) {
        if (line.startsWith("st ")) {
            printf(STATUS, " %s", line);
        } else if (CONFIG_COMMANDS_ACK.test(line) || line.startsWith("ck ")) {
            printf(CONFIG, " %s", line);
        } else {
            printf(OTHER, " %s", line);
            if (line.startsWith("!!")) {
                logger.atError().log(line);
            }
        }
    }

    public void onWriteLine(String line) {
        if (line.equals("ha") || line.startsWith("mv ")) {
            printf(MOVE, " %s", line);
        } else if (line.startsWith("sc ")) {
            printf(SCAN, " %s", line);
        } else if (CONFIG_COMMANDS.test(line) || line.startsWith("ck ")) {
            printf(CONFIG, " %s", line);
        } else {
            printf(OTHER, " %s", line);
        }
    }
}
