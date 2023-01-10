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
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jetbrains.annotations.NotNull;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.engines.StateMachineAgent;
import org.mmarini.wheelly.swing.EnvironmentFrame;
import org.mmarini.wheelly.swing.Messages;
import org.mmarini.wheelly.swing.PolarPanel;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mmarini.yaml.schema.Validator.*;

/**
 * Run a test to check for robot environment with random behavior agent
 */
public class RobotExecutor {
    private static final Logger logger = LoggerFactory.getLogger(RobotExecutor.class);
    private static final Validator BASE_CONFIG = objectPropertiesRequired(Map.of(
            "version", string(values("0.4")),
            "active", string(),
            "configurations", object()
    ), List.of("version", "active", "configurations"));

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

    static JFrame createRadarFrame(JComponent panel) {
        JFrame frame = new JFrame("Radar");
        frame.setSize(400, 400);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setResizable(false);
        Container content = frame.getContentPane();
        content.setLayout(new BorderLayout());
        content.add(panel, BorderLayout.CENTER);
        frame.setVisible(true);
        return frame;
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
     * @param args command line arguments
     */
    public static void main(String[] args) {
        new RobotExecutor().start(args);
    }

    protected final EnvironmentFrame frame;
    protected Namespace args;

    /**
     *
     */
    public RobotExecutor() {
        frame = new EnvironmentFrame(Messages.getString("Wheelly.title"));
        frame.onStart(this::process);
    }

    private StateMachineAgent createAgent(RobotApi robot) {
        return fromConfig(args.getString("agent"), new Object[]{robot}, new Class[]{RobotApi.class});
    }

    /**
     * Returns the robot api
     */
    protected RobotApi createRobot() {
        return fromConfig(args.getString("robot"), new Object[0], new Class[0]);
    }

    private void process() {
        try (INDArray ignored = Nd4j.zeros(1)) {
        }
        logger.info("Creating api");
        try (RobotApi robot = createRobot()) {
            logger.info("Creating environment");
            JFrame radarFrame = null;
            try (StateMachineAgent agent = createAgent(robot)) {
//                RadarPanel radarPanel = new RadarPanel();
                PolarPanel polarPanel = new PolarPanel();
//                radarFrame = createRadarFrame(radarPanel);
                radarFrame = createRadarFrame(polarPanel);
                float radarMaxDistance = agent.getMaxRadarDistance();
                polarPanel.setRadarMaxDistance(radarMaxDistance);
                long sessionDuration = args.getLong("time");
                logger.info("Starting session ...");
                logger.info("Session are running for {} sec...", sessionDuration);
                sessionDuration *= 1000;
                long start = System.currentTimeMillis();
                float avgRewards = 0;
                agent.init();
                RobotStatus status = robot.getStatus();
                if (robot instanceof SimRobot) {
                    Optional<ObstacleMap> obstaclesMap = ((SimRobot) robot).getObstaclesMap();
                    obstaclesMap.map(ObstacleMap::getPoints)
                            .ifPresent(frame::setObstacleMap);
                    obstaclesMap.map(ObstacleMap::getTopology)
                            .map(GridTopology::getGridSize)
                            .ifPresent(frame::setObstacleSize);
                }
                frame.setRobotStatus(status);
                for (boolean running = true; running; ) {
                    agent.step();
                    status = robot.getStatus();
                    frame.setRobotStatus(status);
                    frame.setReward(avgRewards);
                    frame.setTimeRatio((float) status.getElapsed() / (System.currentTimeMillis() - start));
                    //                  radarPanel.setRadarMap(agent.getRadarMap());
                    polarPanel.setPolarMap(agent.getPolarMap());
                    running = status.getElapsed() <= sessionDuration &&
                            frame.isVisible();
                }
            } finally {
                if (radarFrame != null) {
                    radarFrame.dispose();
                }
            }
            logger.info("Cleaning up ...");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        logger.info("Cleaned up.");
    }

    protected void start(String[] args) {
        ArgumentParser parser = createParser();
        try {
            this.args = parser.parseArgs(args);
            frame.setSilent(this.args.getBoolean("silent"));
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
        frame.start();
    }
}
