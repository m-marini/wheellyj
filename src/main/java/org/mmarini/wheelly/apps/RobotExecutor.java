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
import io.reactivex.rxjava3.schedulers.Schedulers;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jetbrains.annotations.NotNull;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.engines.ProcessorContext;
import org.mmarini.wheelly.engines.StateMachineAgent;
import org.mmarini.wheelly.engines.StateNode;
import org.mmarini.wheelly.swing.*;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.swing.Utils.*;

/**
 * Run a test to check for robot environment with random behavior agent
 */
public class RobotExecutor {
    public static final String EXECUTOR_SCHEMA_YML = "https://mmarini.org/wheelly/executor-schema-1.0";
    private static final Logger logger = LoggerFactory.getLogger(RobotExecutor.class);

    /**
     * Returns the agent from configuration files
     *
     * @param file the configuration file
     */
    static StateMachineAgent createAgent(String file) {
        try {
            JsonNode config = org.mmarini.yaml.Utils.fromFile(file);
            RobotControllerApi controller = AppYaml.controllerFromJson(config, Locator.root(), EXECUTOR_SCHEMA_YML);
            Locator agentLocator = Locator.locate(Locator.locate("agent").getNode(config).asText());
            if (agentLocator.getNode(config).isMissingNode()) {
                throw new IllegalArgumentException(format("Missing node %s", agentLocator));
            }
            return StateMachineAgent.create(config, agentLocator, controller);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the argument parser
     */
    @NotNull
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(RobotExecutor.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Run a session of interaction between robot and environment.");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-c", "--config")
                .setDefault("executor.yml")
                .help("specify yaml configuration file");
        parser.addArgument("-s", "--silent")
                .action(Arguments.storeTrue())
                .help("specify silent closing (no window messages)");
        parser.addArgument("-w", "--windows")
                .action(Arguments.storeTrue())
                .help("use multiple windows");
        parser.addArgument("-t", "--localTime")
                .setDefault(43200L)
                .type(Long.class)
                .help("specify number of seconds of session duration");
        parser.addArgument("-d", "--dump")
                .help("specify dump signal file");
        return parser;
    }

    /**
     * Application entry point
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        ArgumentParser parser = createParser();
        try {
            new RobotExecutor(parser.parseArgs(args)).run();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
    }

    private final EnvironmentPanel envPanel;
    private final PolarPanel polarPanel;
    private final MeanValue reactionRobotTime;
    private final MeanValue reactionRealTime;
    private final ComMonitor comMonitor;
    private final SensorMonitor sensorMonitor;
    private final StateEngineMonitor engineMonitor;
    private final Namespace args;
    private long start;
    private long sessionDuration;
    private StateMachineAgent agent;
    private long robotStartTimestamp;
    private long prevRobotStep;
    private long prevRealStep;
    private ComDumper dumper;
    private List<JFrame> allFrames;

    /**
     * Creates the roboto executor
     *
     * @param args the line command parsed arguments
     */
    public RobotExecutor(Namespace args) {
        this.args = requireNonNull(args);
        this.envPanel = new EnvironmentPanel();
        this.polarPanel = new PolarPanel();
        this.comMonitor = new ComMonitor();
        this.engineMonitor = new StateEngineMonitor();
        this.reactionRobotTime = MeanValue.zeros();
        this.reactionRealTime = MeanValue.zeros();
        this.robotStartTimestamp = -1;
        this.prevRobotStep = -1;
        this.prevRealStep = -1;
        this.sensorMonitor = new SensorMonitor();
    }

    /**
     * Creates the multi frames
     */
    private void createMultiFrames() {
        this.allFrames = List.of(
                createFrame(Messages.getString("RobotExecutor.title"), envPanel),
                createFixFrame(Messages.getString("Radar.title"), polarPanel),
                engineMonitor.createFrame(),
                sensorMonitor.createFrame(),
                comMonitor.createFrame()
        );
    }

    /**
     * Creates single application frame
     */
    private void createSingleFrames() {
        JTabbedPane panel = new JTabbedPane();
        panel.addTab(Messages.getString("RobotExecutor.tabPanel.envMap"), new JScrollPane(envPanel));
        panel.addTab(Messages.getString("RobotExecutor.tabPanel.polarMap"), new JScrollPane(polarPanel));
        panel.addTab(Messages.getString("RobotExecutor.tabPanel.engine"), new JScrollPane(engineMonitor));
        panel.addTab(Messages.getString("RobotExecutor.tabPanel.sensor"), new JScrollPane(sensorMonitor));
        panel.addTab(Messages.getString("RobotExecutor.tabPanel.com"), new JScrollPane(comMonitor));
        allFrames = List.of(
                createFrame(Messages.getString("RobotExecutor.title"), panel)
        );
    }

    /**
     * Handles controller status event
     *
     * @param status the controller status string
     */
    private void handleControllerStatus(String status) {
        sensorMonitor.onControllerStatus(status);
        comMonitor.onControllerStatus(status);
    }

    /**
     * Handles read line
     *
     * @param line the read line
     */
    private void handleReadLine(String line) {
        comMonitor.onReadLine(line);
        if (dumper != null) {
            dumper.dumpReadLine(line);
        }
    }

    /**
     * Handles the application shutdown
     */
    private void handleShutdown() {
        agent.shutdown();
        allFrames.forEach(JFrame::dispose);
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

    /**
     * Handles state change event
     *
     * @param state the state
     */
    private void handleState(StateNode state) {
        engineMonitor.addState(state);
    }

    /**
     * Handles step up of agent
     *
     * @param ctx the context
     */
    private void handleStepUp(ProcessorContext ctx) {
        RobotStatus status = ctx.robotStatus();
        if (robotStartTimestamp < 0) {
            robotStartTimestamp = status.simulationTime();
        }
        sensorMonitor.onStatus(status);
        envPanel.setRobotStatus(status);
        envPanel.setRadarMap(ctx.radarMap());
        long robotClock = status.simulationTime();
        long robotElapsed = robotClock - robotStartTimestamp;
        envPanel.setTimeRatio((double) robotElapsed / (System.currentTimeMillis() - start));
        long clock = System.currentTimeMillis();
        if (prevRobotStep >= 0) {
            envPanel.setReactionRealTime(reactionRealTime.add(clock - prevRealStep).value() * 1e-3);
            envPanel.setReactionRobotTime(reactionRobotTime.add(robotClock - prevRobotStep).value() * 1e-3);
        }
        prevRobotStep = robotClock;
        this.prevRealStep = clock;

        polarPanel.setPolarMap(ctx.polarMap());
        if (robotElapsed > sessionDuration) {
            agent.shutdown();
        }
    }

    /**
     * Handles the target event
     *
     * @param target the target point
     */
    private void handleTarget(Optional<Point2D> target) {
        envPanel.setTarget(target.orElse(null));
    }

    /**
     * Handles the trigger event
     *
     * @param trigger the trigger
     */
    private void handleTrigger(String trigger) {
        engineMonitor.addTrigger(trigger);
    }

    private void handleWindowClosing(WindowEvent windowEvent) {
        agent.shutdown();
    }

    /**
     * Handles the windows opened
     * Initializes the agent
     *
     * @param e the event
     */
    private void handleWindowOpened(WindowEvent e) {
        agent.init();
        RobotApi robot = agent.getController().getRobot();
        if (robot instanceof SimRobot) {
            Optional<ObstacleMap> obstaclesMap = ((SimRobot) robot).obstaclesMap();
            obstaclesMap.map(ObstacleMap::points)
                    .ifPresent(envPanel::setObstacleMap);
            obstaclesMap.map(ObstacleMap::gridSize)
                    .ifPresent(envPanel::setObstacleSize);
        }
    }

    /**
     * Handles written line
     *
     * @param line the written line
     */
    private void handleWrittenLine(String line) {
        comMonitor.onWriteLine(line);
        if (dumper != null) {
            dumper.dumpWrittenLine(line);
        }
    }

    /**
     * Initializes the application
     */
    private void init() {
        SwingObservable.window(allFrames.getFirst(), SwingObservable.WINDOW_ACTIVE)
                .filter(ev -> ev.getID() == WindowEvent.WINDOW_OPENED)
                .doOnNext(this::handleWindowOpened)
                .subscribe();

        allFrames.forEach(f -> SwingObservable.window(f, SwingObservable.WINDOW_ACTIVE)
                .filter(ev -> ev.getID() == WindowEvent.WINDOW_CLOSING)
                .doOnNext(this::handleWindowClosing)
                .subscribe());
        layHorizontally(allFrames);
    }

    /**
     * Starts the executor.
     * <p>
     * Creates the agent
     * Initializes the UI components
     * Opens the application frames (environment + radar)
     */
    private void run() {
        logger.atInfo().log("Creating Agent");
        this.sessionDuration = this.args.getLong("localTime");
        sessionDuration *= 1000;
        logger.atInfo().log("Starting session ...");
        this.agent = createAgent(this.args.getString("config"));
        Optional.ofNullable(this.args.getString("dump"))
                .ifPresent(file -> {
                    try {
                        dumper = ComDumper.fromFile(file);
                    } catch (IOException e) {
                        logger.atError().setCause(e).log();
                    }
                });
        double radarMaxDistance = agent.getMaxRadarDistance();
        polarPanel.setRadarMaxDistance(radarMaxDistance);
        logger.atInfo().setMessage("Session are running for {} sec...").addArgument(sessionDuration).log();
        this.start = System.currentTimeMillis();
        agent.readStepUp()
                .observeOn(Schedulers.io())
                .doOnNext(this::handleStepUp).subscribe();
        agent.readTargets()
                .observeOn(Schedulers.io())
                .doOnNext(this::handleTarget).subscribe();
        agent.readShutdown().doOnComplete(this::handleShutdown).subscribe();
        agent.readErrors()
                .observeOn(Schedulers.io())
                .doOnNext(err -> {
                    comMonitor.onError(err);
                    logger.atError().setCause(err).log();
                }).subscribe();
        agent.readReadLine()
                .observeOn(Schedulers.io())
                .doOnNext(this::handleReadLine).subscribe();
        agent.readWriteLine()
                .observeOn(Schedulers.io())
                .doOnNext(this::handleWrittenLine).subscribe();
        agent.readControllerStatus()
                .observeOn(Schedulers.io())
                .doOnNext(this::handleControllerStatus).subscribe();
        agent.readCommand()
                .observeOn(Schedulers.io())
                .doOnNext(sensorMonitor::onCommand).subscribe();
        agent.readTriggers()
                .observeOn(Schedulers.io())
                .doOnNext(this::handleTrigger)
                .subscribe();
        agent.readState()
                .observeOn(Schedulers.io())
                .doOnNext(this::handleState)
                .subscribe();
        if (agent.getController().getRobot() instanceof SimRobot simRobot) {
            simRobot.setOnObstacleChanged(sim ->
                    sim.obstaclesMap().map(ObstacleMap::points)
                            .ifPresent(envPanel::setObstacleMap));
        }
        if (args.getBoolean("windows")) {
            createMultiFrames();
        } else {
            createSingleFrames();
        }
        init();
        allFrames.forEach(f -> f.setVisible(true));
    }
}
