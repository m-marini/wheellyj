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

import com.fasterxml.jackson.databind.JsonNode;
import hu.akarnokd.rxjava3.swing.SwingObservable;
import io.reactivex.rxjava3.core.Observable;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jetbrains.annotations.NotNull;
import org.mmarini.rl.agents.Agent;
import org.mmarini.rl.agents.KpiCSVSubscriber;
import org.mmarini.rl.envs.Environment;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.envs.PolarRobotEnv;
import org.mmarini.wheelly.envs.RobotEnvironment;
import org.mmarini.wheelly.envs.WithPolarMap;
import org.mmarini.wheelly.envs.WithRadarMap;
import org.mmarini.wheelly.swing.*;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mmarini.wheelly.swing.Utils.*;
import static org.mmarini.yaml.Utils.fromFile;

/**
 * Run a test to check for robot environment with random behavior agent
 */
public class Wheelly {
    public static final Dimension DEFAULT_RADAR_DIMENSION = new Dimension(400, 400);
    public static final String[] DEFAULT_KPIS = {
            "^reward$",
            "^avgReward$",
            "^delta$",
            "^v0$",
            "^trainedCritic.output$",
    };
    public static final String WHEELLY_SCHEMA_YML = "https://mmarini.org/wheelly/wheelly-schema-0.1";
    private static final Logger logger = LoggerFactory.getLogger(Wheelly.class);

    /**
     * Creates kpis process
     *
     * @param agent   the agent
     * @param actions the action spec
     * @param file    the path of kpis
     * @param labels  the key labels to filter
     */
    private static void createKpis(Agent agent, Map<String, SignalSpec> actions, File file, String labels) {
        KpiCSVSubscriber sub;

        if (labels.length() == 0) {
            // Default kpis
            String[] labs = Stream.concat(Stream.of(DEFAULT_KPIS),
                    actions.keySet().stream()
                            .flatMap(n -> Stream.of("^policy", "^trainedPolicy", "^gradPolicy").map(k -> k + "." + n + "$")
                            )
            ).toArray(String[]::new);
            sub = KpiCSVSubscriber.create(file, labs);
        } else if ("all".equals(labels)) {
            // full kpis
            sub = KpiCSVSubscriber.create(file);
        } else {
            // filtered kpis
            sub = KpiCSVSubscriber.create(file, labels.split(","));
        }
        agent.readKpis().subscribe(sub);
    }

    @NotNull
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(Wheelly.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Run a session of interaction between robot and environment.");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-c", "--config")
                .setDefault("wheelly.yml")
                .help("specify yaml configuration file");
        parser.addArgument("-k", "--kpis")
                .setDefault("")
                .help("specify kpis path");
        parser.addArgument("-l", "--labels")
                .setDefault("")
                .help("specify kpi labels comma separated (all for all kpi)");
        parser.addArgument("-s", "--silent")
                .action(Arguments.storeTrue())
                .help("specify silent closing (no window messages)");
        parser.addArgument("-t", "--localTime")
                .setDefault(43200L)
                .type(Long.class)
                .help("specify number of seconds of session duration");
        parser.addArgument("-d", "--dump")
                .help("specify dump signal file");
        return parser;
    }

    /**
     * @param args command line arguments
     */
    public static void main(String[] args) {
        new Wheelly().start(args);
    }

    protected final JFrame frame;
    protected final EnvironmentPanel envPanel;
    private final AverageValue avgRewards;
    private final AverageValue reactionRobotTime;
    private final AverageValue reactionRealTime;
    private final ComMonitor comMonitor;
    private final JFrame comFrame;
    private final SensorMonitor sensorMonitor;
    private final JFrame sensorFrame;
    protected Namespace args;
    private long robotStartTimestamp;
    private Long sessionDuration;
    private PolarPanel polarPanel;
    private JFrame radarFrame;
    private long start;
    private RobotEnvironment environment;
    private Agent agent;
    private long prevRobotStep;
    private long prevStep;
    private ComDumper dumper;

    /**
     *
     */
    public Wheelly() {
        this.envPanel = new EnvironmentPanel();
        this.frame = createFrame(Messages.getString("Wheelly.title"), envPanel);
        this.comMonitor = new ComMonitor();
        comMonitor.setPrintTimestamp(true);
        this.comFrame = comMonitor.createFrame();
        this.sensorMonitor = new SensorMonitor();
        this.sensorFrame = sensorMonitor.createFrame();
        this.robotStartTimestamp = -1;
        this.avgRewards = AverageValue.create();
        this.reactionRobotTime = AverageValue.create();
        this.reactionRealTime = AverageValue.create();
        this.prevRobotStep = -1;
        this.prevStep = -1;
        SwingObservable.window(frame, SwingObservable.WINDOW_ACTIVE)
                .filter(ev -> ev.getID() == WindowEvent.WINDOW_OPENED)
                .doOnNext(this::handleWindowOpened)
                .subscribe();
        Observable.mergeArray(
                        SwingObservable.window(frame, SwingObservable.WINDOW_ACTIVE),
                        SwingObservable.window(sensorFrame, SwingObservable.WINDOW_ACTIVE),
                        SwingObservable.window(comFrame, SwingObservable.WINDOW_ACTIVE))
                .filter(ev -> ev.getID() == WindowEvent.WINDOW_CLOSING)
                .doOnNext(this::handleWindowClosing)
                .subscribe();
    }

    private void handleControllerStatus(String status) {
        sensorMonitor.onControllerStatus(status);
        comMonitor.onControllerStatus(status);
    }

    private void handleInference(RobotStatus status) {
        long robotClock = status.simulationTime();
        envPanel.setRobotStatus(status);
        sensorMonitor.onStatus(status);
        if (environment instanceof WithRadarMap) {
            envPanel.setRadarMap(((WithRadarMap) environment).getRadarMap());
        }
        if (environment instanceof WithPolarMap) {
            polarPanel.setPolarMap(((WithPolarMap) environment).getPolarMap());
        }
        long time = System.currentTimeMillis();
        if (prevRobotStep >= 0) {
            envPanel.setReactionRealTime(reactionRealTime.add(time - prevStep) * 1e-3);
            envPanel.setReactionRobotTime(reactionRobotTime.add(robotClock - prevRobotStep) * 1e-3);
        }
        prevRobotStep = robotClock;
        prevStep = time;
    }

    /**
     * Handles read line
     *
     * @param line read line
     */
    private void handleReadLine(String line) {
        comMonitor.onReadLine(line);
        if (dumper != null) {
            dumper.dumpReadLine(line);
        }
    }

    private void handleResult(Environment.ExecutionResult result) {
        double reward = result.getReward();
        envPanel.setReward(avgRewards.add(reward));
        sensorMonitor.onReward(reward);
        agent.observe(result);
    }

    private void handleShutdown() {
        try {
            agent.close();
        } catch (IOException e) {
            logger.atError().setCause(e).log();
        }
        frame.dispose();
        if (radarFrame != null) {
            radarFrame.dispose();
        }
        sensorFrame.dispose();
        comFrame.dispose();
        if (dumper != null) {
            try {
                dumper.close();
            } catch (IOException e) {
                logger.atError().setCause(e).log();
            }
        }
        if (!args.getBoolean("silent")) {
            JOptionPane.showMessageDialog(null,
                    "Completed", "Information", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void handleStatusReady(RobotStatus status) {
        if (robotStartTimestamp < 0) {
            robotStartTimestamp = status.simulationTime();
        }
        long robotElapsed = status.simulationTime() - robotStartTimestamp;
        envPanel.setTimeRatio((double) robotElapsed / (System.currentTimeMillis() - start));
        if (robotElapsed > sessionDuration) {
            environment.shutdown();
        }
    }

    private void handleWindowClosing(WindowEvent windowEvent) {
        environment.shutdown();
    }

    /**
     * Handles the windows opened
     * Initializes the agent
     *
     * @param e the event
     */
    private void handleWindowOpened(WindowEvent e) {
        Nd4j.zeros(1);
        RobotApi robot = environment.getController().getRobot();
        if (robot instanceof SimRobot) {
            Optional<ObstacleMap> obstaclesMap = ((SimRobot) robot).getObstaclesMap();
            obstaclesMap.map(ObstacleMap::getPoints)
                    .ifPresent(envPanel::setObstacleMap);
            obstaclesMap.map(ObstacleMap::topology)
                    .map(GridTopology::gridSize)
                    .ifPresent(envPanel::setObstacleSize);
        }
        environment.start();
    }

    /**
     * Handles written line
     *
     * @param line written line
     */
    private void handleWrittenLine(String line) {
        comMonitor.onWriteLine(line);
        if (dumper != null) {
            dumper.dumpWrittenLine(line);
        }
    }

    protected void start(String[] args) {
        ArgumentParser parser = createParser();
        try {
            this.args = parser.parseArgs(args);
            logger.atInfo().log("Creating environment");
            JsonNode config = fromFile(this.args.getString("config"));
            this.environment = Yaml.envFromJson(config, Locator.root(), WHEELLY_SCHEMA_YML);
            this.agent = Agent.fromConfig(config, Locator.locate("agent"), environment);

            logger.atInfo().log("Creating agent");
            if (environment instanceof PolarRobotEnv) {
                this.polarPanel = new PolarPanel();
                double radarMaxDistance = ((PolarRobotEnv) environment).getMaxRadarDistance();
                polarPanel.setRadarMaxDistance(radarMaxDistance);
                radarFrame = createFixFrame(Messages.getString("Radar.title"), DEFAULT_RADAR_DIMENSION, polarPanel);
                SwingObservable.window(radarFrame, SwingObservable.WINDOW_ACTIVE)
                        .filter(ev -> ev.getID() == WindowEvent.WINDOW_CLOSING)
                        .doOnNext(this::handleWindowClosing)
                        .subscribe();
                layHorizontally(frame, radarFrame, sensorFrame, comFrame);
            } else {
                layHorizontally(frame, sensorFrame, comFrame);
            }
            sessionDuration = this.args.getLong("localTime");
            logger.atInfo().log("Starting session ...");
            logger.atInfo().setMessage("Session are running for {} sec...").addArgument(sessionDuration).log();
            sessionDuration *= 1000;

            String kpis = this.args.getString("kpis");
            if (kpis.length() != 0) {
                createKpis(agent, environment.getActions(), new File(kpis), this.args.getString("labels"));
            }
            this.start = System.currentTimeMillis();
            Optional.ofNullable(this.args.getString("dump"))
                    .ifPresent(file -> {
                        try {
                            this.dumper = ComDumper.fromFile(file);
                        } catch (IOException e) {
                            logger.atError().setCause(e).log();
                        }
                    });
            environment.readRobotStatus()
                    .doOnNext(this::handleStatusReady)
                    .subscribe();
            environment.readReadLine()
                    .doOnNext(this::handleReadLine)
                    .subscribe();
            environment.readWriteLine()
                    .doOnNext(this::handleWrittenLine)
                    .subscribe();
            environment.readCommand()
                    .doOnNext(sensorMonitor::onCommand)
                    .subscribe();
            environment.readErrors().doOnNext(err -> {
                        comMonitor.onError(err);
                        logger.atError().setCause(err).log();
                    })
                    .subscribe();
            environment.readControllerStatus()
                    .doOnNext(this::handleControllerStatus)
                    .subscribe();
            environment.readShutdown()
                    .doOnComplete(this::handleShutdown)
                    .subscribe();

            environment.setOnInference(this::handleInference);
            environment.setOnAct(agent::act);
            environment.setOnResult(this::handleResult);

            frame.setVisible(true);
            (radarFrame != null
                    ? Stream.of(comFrame, sensorFrame, radarFrame, frame)
                    : Stream.of(comFrame, sensorFrame, frame)).forEach(f -> f.setVisible(true));
            comFrame.setState(JFrame.ICONIFIED);

            environment.start();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        } catch (IOException e) {
            logger.atError().setCause(e).log("IO exception");
            System.exit(1);
        }
    }
}
