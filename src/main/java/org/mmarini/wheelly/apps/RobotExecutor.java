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
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jetbrains.annotations.NotNull;
import org.mmarini.Tuple2;
import org.mmarini.swing.Messages;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.engines.ProcessorContextApi;
import org.mmarini.wheelly.engines.StateMachineAgent;
import org.mmarini.wheelly.engines.StateNode;
import org.mmarini.wheelly.mqtt.MqttRobot;
import org.mmarini.wheelly.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.swing.BaseShape.PATH_COLOR;
import static org.mmarini.wheelly.swing.Utils.*;

/**
 * Run a test to check for robot environment with random behaviour agent
 */
public class RobotExecutor {
    public static final String EXECUTOR_SCHEMA_YML = "https://mmarini.org/wheelly/executor-schema-2.0";
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
        parser.addArgument("-c", "--config")
                .setDefault("executor.yml")
                .help("specify yaml configuration file");
        parser.addArgument("-d", "--dump")
                .help("specify inference dump file");
        parser.addArgument("-s", "--silent")
                .action(Arguments.storeTrue())
                .help("specify silent closing (no window messages)");
        parser.addArgument("-t", "--time")
                .setDefault(43200L)
                .type(Long.class)
                .help("specify number of seconds of session duration");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-w", "--windows")
                .action(Arguments.storeTrue())
                .help("use multiple windows");
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
        } catch (IOException e) {
            logger.atError().setCause(e).log("Error running application");
            System.exit(1);
        }
    }

    private final EnvironmentPanel envPanel;
    private final PolarPanel polarPanel;
    private final DoubleReducedValue reactionRobotTime;
    private final DoubleReducedValue reactionRealTime;
    private final ComMonitor comMonitor;
    private final SensorMonitor sensorMonitor;
    private final StateEngineMonitor engineMonitor;
    private final Namespace args;
    private RobotApi robot;
    private long start;
    private long sessionDuration;
    private StateMachineAgent agent;
    private long robotStartTimestamp;
    private long prevRobotStep;
    private long prevRealStep;
    private List<JFrame> allFrames;
    private RobotControllerApi controller;
    private WorldModeller modeller;
    private InferenceFileWriter dumpFile;

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
        this.reactionRobotTime = DoubleReducedValue.mean();
        this.reactionRealTime = DoubleReducedValue.mean();
        this.robotStartTimestamp = -1;
        this.prevRobotStep = -1;
        this.prevRealStep = -1;
        this.sensorMonitor = new SensorMonitor();
    }

    /**
     * Creates the context.
     * It consists of the robot, the controller, the modeller and the state machine agent
     */
    void createContext() throws IOException {
        File confFile = new File(this.args.getString("config"));
        JsonNode config = org.mmarini.yaml.Utils.fromFile(confFile);
        WheellyJsonSchemas.instance().validateOrThrow(config, EXECUTOR_SCHEMA_YML);

        logger.atInfo().log("Creating robot ...");
        this.robot = AppYaml.robotFromJson(config);

        logger.atInfo().log("Creating controller ...");
        this.controller = AppYaml.controllerFromJson(config);
        controller.connectRobot(robot);

        logger.atInfo().log("Creating world modeller ...");
        this.modeller = AppYaml.modellerFromJson(config);
        modeller.setRobotSpec(robot.robotSpec());
        modeller.connectController(controller);

        logger.atInfo().log("Creating agent ...");
        this.agent = StateMachineAgent.fromFile(
                new File(config.path("agent").asText()));
        modeller.connect(agent);

        this.sessionDuration = this.args.getLong("time") * 1000;
        logger.atInfo().setMessage("Session will be running for {} sec...").addArgument(sessionDuration).log();

        String dumpFile = this.args.getString("dump");
        if (dumpFile != null) {
            File file = new File(dumpFile);
            file.delete();
            this.dumpFile = InferenceFileWriter.fromFile(file);
            logger.atInfo().log("Writing dump {}", file);
        }
    }


    /**
     * Creates the reactive flows
     */
    private void createFlows() {
        switch (robot) {
            case SimRobot sim:
                sim.readObstacleMap()
                        .subscribe(this::onObstacleMap);
                break;
            case MqttRobot mqttRobot:
                comMonitor.addRobot(mqttRobot);
                break;
            case null, default:
                break;
        }
        controller.readShutdown()
                .subscribe(this::onShutdown);
        controller.readErrors()
                .subscribe(err -> {
                    comMonitor.onError(err);
                    logger.atError().setCause(err).log("Controller error");
                });
        controller.readControllerStatus()
                .map(ControllerStatusMapper::map)
                .subscribe(this::onControllerStatus);
        controller.readCommand()
                .subscribe(sensorMonitor::onCommand);
        agent.readState()
                .subscribe(this::onState);
        agent.readStepUp()
                .subscribe(this::onStepUp);
        agent.readTargets()
                .subscribe(t ->
                        envPanel.target(t.orElse(null)));

        agent.readPath()
                .subscribe(this::onPath);
        agent.readTriggers()
                .subscribe(this::onTrigger);

        modeller.readInference()
                .subscribe(this::onInference);
    }

    /**
     * Handles the inference result
     *
     * @param result the inference result
     */
    private void onInference(Tuple2<WorldModel, RobotCommands> result) {
        if (dumpFile != null) {
            WorldModel world = result._1;
            RobotCommands commands = result._2;
            try {
                dumpFile.write(world, commands);
            } catch (IOException e) {
                logger.atError().setCause(e).log("Error writing dump file {}", args.getString("dump"));
                try {
                    dumpFile.close();
                } catch (IOException ex) {
                }
                dumpFile = null;
            }
        }
    }

    /**
     * Creates the multi frames
     */
    private void createMultiFrames() {
        this.allFrames = List.of(
                createFrame(Messages.getString("RobotExecutor.title"), envPanel),
                engineMonitor.createFrame(),
                createFixFrame(Messages.getString("Radar.title"), polarPanel),
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
     * Initializes the user interface
     */
    private void initUI() {
        double radarMaxDistance = robot.robotSpec().maxRadarDistance();
        polarPanel.setRadarMaxDistance(radarMaxDistance);
        envPanel.markerSize((float) modeller.worldModelSpec().markerSize());
        if (args.getBoolean("windows")) {
            createMultiFrames();
        } else {
            createSingleFrames();
        }
        allFrames.forEach(f -> SwingObservable.window(f, SwingObservable.WINDOW_ACTIVE)
                .filter(ev -> ev.getID() == WindowEvent.WINDOW_CLOSING)
                .doOnNext(this::onWindowClosing)
                .subscribe());
        layHorizontally(allFrames);
    }

    /**
     * Handles controller status event
     *
     * @param status the controller status string
     */
    private void onControllerStatus(String status) {
        sensorMonitor.onControllerStatus(status);
        comMonitor.onControllerStatus(status);
    }

    /**
     * Handles the obstacle map changes
     *
     * @param map the obstacle map
     */
    private void onObstacleMap(ObstacleMap map) {
        envPanel.obstacles(map);
    }

    /**
     * Handles the path event
     *
     * @param path the path
     */
    private void onPath(List<Point2D> path) {
        envPanel.path(PATH_COLOR, path.toArray(Point2D[]::new));
    }

    /**
     * Handles the application shutdown
     */
    private void onShutdown() {
        allFrames.forEach(JFrame::dispose);
        if (dumpFile != null) {
            try {
                dumpFile.close();
                logger.atInfo().log("Closed dump file {}", args.getString("dump"));
            } catch (IOException e) {
                logger.atError().setCause(e).log("Error closing dump file {}", args.getString("dump"));
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
    private void onState(StateNode state) {
        engineMonitor.addState(state);
    }

    /**
     * Handles the step-up of agent
     *
     * @param ctx the context
     */
    private void onStepUp(ProcessorContextApi ctx) {
        WorldModel worldModel = ctx.worldModel();
        RobotStatus status = worldModel.robotStatus();
        if (robotStartTimestamp < 0) {
            robotStartTimestamp = status.simulationTime();
        }
        sensorMonitor.onStatus(status);
        envPanel.robotStatus(status);
        envPanel.radarMap(worldModel.radarMap());
        envPanel.markers(worldModel.markers().values());
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

        polarPanel.setPolarMap(worldModel.polarMap(), worldModel.markers().values());
        if (robotElapsed > sessionDuration) {
            controller.shutdown();
        }
    }

    /**
     * Handles the trigger event
     *
     * @param trigger the trigger
     */
    private void onTrigger(String trigger) {
        engineMonitor.addTrigger(trigger);
    }

    /**
     * Handles window closing
     *
     * @param windowEvent the window event
     */
    private void onWindowClosing(WindowEvent windowEvent) {
        controller.shutdown();
    }

    /**
     * Starts the executor.
     * <p>
     * Creates the agent
     * Initializes the UI components
     * Opens the application frames (environment and radar)
     */
    private void run() throws IOException {
        createContext();
        createFlows();
        initUI();
        if (dumpFile != null) {
            dumpFile.writeHeader(modeller.worldModelSpec(), modeller.radarModeller().topology());
        }

        // Configure the user interface
        logger.atInfo().log("Starting session ...");
        this.start = System.currentTimeMillis();
        allFrames.reversed().forEach(f -> f.setVisible(true));
        controller.start();
    }
}
