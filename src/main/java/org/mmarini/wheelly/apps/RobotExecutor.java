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

import hu.akarnokd.rxjava3.swing.SwingObservable;
import io.reactivex.rxjava3.core.Observable;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jetbrains.annotations.NotNull;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.engines.ProcessorContext;
import org.mmarini.wheelly.engines.StateMachineAgent;
import org.mmarini.wheelly.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.util.Optional;

import static org.mmarini.wheelly.swing.Utils.*;

/**
 * Run a test to check for robot environment with random behavior agent
 */
public class RobotExecutor {
    public static final Dimension DEFALT_RADAR_DIMENSION = new Dimension(400, 400);
    private static final Logger logger = LoggerFactory.getLogger(RobotExecutor.class);

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
        parser.addArgument("-r", "--robot")
                .setDefault("robot.yml")
                .help("specify robot yaml configuration file");
        parser.addArgument("-c", "--controller")
                .setDefault("controller.yml")
                .help("specify controller yaml configuration file");
        parser.addArgument("-a", "--agent")
                .setDefault("agent.yml")
                .help("specify agent yaml configuration file");
        parser.addArgument("-s", "--silent")
                .action(Arguments.storeTrue())
                .help("specify silent closing (no window messages)");
        parser.addArgument("-t", "--time")
                .setDefault(43200L)
                .type(Long.class)
                .help("specify number of seconds of session duration");
        return parser;
    }

    /**
     * Application entry point
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        new RobotExecutor().start(args);
    }

    private final JFrame frame;
    private final JFrame radarFrame;
    private final EnvironmentPanel envPanel;
    private final PolarPanel polarPanel;
    private final AverageValue reactionRobotTime;
    private final AverageValue reactionRealTime;
    private final ComMonitor comMonitor;
    private final JFrame comFrame;
    private final SensorMonitor sensorMonitor;
    private final JFrame sensorFrame;
    private Namespace args;
    private long start;
    private long sessionDuration;
    private StateMachineAgent agent;
    private long robotStartTimestamp;
    private long prevRobotStep;
    private long prevRealStep;

    /**
     * Creates the roboto executor
     */
    public RobotExecutor() {
        this.envPanel = new EnvironmentPanel();
        this.polarPanel = new PolarPanel();
        this.frame = createFrame(Messages.getString("RobotExecutor.title"), envPanel);
        this.radarFrame = createFixFrame(Messages.getString("Radar.title"), DEFALT_RADAR_DIMENSION, polarPanel);
        this.comMonitor = new ComMonitor();
        comMonitor.setPrintTimestamp(true);
        this.comFrame = comMonitor.createFrame();
        this.sensorMonitor = new SensorMonitor();
        this.sensorFrame = sensorMonitor.createFrame();
        this.reactionRobotTime = AverageValue.create();
        this.reactionRealTime = AverageValue.create();
        this.robotStartTimestamp = -1;
        this.prevRobotStep = -1;
        this.prevRealStep = -1;
        SwingObservable.window(frame, SwingObservable.WINDOW_ACTIVE)
                .filter(ev -> ev.getID() == WindowEvent.WINDOW_OPENED)
                .doOnNext(this::handleWindowOpened)
                .subscribe();
        Observable.mergeArray(
                        SwingObservable.window(frame, SwingObservable.WINDOW_ACTIVE),
                        SwingObservable.window(comFrame, SwingObservable.WINDOW_ACTIVE),
                        SwingObservable.window(sensorFrame, SwingObservable.WINDOW_ACTIVE),
                        SwingObservable.window(radarFrame, SwingObservable.WINDOW_ACTIVE))
                .filter(ev -> ev.getID() == WindowEvent.WINDOW_CLOSING)
                .doOnNext(this::handleWindowClosing)
                .subscribe();
        layHorizontally(frame, radarFrame, sensorFrame, comFrame);
    }

    /**
     * Returns the agent from configuration files
     */
    private StateMachineAgent createAgent() {
        RobotApi robot = RobotApi.fromConfig(args.getString("robot"));
        RobotControllerApi controller = RobotControllerApi.fromConfig(args.getString("controller"), robot);
        return StateMachineAgent.fromConfig(args.getString("agent"), controller);
    }

    private void handleControllerStatus(String status) {
        sensorMonitor.onControllerStatus(status);
        comMonitor.onControllerStatus(status);
    }

    /**
     * Handles the application shutdown
     */
    private void handleShutdown() {
        frame.dispose();
        radarFrame.dispose();
        comFrame.dispose();
        sensorFrame.dispose();
        if (!args.getBoolean("silent")) {
            JOptionPane.showMessageDialog(null,
                    "Completed", "Information", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Handles step up of agent
     *
     * @param ctx the context
     */
    private void handleStepUp(ProcessorContext ctx) {
        RobotStatus status = ctx.getRobotStatus();
        if (robotStartTimestamp < 0) {
            robotStartTimestamp = status.getTime();
        }
        sensorMonitor.onStatus(status);
        envPanel.setRobotStatus(status);
        envPanel.setRadarMap(ctx.getRadarMap());
        long robotClock = status.getTime();
        long robotElapsed = robotClock - robotStartTimestamp;
        envPanel.setTimeRatio((double) robotElapsed / (System.currentTimeMillis() - start));
        long clock = System.currentTimeMillis();
        if (prevRobotStep >= 0) {
            envPanel.setReactionRealTime(reactionRealTime.add(clock - prevRealStep) * 1e-3);
            envPanel.setReactionRobotTime(reactionRobotTime.add(robotClock - prevRobotStep) * 1e-3);
        }
        prevRobotStep = robotClock;
        this.prevRealStep = clock;

        polarPanel.setPolarMap(ctx.getPolarMap());
        if (robotElapsed > sessionDuration) {
            agent.shutdown();
        }
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
            Optional<ObstacleMap> obstaclesMap = ((SimRobot) robot).getObstaclesMap();
            obstaclesMap.map(ObstacleMap::getPoints)
                    .ifPresent(envPanel::setObstacleMap);
            obstaclesMap.map(ObstacleMap::getTopology)
                    .map(GridTopology::getGridSize)
                    .ifPresent(envPanel::setObstacleSize);
        }
    }

    /**
     * Starts the executor.
     * <p>
     * Creates the agent
     * Initializes the UI components
     * Opens the application frames (environment + radar)
     *
     * @param args the command line parameters
     */
    private void start(String[] args) {
        ArgumentParser parser = createParser();
        try {
            this.args = parser.parseArgs(args);
            logger.atInfo().log("Creating Agent");
            this.sessionDuration = this.args.getLong("time");
            sessionDuration *= 1000;
            logger.atInfo().log("Starting session ...");
            this.agent = createAgent();
            double radarMaxDistance = agent.getMaxRadarDistance();
            polarPanel.setRadarMaxDistance(radarMaxDistance);
            logger.atInfo().setMessage("Session are running for {} sec...").addArgument(sessionDuration).log();
            this.start = System.currentTimeMillis();
            agent.readStepUp().doOnNext(this::handleStepUp).subscribe();
            agent.readShutdown().doOnComplete(this::handleShutdown).subscribe();
            agent.readErrors().doOnNext(err -> {
                comMonitor.onError(err);
                logger.atError().setCause(err).log();
            }).subscribe();
            agent.readReadLine().doOnNext(comMonitor::onReadLine).subscribe();
            agent.readWriteLine().doOnNext(comMonitor::onWriteLine).subscribe();
            agent.readControllerStatus().doOnNext(this::handleControllerStatus).subscribe();
            agent.readCommand().doOnNext(sensorMonitor::onCommand).subscribe();
            frame.setVisible(true);
            radarFrame.setVisible(true);
            sensorFrame.setVisible(true);
            comFrame.setVisible(true);
            comFrame.setState(JFrame.ICONIFIED);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
    }
}
