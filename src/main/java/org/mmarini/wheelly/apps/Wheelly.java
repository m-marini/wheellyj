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
import org.mmarini.wheelly.agents.Agent;
import org.mmarini.wheelly.agents.KpiCSVSubscriber;
import org.mmarini.wheelly.apis.ObstacleMap;
import org.mmarini.wheelly.apis.RobotApi;
import org.mmarini.wheelly.envs.Environment;
import org.mmarini.wheelly.envs.RadarMapApi;
import org.mmarini.wheelly.envs.Signal;
import org.mmarini.wheelly.swing.EnvironmentFrame;
import org.mmarini.wheelly.swing.Messages;
import org.mmarini.wheelly.swing.RadarFrame;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static java.lang.Math.exp;
import static org.mmarini.yaml.schema.Validator.*;

/**
 * Run a test to check for robot environment with random behavior agent
 */
public class Wheelly {
    public static final float DEFAULT_DISCOUNT = (float) exp(-1 / 29.7);
    private static final Logger logger = LoggerFactory.getLogger(Wheelly.class);
    private static final Validator BASE_CONFIG = objectPropertiesRequired(Map.of(
            "version", string(values("0.2")),
            "active", string(),
            "configurations", object()
    ), List.of("version", "active", "configurations"));


    /**
     * Creates kpis process
     *
     * @param agent  the agent
     * @param file   the path of kpis
     * @param labels the key labels to filter
     */
    private static void createKpis(Agent agent, File file, String labels) {
        KpiCSVSubscriber sub = labels.length() != 0
                ? KpiCSVSubscriber.create(file, labels.split(","))
                : KpiCSVSubscriber.create(file);
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
        parser.addArgument("-r", "--robot")
                .setDefault("robot.yml")
                .help("specify robot yaml configuration file");
        parser.addArgument("-e", "--env")
                .setDefault("env.yml")
                .help("specify environment yaml configuration file");
        parser.addArgument("-a", "--agent")
                .setDefault("agent.yml")
                .help("specify agent yaml configuration file");
        parser.addArgument("-k", "--kpis")
                .setDefault("")
                .help("specify kpis path");
        parser.addArgument("-l", "--labels")
                .setDefault("")
                .help("specify kpi labels comma separated");
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
     * @param args command line arguments
     */
    public static void main(String[] args) {
        new Wheelly().start(args);
    }

    protected final EnvironmentFrame frame;
    protected Namespace args;

    /**
     *
     */
    public Wheelly() {
        frame = new EnvironmentFrame(Messages.getString("Wheelly.title"));
        frame.onStart(this::process);
    }

    /**
     * Returns the agent
     *
     * @param env the environment
     */
    protected Agent createAgent(Environment env) {
        return fromConfig(args.getString("agent"), new Object[]{env}, new Class[]{Environment.class});
    }

    /**
     * Returns the environment
     *
     * @param robot the robot api
     */
    protected Environment createEnvironment(RobotApi robot) {
        return fromConfig(args.getString("env"), new Object[]{robot}, new Class[]{RobotApi.class});
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
            try (Environment env = createEnvironment(robot)) {
                logger.info("Creating agent");
                try (Agent agent = createAgent(env)) {
                    RadarFrame radarFrame = null;
                    if (env instanceof RadarMapApi) {
                        radarFrame = new RadarFrame();
                    }
                    long sessionDuration = args.getLong("time");
                    logger.info("Starting session ...");
                    logger.info("Session are running for {} sec...", sessionDuration);
                    sessionDuration *= 1000;
                    String kpis = args.getString("kpis");
                    if (kpis.length() != 0) {
                        createKpis(agent, new File(kpis), args.getString("labels"));
                    }
                    long start = System.currentTimeMillis();
                    float avgRewards = 0;
                    Map<String, Signal> state = env.reset();
                    frame.setObstacleMap(robot.getObstaclesMap()
                            .map(ObstacleMap::getPoints)
                            .orElse(null));
                    frame.setRobot(robot);
                    for (boolean running = true; running; ) {
                        Map<String, Signal> actions = agent.act(state);
                        Environment.ExecutionResult result = env.execute(actions);
                        agent.observe(result);
                        float reward = result.getReward();
                        avgRewards = DEFAULT_DISCOUNT * (avgRewards - reward) + reward;
                        frame.setRobot(robot);
                        frame.setReward(avgRewards);
                        frame.setTimeRatio((float) robot.getElapsed() / (System.currentTimeMillis() - start));
                        if (radarFrame != null) {
                            radarFrame.setRadar(((RadarMapApi) env).getRadarMap());
                        }
                        running = robot.getElapsed() <= sessionDuration &&
                                frame.isVisible();
                    }
                    if (radarFrame != null) {
                        radarFrame.dispose();
                    }
                    logger.info("Cleaning up ...");
                }
            }
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
