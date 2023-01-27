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
import io.reactivex.rxjava3.core.BackpressureStrategy;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jetbrains.annotations.NotNull;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.engines.ProcessorContext;
import org.mmarini.wheelly.engines.StateMachineAgent;
import org.mmarini.wheelly.swing.EnvironmentPanel;
import org.mmarini.wheelly.swing.Messages;
import org.mmarini.wheelly.swing.PolarPanel;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mmarini.wheelly.swing.Utils.*;
import static org.mmarini.yaml.schema.Validator.*;

/**
 * Run a test to check for robot environment with random behavior agent
 */
public class RobotExecutor {
    public static final Dimension DEFALT_RADAR_DIMENSION = new Dimension(400, 400);
    private static final Logger logger = LoggerFactory.getLogger(RobotExecutor.class);
    private static final Validator BASE_CONFIG = objectPropertiesRequired(Map.of(
            "version", string(values("0.4")),
            "active", string(),
            "configurations", object()
    ), List.of("version", "active", "configurations"));

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
     * Returns an object instance from configuration file
     *
     * @param <T>        the returned object class
     * @param file       the filename
     * @param args       the builder additional arguments
     * @param argClasses the builder additional argument classes
     */
    protected static <T> T fromConfig(String file, Object[] args, Class<?>[] argClasses) {
        try {
            JsonNode config = Utils.fromFile(file);
            BASE_CONFIG.apply(Locator.root()).accept(config);
            String active = Locator.locate("active").getNode(config).asText();
            Locator baseLocator = Locator.locate("configurations").path(active);
            return Utils.createObject(config, baseLocator, args, argClasses);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        this.reactionRobotTime = AverageValue.create();
        this.reactionRealTime = AverageValue.create();
        this.robotStartTimestamp = -1;
        this.prevRobotStep = -1;
        this.prevRealStep = -1;
        SwingObservable.window(frame, SwingObservable.WINDOW_ACTIVE)
                .toFlowable(BackpressureStrategy.DROP)
                .filter(ev -> ev.getID() == WindowEvent.WINDOW_OPENED)
                .doOnNext(this::handleWindowOpened)
                .subscribe();
        layHorizontaly(frame, radarFrame);
    }

    /**
     * Returns the agent from configuration files
     */
    private StateMachineAgent createAgent() {
        RobotApi robot = fromConfig(args.getString("robot"), new Object[0], new Class[0]);
        RobotControllerApi controller = fromConfig(args.getString("controller"), new Object[]{robot}, new Class[]{RobotApi.class});
        return fromConfig(args.getString("agent"), new Object[]{controller}, new Class[]{RobotControllerApi.class});
    }

    /**
     * Handles the application shutdown
     */
    private void handleShutdown() {
        frame.dispose();
        radarFrame.dispose();
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
        if (robotElapsed > sessionDuration
                || !frame.isVisible()
                || !radarFrame.isVisible()) {
            agent.shutdown();
        }
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
            agent.setOnStepUp(this::handleStepUp);
            agent.readShutdown()
                    .doOnComplete(this::handleShutdown)
                    .subscribe();
            agent.setOnError(err -> logger.atError().setCause(err).log());
            frame.setVisible(true);
            radarFrame.setVisible(true);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
    }
}
