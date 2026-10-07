/*
 * Copyright (c) 2022-2026 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
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

package org.mmarini.wheelly.apps;

import com.fasterxml.jackson.databind.JsonNode;
import hu.akarnokd.rxjava3.swing.SwingObservable;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
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
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.swing.BaseShape.PATH_COLOR;
import static org.mmarini.wheelly.swing.Utils.*;

/**
 * Runs an execution test session to evaluate the robot environment using a
 * finite state-machine or random behaviour agent interaction model.
 */
public class RobotExecutor {
    /**
     * The configuration target schema resource path URL utilised for validating YAML files.
     */
    public static final String EXECUTOR_SCHEMA_YML = "https://mmarini.org/wheelly/executor-schema-2.0";
    private static final Logger logger = LoggerFactory.getLogger(RobotExecutor.class);

    /**
     * Returns the command line arguments parser configured with application flags.
     *
     * @return the initialised {@link ArgumentParser} instance
     */
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
                .help("specify silent shuttingDown (no window messages)");
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
     * Application entry point.
     *
     * @param args the command line arguments array
     */
    public static void main(String[] args) {
        ArgumentParser parser = createParser();
        try {
            new RobotExecutor(parser.parseArgs(args)).run();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        } catch (Throwable e) {
            logger.atError().setCause(e).log("Error running application");
            System.exit(1);
        }
    }

    /**
     * The graphical panel rendering digital representations of obstacles and sensor sweeps.
     */
    private final EnvironmentPanel envPanel;

    /**
     * The graphical panel representing spatial mapping clusters over a coordinate grid matrix.
     */
    private final GridPanel gridPanel;

    /**
     * The telemetry data metrics tracker computing mean execution delays based on internal robot time clocks.
     */
    private final DoubleReducedValue reactionRobotTime;

    /**
     * The telemetry data metrics tracker computing mean execution delays based on absolute wall-clock time.
     */
    private final DoubleReducedValue reactionRealTime;

    /**
     * The graphical logging display dedicated to low-level communication packet diagnostic streams.
     */
    private final ComMonitor comMonitor;

    /**
     * The graphical panel displaying multi-parameter telemetry matrices.
     */
    private final SensorMonitor sensorMonitor;

    /**
     * The visual dashboard window monitoring state-machine active nodes and context evaluations.
     */
    private final StateEngineMonitor engineMonitor;

    /**
     * The parsed command line namespace context options mapping operational flags.
     */
    private final Namespace args;

    /**
     * The main window graphical toolbar organising manual action buttons.
     */
    private final WheellyToolBar toolBar;

    /**
     * The atomic thread-safe flag locking closing procedures during system de-allocation sweeps.
     */
    private final AtomicBoolean shuttingDown;

    /**
     * The atomic thread-safe flag indicating whether the background evaluation processing loop is active.
     */
    private final AtomicBoolean active;

    /**
     * The interface connection channel abstraction bound to either physical hardware or simulated instances.
     */
    private RobotApi robot;

    /**
     * The wall-clock epoch timestamp marking the exact start moment of the current operational session.
     */
    private long start;

    /**
     * The targeted session duration upper boundary limit transformed into milliseconds (ms).
     */
    private long sessionDuration;

    /**
     * The primary decision agent instance handling state-machine transitions and control logic.
     */
    private StateMachineAgent agent;

    /**
     * The baseline internal timeline reference coordinate marker tracked directly from the robot.
     */
    private long robotStartTimestamp;

    /**
     * The chronological log tracking the previous evaluated execution step in robot time.
     */
    private long prevRobotStep;

    /**
     * The chronological log tracking the previous evaluated execution step in real wall-clock time.
     */
    private long prevRealStep;

    /**
     * The collection registry holding all instantiated graphical window frames.
     */
    private List<JFrame> allFrames;

    /**
     * The supervisor engine managing data routing pipelines across connected actuators and modules.
     */
    private RobotControllerApi controller;

    /**
     * The sensory integration matrix model structuring dynamic safe navigation cells.
     */
    private WorldModeller modeller;

    /**
     * The serialization file writer handling binary text dumps of inference matrices and models evaluation metrics.
     */
    private InferenceFileWriter dumpFile;

    /**
     * Initialises a new {@link RobotExecutor} instance, allocating visual panels, tracking structures,
     * and default state flags.
     *
     * @param args the parsed command line configuration parameters namespace options
     */
    public RobotExecutor(Namespace args) {
        this.args = requireNonNull(args);
        this.envPanel = new EnvironmentPanel();
        this.gridPanel = new GridPanel();
        this.comMonitor = new ComMonitor();
        this.toolBar = new WheellyToolBar();
        this.engineMonitor = new StateEngineMonitor();
        this.reactionRobotTime = DoubleReducedValue.mean();
        this.reactionRealTime = DoubleReducedValue.mean();
        this.robotStartTimestamp = -1;
        this.prevRobotStep = -1;
        this.prevRealStep = -1;
        this.sensorMonitor = new SensorMonitor();
        this.shuttingDown = new AtomicBoolean(false);
        this.active = new AtomicBoolean(true);
        toolBar.resetButton().setEnabled(false);
        toolBar.learningButton().setEnabled(false);
    }

    /**
     * Initialises the operational context components by parsing configurations, loading schemas,
     * connecting the robot api layers, and linking the state machine decision agent pipelines.
     *
     * @throws Throwable if errors occur during configuration parsing, verification validations, or initialization sequences
     */
    void createContext() throws Throwable {
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
        modeller.connect(this::onInferenceProcess);
        agent.modeller(modeller);

        this.sessionDuration = this.args.getLong("time") * 1000;
        logger.atInfo().setMessage("Session will be running for {} sec...").addArgument(sessionDuration).log();

        String dumpFile = this.args.getString("dump");
        if (dumpFile != null) {
            File file = new File(dumpFile);
            file.delete();
            this.dumpFile = InferenceFileWriter.fromFile(file);
            logger.atInfo().log("Writing dump {}", file);
        }
        if (!(this.robot instanceof SimRobot)) {
            toolBar.relocateButton().setEnabled(false);
        }
    }

    /**
     * Initialises the reactive data streams and event flows for the execution session,
     * binding toolbar action listeners, mapping UI updates to the Swing Event Dispatch Thread (EDT),
     * and establishing robot-specific logging subscriptions.
     */
    private void createFlows() {
        toolBar.playButton().addActionListener(this::onStartButton);
        toolBar.pauseButton().addActionListener(this::onStopButton);
        toolBar.clearMapButton().addActionListener(this::onClearMapButton);
        toolBar.relocateButton().addActionListener(this::onRelocateButton);
        switch (robot) {
            case SimRobot sim:
                sim.readObstacleMap()
                        .observeOn(hu.akarnokd.rxjava3.swing.SwingSchedulers.edt())
                        .subscribe(this::onObstacleMap);
                break;
            case MqttRobot mqttRobot:
                comMonitor.addRobot(mqttRobot);
                break;
            case null, default:
                break;
        }
        controller.readShutdown()
                .subscribe(this::onControllerShutdown);
        controller.readErrors()
                .observeOn(hu.akarnokd.rxjava3.swing.SwingSchedulers.edt())
                .subscribe(err -> {
                    comMonitor.onError(err);
                    logger.atError().setCause(err).log("Controller error");
                });
        controller.readControllerStatus()
                .observeOn(hu.akarnokd.rxjava3.swing.SwingSchedulers.edt())
                .map(ControllerStatusMapper::map)
                .subscribe(this::onControllerStatus);
        controller.addOnRobotStatus(s ->
                SwingUtilities.invokeLater(() -> {
                    envPanel.robotStatus(s);
                }));
        agent.readState()
                .observeOn(hu.akarnokd.rxjava3.swing.SwingSchedulers.edt())
                .subscribe(this::onState);
        agent.readStepUp()
                .observeOn(hu.akarnokd.rxjava3.swing.SwingSchedulers.edt())
                .subscribe(this::onStepUp);
        agent.readTargets()
                .observeOn(hu.akarnokd.rxjava3.swing.SwingSchedulers.edt())
                .subscribe(t ->
                        envPanel.target(t.orElse(null)));

        agent.readPath()
                .observeOn(hu.akarnokd.rxjava3.swing.SwingSchedulers.edt())
                .subscribe(this::onPath);
        agent.readTriggers()
                .observeOn(hu.akarnokd.rxjava3.swing.SwingSchedulers.edt())
                .subscribe(this::onTrigger);
        modeller.addOnInference(t ->
                SwingUtilities.invokeLater(() ->
                        onInference(t)));
    }

    /**
     * Initialises and structures individual graphical window frames for multi-window
     * layout configurations, mapping panels across isolated window instances.
     */
    private void createMultiFrames() {
        JFrame frame = createFrame(Messages.getString("RobotExecutor.title"), new JScrollPane(envPanel));
        frame.getContentPane().add(toolBar, BorderLayout.NORTH);
        this.allFrames = List.of(
                frame,
                engineMonitor.createFrame(),
                createFixFrame(Messages.getString("Radar.title"), gridPanel),
                sensorMonitor.createFrame(),
                comMonitor.createFrame()
        );
    }

    /**
     * Initialises and structures a single layout tabbed window frame configuration,
     * grouping panels under independent tab views.
     */
    private void createSingleFrames() {
        JTabbedPane panel = new JTabbedPane();
        panel.addTab(Messages.getString("RobotExecutor.tabPanel.envMap"), new JScrollPane(envPanel));
        panel.addTab(Messages.getString("RobotExecutor.tabPanel.gridMap"), new JScrollPane(gridPanel));
        panel.addTab(Messages.getString("RobotExecutor.tabPanel.engine"), new JScrollPane(engineMonitor));
        panel.addTab(Messages.getString("RobotExecutor.tabPanel.sensor"), new JScrollPane(sensorMonitor));
        panel.addTab(Messages.getString("RobotExecutor.tabPanel.com"), new JScrollPane(comMonitor));
        JFrame frame = createFrame(Messages.getString("RobotExecutor.title"), panel);
        allFrames = List.of(frame);
        frame.getContentPane().add(toolBar, BorderLayout.NORTH);
    }

    /**
     * Initialises the complete user interface canvas layout based on command-line flags,
     * registers reactive frame closing listeners, and anchors window bounds horizontally.
     */
    private void initUI() {
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
     * Handles the clear map action selection events by resetting grid cell radar memories.
     *
     * @param actionEvent the action event triggered by the clear map component
     */
    private void onClearMapButton(ActionEvent actionEvent) {
        modeller.clearRadarMap();
    }

    /**
     * Handles the controller shutdown notification by closing open inference file logs
     * and releasing graphical user interface resources.
     */
    private void onControllerShutdown() {
        if (dumpFile != null) {
            try {
                dumpFile.close();
                logger.atInfo().log("Closed dump file {}", args.getString("dump"));
            } catch (IOException e) {
                logger.atError().setCause(e).log("Error shuttingDown dump file {}", args.getString("dump"));
            }
        }
        allFrames.forEach(JFrame::dispose);
        if (!args.getBoolean("silent")) {
            JOptionPane.showMessageDialog(null,
                    "Completed", "Information", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Handles controller status notifications by updating multi-parameter displays
     * across communication and sensor monitor components.
     *
     * @param status the raw textual operational state description emitted by the controller
     */
    private void onControllerStatus(String status) {
        sensorMonitor.onControllerStatus(status);
        comMonitor.onControllerStatus(status);
    }

    /**
     * Handles inference cycle evaluation logs by serialising active environment world models
     * and composite robot commands into the designated external dump file.
     *
     * @param result the tuple container pairing the active {@link WorldModel} with the processed {@link RobotCommand}
     */
    private void onInference(Tuple2<WorldModel, RobotCommand> result) {
        if (dumpFile != null) {
            WorldModel world = result._1;
            RobotCommand commands = result._2;
            try {
                dumpFile.write(world, commands);
            } catch (IOException e) {
                logger.atError().setCause(e).log("Error writing dump file {}", args.getString("dump"));
                try {
                    dumpFile.close();
                } catch (IOException ignored) {
                }
                dumpFile = null;
            }
        }
    }

    /**
     * Dispatches processed world state properties to the state machine decision agent,
     * returning a baseline halt instruction if active operation mode is paused.
     *
     * @param state the active structured {@link WorldModel} tracking telemetry metrics
     * @return the newly formulated composite {@link RobotCommand} order payload
     */
    private RobotCommand onInferenceProcess(WorldModel state) {
        return active.get()
                ? agent.onInference(state)
                : RobotCommand.halt();
    }

    /**
     * Handles real-time environmental obstacle layer adjustments by synchronising
     * visual elements over the drawing panel canvas.
     *
     * @param map the new global active obstacle field layout collection snapshot
     */
    private void onObstacleMap(Collection<Obstacle> map) {
        envPanel.obstacles(map);
    }

    /**
     * Handles agent calculated route path changes by re-rendering path vectors
     * across the environment canvas layout.
     *
     * @param path the ordered list of spatial coordinate points mapping the route target
     */
    private void onPath(List<Point2D> path) {
        envPanel.path(PATH_COLOR, path.toArray(Point2D[]::new));
    }

    /**
     * Handles relocate command action events by triggering random safe spatial coordinate
     * displacement updates across simulated robot environments.
     *
     * @param actionEvent the action event triggered by the relocate toolbar button component
     */
    private void onRelocateButton(ActionEvent actionEvent) {
        if (this.robot instanceof SimRobot simRobot) {
            simRobot.safeRelocateRandom();
        }
    }

    /**
     * Handles active execution session play button selection events by unblocking agent
     * state processing pipelines and modifying toolbar button states.
     *
     * @param actionEvent the action event triggered by the toolbar play button component
     */
    private void onStartButton(ActionEvent actionEvent) {
        active.set(true);
        toolBar.pauseButton().setEnabled(true);
        toolBar.playButton().setEnabled(false);
    }

    /**
     * Handles active agent state tree path changes by updating the state engine logging monitor.
     *
     * @param state the newly activated {@link StateNode} metadata profile
     */
    private void onState(StateNode state) {
        engineMonitor.addState(state);
    }

    /**
     * Handles agent state validation and step metrics increments, parsing localised sensor arrays,
     * rendering markers on the visual grid layout, calculating execution latency performance parameters,
     * and automatically initiating a system shutdown sequence when the session duration threshold is exceeded.
     *
     * @param ctx the active {@link ProcessorContextApi} context instance tracking variables maps
     */
    private void onStepUp(ProcessorContextApi ctx) {
        WorldModel worldModel = ctx.worldModel();
        RobotStatus status = worldModel.robotStatus();
        if (robotStartTimestamp < 0) {
            robotStartTimestamp = status.robotTime();
        }
        sensorMonitor.onStatus(status);
        envPanel.robotStatus(status);
        envPanel.radarMap(worldModel.radarMap());
        envPanel.markers(worldModel.markers().values());
        long robotClock = status.robotTime();
        long robotElapsed = robotClock - robotStartTimestamp;
        envPanel.setTimeRatio((double) robotElapsed / (System.currentTimeMillis() - start));
        long clock = System.currentTimeMillis();
        if (prevRobotStep >= 0) {
            envPanel.setReactionRealTime(reactionRealTime.add(clock - prevRealStep).value() * 1e-3);
            envPanel.setReactionRobotTime(reactionRobotTime.add(robotClock - prevRobotStep).value() * 1e-3);
        }
        prevRobotStep = robotClock;
        this.prevRealStep = clock;

        Map<String, LabelMarker> markers = worldModel.markers();
        GridMap map = worldModel.gridMap();
        Complex robotDir = status.direction();
        /*
         * Transforms the marker locations to grid map coordinates
         */
        AffineTransform tr = AffineTransform.getRotateInstance(map.direction().toRad());
        Point2D center = map.center();
        tr.translate(-center.getX(), -center.getY());
        List<Point2D> mks = markers.values().stream()
                .map(p -> tr.transform(p.location(), null))
                .toList();

        gridPanel.setGridMap(map);
        gridPanel.setRobotDirection(robotDir.sub(map.direction()));
        gridPanel.setMarkers(mks);

        if (robotElapsed > sessionDuration) {
            shutdown();
        }
    }

    /**
     * Handles the pause or stop button action selection events by blocking agent
     * state inference loops and updating toolbar toggle button states.
     *
     * @param actionEvent the action event triggered by the toolbar pause component
     */
    private void onStopButton(ActionEvent actionEvent) {
        active.set(false);
        toolBar.pauseButton().setEnabled(false);
        toolBar.playButton().setEnabled(true);
    }

    /**
     * Handles state-machine event trigger signals by registering the corresponding
     * text details inside the engine monitor interface panel.
     *
     * @param trigger the identifier text describing the triggered state transition event
     */
    private void onTrigger(String trigger) {
        engineMonitor.addTrigger(trigger);
    }

    /**
     * Handles explicit visual frame closing events by executing a global
     * application shutdown sequence.
     *
     * @param windowEvent the window event triggered by closing any application frame container
     */
    private void onWindowClosing(WindowEvent windowEvent) {
        shutdown();
    }

    /**
     * Executes the operational loop session by creating the context parameters, initialising reactive
     * data flows, rendering the graphical user interface elements, writing logs file headers,
     * and starting the controller execution loop.
     *
     * @throws Throwable if errors occur during file parsing, validations, or hardware interface initialisation
     */
    private void run() throws Throwable {
        createContext();
        createFlows();
        initUI();
        toolBar.playButton().setEnabled(false);
        if (dumpFile != null) {
            dumpFile.writeHeader(modeller.worldModelSpec(), modeller.radarModeller().topology());
        }

        // Configure the user interface
        logger.atInfo().log("Starting session ...");
        this.start = System.currentTimeMillis();
        allFrames.reversed().forEach(f -> f.setVisible(true));
        controller.start();
    }

    /**
     * Triggers a comprehensive transactional shutdown sequence across active interface controllers
     * if the system has not already initiated resource teardown procedures.
     */
    private void shutdown() {
        if (!shuttingDown.getAndSet(true)) {
            controller.shutdown();
        }
    }
}
