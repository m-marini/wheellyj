/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.apps;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Timed;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jetbrains.annotations.NotNull;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.LineSocket;
import org.mmarini.wheelly.rx.RXFunc;
import org.mmarini.wheelly.swing.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static java.lang.String.format;


/**
 * Run a test to check for robot environment with random behaviour agent
 */
public class WheellyMeasures {
    public static final int CONNECTION_TIMEOUT = 3000;
    public static final int READ_TIMEOUT = 20000;
    public static final long DEFAULT_IDLE_INTERVAL = 1000L;
    public static final int DEFAULT_PORT = 22;
    private static final Logger logger = LoggerFactory.getLogger(WheellyMeasures.class);
    private static final int DEFAULT_MAX_POWER = 255;

    /**
     * Returns the argument parser
     */
    @NotNull
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(WheellyMeasures.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Run a session of measure tests.");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-s", "--server")
                .setDefault("wheelly")
                .help("specify robot server name or ip address");
        parser.addArgument("-p", "--port")
                .type(Integer.class)
                .setDefault(DEFAULT_PORT)
                .help("specify robot server name or ip address");
        parser.addArgument("-o", "--output")
                .setDefault("measures/")
                .help("specify output files prefix");
        parser.addArgument("-i", "--idle")
                .type(Long.class)
                .setDefault(DEFAULT_IDLE_INTERVAL)
                .help("specify idle interval between tests (ms)");
        parser.addArgument("-x", "--maxPower")
                .type(Integer.class)
                .setDefault(DEFAULT_MAX_POWER)
                .help("specify idle interval between tests (ms)");
        parser.addArgument("number")
                .type(Integer.class)
                .help("specify the number of tests");
        parser.addArgument("duration")
                .type(Integer.class)
                .help("specify the single test duration (ms)");
        return parser;
    }

    /**
     * Application entry point
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        new WheellyMeasures().start(args);
    }

    /**
     * Returns the random power of motor
     *
     * @param random the ransom number generator
     * @param max    the max absolute value
     */
    private static int nextPower(Random random, int max) {
        int steps = max / 5;
        return (random.nextInt(steps * 2 + 1) - steps) * 5;
    }

    /**
     * Writes csv values
     *
     * @param writer the writer
     * @param data   the data
     */
    static void writeCSV(PrintWriter writer, int[][] data) {
        for (int[] datum : data) {
            for (int j = 0; j < datum.length; j++) {
                if (j > 0) {
                    writer.print(",");
                }
                writer.print(datum[j]);
            }
            writer.println();
        }
    }

    private Namespace args;
    private LineSocket socket;
    private String outputPrefix;

    /**
     * Creates the roboto executor
     */
    public WheellyMeasures() {
    }

    /**
     * Returns the measure set read from socket
     *
     * @param prefix the prefix id
     */
    private int[][] readMeasureSet(String prefix) {
        // Waits for start record
        Pattern startRegex = Pattern.compile(prefix + "s (\\d*)");
        int n = socket.readLines()
                .map(Timed::value)
                .doOnNext(line ->
                        logger.atDebug().log("< {}", line))
                .map(startRegex::matcher)
                .filter(Matcher::matches)
                .map(matcher -> Integer.parseInt(matcher.group(1)))
                .blockingFirst();
        logger.atDebug().log("Measure set with {} record", n);

        // Reads data
        Pattern dataRegex = Pattern.compile(prefix + "d (\\d*) (\\d*) (-?\\d*) (-?\\d*)");
        int[][] result = (int[][]) RXFunc.validate(
                        RXFunc.validate(
                                        socket.readLines()
                                                .map(Timed::value)
                                                .doOnNext(line ->
                                                        logger.atDebug().log("< {}", line))
                                                .map(dataRegex::matcher),
                                        Matcher::matches)
                                .zipWith(IntStream.range(0, n).boxed().toList(), Tuple2::of),
                        t ->
                                Integer.parseInt(t._1.group(1)) == t._2)
                .map(t -> {
                    Matcher matcher = t._1;
                    int time = Integer.parseInt(matcher.group(2));
                    int pulses = Integer.parseInt(matcher.group(3));
                    int power = Integer.parseInt(matcher.group(4));
                    return new int[]{time, pulses, power};
                })
                .toList()
                .map(List::toArray)
                .blockingGet();

        // Waits for stop record
        String stop = prefix + "e";
        socket.readLines()
                .map(Timed::value)
                .doOnNext(line ->
                        logger.atDebug().log("< {}", line)
                )
                .filter(stop::equals)
                .blockingFirst();
        logger.atDebug().log("Measure set complete");
        return result;
    }

    /**
     * Returns the report from socket
     *
     * @param leftPwr  the left power
     * @param rightPwr the right power
     */
    private Report readReport(int leftPwr, int rightPwr) {
        int[][] leftMeasures = readMeasureSet("l");
        int[][] rightMeasures = readMeasureSet("r");
        return new Report(leftPwr, rightPwr, leftMeasures, rightMeasures);
    }

    /**
     * Runs the test
     *
     * @param duration the duration (ms)
     * @param leftPwr  the left power
     * @param rightPwr the right power
     */
    private Report runTest(int duration, int leftPwr, int rightPwr) {
        String cmd = "start " + duration + " " + leftPwr + " " + rightPwr;
        logger.atInfo().log("  {}", cmd);
        socket.writeCommand(cmd);
        waitForCompletion();
        return readReport(leftPwr, rightPwr);
    }

    /**
     * Returns the test report by running the tests
     */
    private void runTests() throws IOException {
        logger.atInfo().log("Started");
        int n = args.getInt("number");
        int duration = args.getInt("duration");
        Random random = new Random();
        long idle = args.getLong("idle");
        int maxPower = args.getInt("maxPower");
        for (int i = 0; i < n; i++) {
            int leftPwr = nextPower(random, maxPower);
            int rightPwr = nextPower(random, maxPower);
            logger.atInfo().log("Test {}", i + 1);
            Report report = runTest(duration, leftPwr, rightPwr);
            writeResults(report);
            Completable.timer(idle, TimeUnit.MILLISECONDS)
                    .blockingAwait();
            report = runTest(duration, -leftPwr, -rightPwr);
            writeResults(report);
            Completable.timer(idle, TimeUnit.MILLISECONDS)
                    .blockingAwait();
        }
    }

    /**
     * Starts the test.
     *
     * @param args the command line parameters
     */
    private void start(String[] args) {
        ArgumentParser parser = createParser();
        try {
            this.args = parser.parseArgs(args);
            this.outputPrefix = this.args.getString("output");
            this.socket = new LineSocket(this.args.getString("server"), this.args.getInt("port"),
                    CONNECTION_TIMEOUT, READ_TIMEOUT);
            socket.connect();

            // Wait for the welcome message
            waitForWelcome();
            runTests();
            socket.close();
            logger.atInfo().log("Completed");
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        } catch (IOException e) {
            logger.atError().setCause(e).log();
        }
    }

    /**
     * Waits for test completion
     */
    private void waitForCompletion() {
        Predicate<String> isError = Pattern.compile("!! .*").asMatchPredicate();
        RXFunc.validate(socket.readLines()
                        .map(Timed::value), x -> !isError.test(x))
                .filter("completed"::equals)
                .blockingFirst();
        logger.atDebug().log("Test completed");
    }

    /**
     * Waits for welcome signal
     *
     * @throws IOException in case of error
     */
    private void waitForWelcome() throws IOException {
        Predicate<String> linePredicate = Pattern.compile("Wheelly measures ready").asMatchPredicate();
        socket.readLines()
                .map(Timed::value)
                .doOnNext(line ->
                        logger.atDebug().log("< {}", line))
                .filter(linePredicate::test)
                .blockingFirst();
    }

    /**
     * Writes the report of a test
     *
     * @param report the report data
     * @throws IOException in case of error
     */
    private void writeResults(Report report) throws IOException {
        String testId = format("%s_%2$tY%2$tm%2$td_%2$tH%2$tM%2$tS_%2$tL_%3$d_%4$d",
                outputPrefix, Calendar.getInstance(), report.leftPower, report.rightPower);
        File headMeasuresFile = new File(testId + "_head.csv");
        //noinspection ResultOfMethodCallIgnored
        headMeasuresFile.getParentFile().mkdirs();
        try (PrintWriter writer = new PrintWriter(new FileWriter(headMeasuresFile))) {
            writer.print(report.leftPower);
            writer.print(",");
            writer.print(report.rightPower);
            writer.println();
        }
        File leftMeasuresFile = new File(testId + "_left.csv");
        //noinspection ResultOfMethodCallIgnored
        leftMeasuresFile.getParentFile().mkdirs();
        try (PrintWriter writer = new PrintWriter(new FileWriter(leftMeasuresFile))) {
            writeCSV(writer, report.leftMeasures);
        }
        File rightMeasuresFile = new File(testId + "_right.csv");
        //noinspection ResultOfMethodCallIgnored
        leftMeasuresFile.getParentFile().mkdirs();
        try (PrintWriter writer = new PrintWriter(new FileWriter(rightMeasuresFile))) {
            writeCSV(writer, report.rightMeasures);
        }
    }

    record Report(int leftPower, int rightPower, int[][] leftMeasures, int[][] rightMeasures) {
    }
}
