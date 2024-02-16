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
import io.reactivex.rxjava3.schedulers.Schedulers;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mmarini.rl.agents.Agent;
import org.mmarini.rl.agents.KpiBinSubscriber;
import org.mmarini.rl.envs.Environment;
import org.mmarini.wheelly.apis.ObstacleMap;
import org.mmarini.wheelly.apis.RobotApi;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apis.SimRobot;
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
import java.util.Optional;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.mmarini.wheelly.swing.Utils.*;
import static org.mmarini.yaml.Utils.fromFile;

/**
 * Run a test to check for robot environment with random behavior agent
 */
public class Wheelly {
    public static final Dimension DEFAULT_RADAR_DIMENSION = new Dimension(400, 400);
    public static final String WHEELLY_SCHEMA_YML = "https://mmarini.org/wheelly/wheelly-schema-1.0";
    private static final Logger logger = LoggerFactory.getLogger(Wheelly.class);

    static {
        Nd4j.zeros(1);
    }

    /**
     * Returns the argument parser
     */
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
                .setDefault("default")
                .help("specify kpi label regex comma separated (all for all kpi, batch for batch training kpis)");
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
    private RobotEnvironment environment;
    private Agent agent;
    private long prevRobotStep;
    private long prevStep;
    private ComDumper dumper;
    private KpiBinSubscriber kpiSubscriber;

    /**
     *
     */
    public Wheelly() {
        this.envPanel = new EnvironmentPanel();
        JScrollPane scrollEnv = new JScrollPane(envPanel);
        this.frame = createFrame(Messages.getString("Wheelly.title"), scrollEnv);
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
        if (kpiSubscriber != null) {
            logger.atInfo().log("Waiting for completion ...");
            kpiSubscriber.readCompleted().blockingAwait();
        }
        logger.atInfo().log("Completed.");
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
        envPanel.setTimeRatio(environment.getController().simRealSpeed());
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
        RobotApi robot = environment.getController().getRobot();
        if (robot instanceof SimRobot) {
            Optional<ObstacleMap> obstaclesMap = ((SimRobot) robot).obstaclesMap();
            obstaclesMap.map(ObstacleMap::points)
                    .ifPresent(envPanel::setObstacleMap);
            obstaclesMap.map(ObstacleMap::gridSize)
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
            this.environment = AppYaml.envFromJson(config, Locator.root(), WHEELLY_SCHEMA_YML);
            Locator agentLocator = Locator.locate(Locator.locate("agent").getNode(config).asText());
            if (agentLocator.getNode(config).isMissingNode()) {
                throw new IllegalArgumentException(format("Missing node %s", agentLocator));
            }
            this.agent = Agent.fromConfig(config, agentLocator, environment);

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
            if (!kpis.isEmpty()) {
                this.kpiSubscriber = KpiBinSubscriber.createFromLabels(new File(kpis), this.args.getString("labels"));
                agent.readKpis().observeOn(Schedulers.io(), true).subscribe(kpiSubscriber);
            }
            Optional.ofNullable(this.args.getString("dump"))
                    .ifPresent(file -> {
                        try {
                            this.dumper = ComDumper.fromFile(file);
                        } catch (IOException e) {
                            logger.atError().setCause(e).log();
                        }
                    });
            environment.readRobotStatus()
                    .observeOn(Schedulers.io())
                    .doOnNext(this::handleStatusReady)
                    .subscribe();
            environment.readReadLine()
                    .observeOn(Schedulers.io())
                    .doOnNext(this::handleReadLine)
                    .subscribe();
            environment.readWriteLine()
                    .observeOn(Schedulers.io())
                    .doOnNext(this::handleWrittenLine)
                    .subscribe();
            environment.readCommand()
                    .observeOn(Schedulers.io())
                    .doOnNext(sensorMonitor::onCommand)
                    .subscribe();
            environment.readErrors()
                    .observeOn(Schedulers.io())
                    .doOnNext(err -> {
                        comMonitor.onError(err);
                        logger.atError().setCause(err).log();
                    })
                    .subscribe();
            environment.readControllerStatus()
                    .observeOn(Schedulers.io())
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
