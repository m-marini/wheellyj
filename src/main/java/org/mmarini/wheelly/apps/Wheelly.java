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
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mmarini.Tuple2;
import org.mmarini.rl.agents.*;
import org.mmarini.rl.envs.WithSignalsSpec;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.swing.Messages;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.envs.EnvironmentApi;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.wheelly.mqtt.MqttRobot;
import org.mmarini.wheelly.swing.*;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.factory.Nd4j;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static io.reactivex.rxjava3.core.Flowable.interval;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.swing.Utils.*;
import static org.mmarini.yaml.Utils.fromFile;

/**
 * Run a test to check for robot environment with random behaviour agent
 */
public class Wheelly {
    public static final String WHEELLY_SCHEMA_YML = "https://mmarini.org/wheelly/wheelly-schema-3.0";
    public static final int LAYOUT_INTERVAL = 300;
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
        parser.addArgument("-a", "--alternate")
                .action(Arguments.storeTrue())
                .help("specify alternate act/training");
        parser.addArgument("-c", "--config")
                .setDefault("wheelly.yml")
                .help("specify yaml configuration path");
        parser.addArgument("-i", "--inference")
                .help("specify inference output path");
        parser.addArgument("-s", "--silent")
                .action(Arguments.storeTrue())
                .help("specify silent closing (no window messages)");
        parser.addArgument("-t", "--localTime")
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
     * @param args command line arguments
     */
    public static void main(String[] args) {
        ArgumentParser parser = createParser();
        try {
            new Wheelly(parser.parseArgs(args)).run();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        } catch (IOException e) {
            logger.atError().setCause(e).log("IO exception");
            System.exit(1);
        }
    }

    protected final EnvironmentPanel envPanel;
    private final DoubleReducedValue reactionRobotTime;
    private final DoubleReducedValue reactionRealTime;
    private final ComMonitor comMonitor;
    private final SensorMonitor sensorMonitor;
    private final KpisPanel kpisPanel;
    private final CompletableSubject completion;
    private final LearnPanel learnPanel;
    private final Namespace args;
    private final JButton stopButton;
    private final JButton startButton;
    private final JButton relocateButton;
    private final InferenceConnector inferenceMediator1;
    private JFrame kpisFrame;
    private long robotStartTimestamp;
    private Long sessionDuration;
    private PolarPanel polarPanel;
    private GridPanel gridPanel;
    private long prevRobotStep;
    private long prevStep;
    private List<JFrame> allFrames;
    private boolean active;
    private RobotControllerApi controller;
    private WorldModeller worldModeller;
    private EnvironmentApi environment;
    private RobotApi robot;
    private Agent agent;
    private JFrame frame;
    private InferenceWriter modelDumper;
    private OnLineAgent mediatorAgent;

    /**
     * Creates the server reinforcement learning engine server
     *
     * @param args the parsed argument
     */
    public Wheelly(Namespace args) {
        this.args = requireNonNull(args);
        this.envPanel = new EnvironmentPanel();
        this.kpisPanel = new KpisPanel();
        this.comMonitor = new ComMonitor();
        this.learnPanel = new LearnPanel();
        this.sensorMonitor = new SensorMonitor();
        this.robotStartTimestamp = -1;
        this.reactionRobotTime = DoubleReducedValue.mean();
        this.reactionRealTime = DoubleReducedValue.mean();
        this.prevRobotStep = -1;
        this.prevStep = -1;
        this.completion = CompletableSubject.create();
        this.relocateButton = SwingUtils.getInstance().initButton(new JButton(), "Wheelly.relocateButton");
        this.stopButton = SwingUtils.getInstance().initButton(new JButton(), "Wheelly.stopButton");
        this.startButton = SwingUtils.getInstance().initButton(new JButton(), "Wheelly.runButton");

        this.inferenceMediator1 = Wheelly.this::onInferenceProcess;
        comMonitor.setPrintTimestamp(true);
    }

    /**
     * Creates the context from configuration
     *
     * @param config the configuration
     * @throws IOException in case of error
     */
    private void createContext(JsonNode config) throws IOException {
        logger.atInfo().log("Creating robot ...");
        this.robot = AppYaml.robotFromJson(config);

        logger.atInfo().log("Creating controller ...");
        this.controller = AppYaml.controllerFromJson(config);
        controller.connectRobot(robot);

        logger.atInfo().log("Creating world modeller ...");
        this.worldModeller = AppYaml.modellerFromJson(config);
        worldModeller.setRobotSpec(robot.robotSpec());
        worldModeller.connectController(controller);
        worldModeller.connect(inferenceMediator1);

        logger.atInfo().log("Creating RL environment ...");
        this.environment = AppYaml.envFromJson(config);
        environment.connect(worldModeller);

        logger.atInfo().log("Create reward function ...");
        RewardFunction rewardFunc = AppYaml.rewardFromJson(config);
        environment.setRewardFunc(rewardFunc);

        logger.atInfo().log("Creating agent ...");
        Function<WithSignalsSpec, Agent> agentBuilder = Agent.fromFile(
                new File(Locator.locate("agent").getNode(config).asText()));
        this.agent = agentBuilder.apply(environment);

        if (agent instanceof DLAgent dlAgent) {
            dlAgent.readKpis().observeOn(Schedulers.computation())
                    .subscribe(this::onKpis);
        }

        long savingInterval = Locator.locate("savingInterval").getNode(config).asLong();
        this.mediatorAgent = new OnLineAgent(this.agent, savingInterval)
                .merger((trained, online) ->
                        trained instanceof AgentRL trainedRL && online instanceof AgentRL onlineRL
                                ? trainedRL.eta(onlineRL.eta())
                                .alphas(onlineRL.alphas())
                                : trained);
        environment.connect(mediatorAgent);

        kpisPanel.addActionColumns(environment.actionSpec()
                .keySet().stream().sorted().toArray(String[]::new));

        // Creates the model dumper
        Optional.ofNullable(this.args.getString("inference")).ifPresent(file -> {
            try {
                this.modelDumper = InferenceFileWriter.fromFile(
                                new File(this.args.getString("inference")))
                        .writeHeader(worldModeller.worldModelSpec(), worldModeller.radarModeller().topology());
            } catch (IOException e) {
                logger.atError().setCause(e).log("Error dumping inference to {}", file);
            }
        });
    }

    /**
     * Creates the flows of events
     */
    private void createFlows() {
        controller.readRobotStatus().subscribe(this::onStatusReady);
        controller.readCommand().subscribe(sensorMonitor::onCommand);
        controller.readErrors().subscribe(err -> {
            comMonitor.onError(err);
            logger.atError().setCause(err).log();
        });
        controller.readControllerStatus()
                .map(ControllerStatusMapper::map)
                .distinct()
                .subscribe(this::onControllerStatus);
        controller.readShutdown().subscribe(this::onShutdown);
        worldModeller.readInference().subscribe(this::onInference);

        learnPanel.readActionAlphas()
                .subscribe(this::onAlphasChange);
        learnPanel.readEtas()
                .subscribe(this::onEtaChange);
        Observable<WindowEvent>[] windowObs = allFrames.stream()
                .map(f -> SwingObservable.window(f, SwingObservable.WINDOW_ACTIVE))
                .toArray(Observable[]::new);
        Observable.mergeArray(windowObs)
                .filter(ev -> ev.getID() == WindowEvent.WINDOW_CLOSING)
                .subscribe(this::onWindowClosing);

        if (robot instanceof MqttRobot mqttRobot) {
            comMonitor.addRobot(mqttRobot);
        }

        completion.onComplete();
    }

    /**
     * Creates all the frames
     */
    private void createMultiFrames() {
        // Create multiple frame app
        JFrame radarFrame = createFixFrame(Messages.getString("Radar.title"), polarPanel);
        JFrame gridFrame = createFixFrame(Messages.getString("Grid.title"), gridPanel);

        // Create kpis frame
        this.kpisFrame = kpisPanel.createFrame();
        frame = createFrame(Messages.getString("Wheelly.title"), new JScrollPane(envPanel));
        frame.getContentPane().add(createToolBar(), BorderLayout.NORTH);
        SwingObservable.window(frame, SwingObservable.WINDOW_ACTIVE)
                .filter(ev ->
                        ev.getID() == WindowEvent.WINDOW_OPENED)
                .subscribe(this::onWindowOpened);

        JFrame comFrame = comMonitor.createFrame();
        JFrame sensorFrame = sensorMonitor.createFrame();

        // Collects all frames
        allFrames = new ArrayList<>();
        allFrames.add(frame);
        allFrames.add(radarFrame);
        allFrames.add(gridFrame);
        if (kpisFrame != null) {
            allFrames.add(kpisFrame);
        }
        allFrames.add(learnPanel.createFrame());
        allFrames.add(sensorFrame);
        allFrames.add(comFrame);

        // Open all the windows
        JFrame[] frameAry = this.allFrames.toArray(JFrame[]::new);

        layHorizontally(frameAry);

        comFrame.setState(JFrame.ICONIFIED);
        interval(LAYOUT_INTERVAL, TimeUnit.MILLISECONDS)
                .limit(allFrames.size())
                .subscribe(i ->
                        allFrames.get(allFrames.size() - 1 - Math.toIntExact(i))
                                .setVisible(true));
    }

    /**
     * Creates the panels
     */
    private void createPanels() {
        if (mediatorAgent.onlineAgent() instanceof AgentRL agent) {
            learnPanel.setEta(agent.eta());
            learnPanel.setActionAlphas(agent.alphas());
        }

        this.polarPanel = new PolarPanel();
        RobotSpec robotSpec = robot.robotSpec();
        double radarMaxDistance = robotSpec.maxRadarDistance();
        polarPanel.setRadarMaxDistance(radarMaxDistance);
        envPanel.markerSize((float) worldModeller.worldModelSpec().markerSize());
        this.gridPanel = new GridPanel();
    }

    /**
     * Creates the single application frame
     */
    private void createSingleFrame() {
        JTabbedPane panel = new JTabbedPane();
        panel.addTab(Messages.getString("Wheelly.tabPanel.envMap"), new JScrollPane(envPanel));
        panel.addTab(Messages.getString("Wheelly.tabPanel.polarMap"), new JScrollPane(polarPanel));
        panel.addTab(Messages.getString("Wheelly.tabPanel.gridMap"), new JScrollPane(gridPanel));
        panel.addTab(Messages.getString("Wheelly.tabPanel.kpi"), new JScrollPane(kpisPanel));

        JPanel panel1 = new GridLayoutHelper<>(new JPanel())
                .modify("insets,10 center").add(learnPanel)
                .getContainer();
        panel.addTab(Messages.getString("Wheelly.tabPanel.learn"), panel1);
        panel.addTab(Messages.getString("Wheelly.tabPanel.sensor"), new JScrollPane(sensorMonitor));
        panel.addTab(Messages.getString("Wheelly.tabPanel.com"), new JScrollPane(comMonitor));

        // Create single frame app
        this.frame = createFrame(Messages.getString("Wheelly.title"), panel);
        frame.getContentPane().add(createToolBar(), BorderLayout.NORTH);
        SwingObservable.window(frame, SwingObservable.WINDOW_ACTIVE)
                .filter(ev ->
                        ev.getID() == WindowEvent.WINDOW_OPENED)
                .subscribe(this::onWindowOpened);
        center(frame);
        allFrames = List.of(frame);
    }

    /**
     * Returns the toolbar
     */
    private JToolBar createToolBar() {
        JToolBar toolBar = new JToolBar();
        JButton resetButton = SwingUtils.getInstance().initButton(new JButton(), "Wheelly.resetButton");
        JButton clearMapButton = SwingUtils.getInstance().initButton(new JButton(), "Wheelly.clearMapButton");
        toolBar.add(stopButton);
        toolBar.add(startButton);
        toolBar.add(resetButton);
        toolBar.add(clearMapButton);
        toolBar.add(relocateButton);
        stopButton.addActionListener(this::onStopButton);
        startButton.addActionListener(this::onStartButton);
        resetButton.addActionListener(this::onResetButton);
        clearMapButton.addActionListener(this::onClearMapButton);
        relocateButton.addActionListener(this::onRelocateButton);
        return toolBar;
    }

    /**
     * Returns the validated application configuration
     *
     * @throws IOException in case of error
     */
    private JsonNode loadConfig() throws IOException {
        File configFile = new File(this.args.getString("config"));
        JsonNode config = fromFile(configFile);
        WheellyJsonSchemas.instance().validateOrThrow(config, WHEELLY_SCHEMA_YML);
        return config;
    }

    /**
     * Handles alphas changes
     *
     * @param alphas the alpha values
     */
    private void onAlphasChange(Map<String, Float> alphas) {
        mediatorAgent.changeAgent(ag ->
                ag instanceof AgentRL agent
                        ? agent.alphas(alphas)
                        : ag);
    }

    /**
     * Handles the clear map button event
     *
     * @param actionEvent the event
     */
    private void onClearMapButton(ActionEvent actionEvent) {
        worldModeller.clearRadarMap();
    }

    /**
     * Handles the controller status event
     *
     * @param status the controller status text
     */
    private void onControllerStatus(String status) {
        sensorMonitor.onControllerStatus(status);
        comMonitor.onControllerStatus(status);
    }

    /**
     * Handles eta changes
     *
     * @param eta the eta value
     */
    private void onEtaChange(float eta) {
        mediatorAgent.changeAgent(ag ->
                ag instanceof AgentRL agent
                        ? agent.eta(eta)
                        : ag);
    }

    /**
     * Handles the inference event
     *
     * @param inferenceResult the inference result
     */
    private void onInference(Tuple2<WorldModel, RobotCommands> inferenceResult) {
        WorldModel worldModel = inferenceResult._1;
        RobotStatus robotStatus = worldModel.robotStatus();
        Map<String, LabelMarker> markers = worldModel.markers();
        PolarMap polarMap = worldModel.polarMap();
        GridMap map = worldModel.gridMap();

        // Dumps world model
        if (modelDumper != null) {
            try {
                modelDumper.write(worldModel, inferenceResult._2);
            } catch (IOException e) {
                logger.atError().setCause(e).log("Error dumping model");
            }
        }

        long robotClock = robotStatus.simulationTime();
        envPanel.robotStatus(robotStatus);
        sensorMonitor.onStatus(robotStatus);
        envPanel.radarMap(worldModel.radarMap());
        envPanel.markers(markers.values());
        polarPanel.setPolarMap(polarMap, markers.values());
        Complex robotDir = robotStatus.direction();

        gridPanel.setGridMap(map);
        gridPanel.setRobotDirection(robotDir.sub(map.direction()));
        Point2D center = map.center();

        /*
         * Transforms the marker locations to grid map coordinates
         */
        AffineTransform tr = AffineTransform.getRotateInstance(map.direction().toRad());
        tr.translate(-center.getX(), -center.getY());

        List<Point2D> mks = markers.values().stream()
                .map(p -> tr.transform(p.location(), null))
                .toList();
        gridPanel.setMarkers(mks);

        long time = System.currentTimeMillis();
        if (prevRobotStep >= 0) {
            envPanel.setReactionRealTime(reactionRealTime.add(time - prevStep).value() * 1e-3);
            envPanel.setReactionRobotTime(reactionRobotTime.add(robotClock - prevRobotStep).value() * 1e-3);
        }
        prevRobotStep = robotClock;
        prevStep = time;
    }

    private RobotCommands onInferenceProcess(WorldModel state) {
        return active
                ? environment.onInference(state)
                : RobotCommands.haltCommand();
    }

    /**
     * Handles the kpis event
     *
     * @param kpis the kpis
     */
    private void onKpis(TrainingKpis kpis) {
        kpisPanel.print(kpis);
    }

    /**
     * Handles the obstacle map
     *
     * @param map the obstacle map
     */
    private void onObstacleMap(ObstacleMap map) {
        envPanel.obstacles(map);
    }

    /**
     * @param actionEvent the action event
     */
    private void onRelocateButton(ActionEvent actionEvent) {
        if (this.robot instanceof SimRobot simRobot) {
            simRobot.safeRelocateRandom();
        }
    }

    /**
     * Handles reset button
     *
     * @param actionEvent the action event
     */
    private void onResetButton(ActionEvent actionEvent) {
        mediatorAgent.init();
    }

    /**
     * Handles the application shutdown
     */
    private void onShutdown() {
        // Open wait frame
        logger.atInfo().log("Shutdown");
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setString("   Waiting for completion ...   ");
        progressBar.setStringPainted(true);
        Container panel = new GridLayoutHelper<>(new JPanel())
                .modify("insets,5")
                .add(progressBar)
                .getContainer();
        panel.setPreferredSize(new Dimension(300, 50));
        JFrame waitFrame = center(createFrame("Shutdown", panel));
        waitFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        waitFrame.setVisible(true);
        Completable shuttingDown = Completable.fromAction(() -> {
                    // Shutting down
                    mediatorAgent.close();
                    if (modelDumper != null) {
                        try {
                            modelDumper.close();
                        } catch (Exception e) {
                            logger.atError().setCause(e).log("Error closing model dumper");
                        }
                    }
                })
                .subscribeOn(Schedulers.computation());

        shuttingDown.andThen(completion)
                // Wait for completion
                .subscribeOn(Schedulers.computation())
                .subscribe(() -> {
                    logger.atInfo().log("Shutdown completed.");
                    // Close waiting frame
                    waitFrame.dispose();
                    if (!args.getBoolean("silent")) {
                        JOptionPane.showMessageDialog(null,
                                "Completed", "Information", JOptionPane.INFORMATION_MESSAGE);
                    }
                    // Close all frame
                    allFrames.forEach(JFrame::dispose);
                    // Notify completion
                    logger.atInfo().log("completed.");
                });
    }

    /**
     * Handles the start button event
     *
     * @param actionEvent the event
     */
    private void onStartButton(ActionEvent actionEvent) {
        active = true;
        stopButton.setEnabled(true);
        startButton.setEnabled(false);
    }

    /**
     * Handles the status ready event
     *
     * @param status the status
     */
    private void onStatusReady(RobotStatus status) {
        if (robotStartTimestamp < 0) {
            robotStartTimestamp = status.simulationTime();
        }
        long robotElapsed = status.simulationTime() - robotStartTimestamp;
        envPanel.setTimeRatio(controller.simRealSpeed());
        if (robotElapsed > sessionDuration) {
            controller.shutdown();
        }
    }

    /**
     * Handles stop button
     *
     * @param actionEvent the action event
     */
    private void onStopButton(ActionEvent actionEvent) {
        active = false;
        stopButton.setEnabled(false);
        startButton.setEnabled(true);
    }

    /**
     * Handles the window closing events
     *
     * @param windowEvent the event
     */
    private void onWindowClosing(WindowEvent windowEvent) {
        controller.shutdown();
    }

    /**
     * Handles the windows opened
     * Initializes the agent
     *
     * @param e the event
     */
    private void onWindowOpened(WindowEvent e) {
        controller.start();
    }

    /**
     * Starts the application
     */
    protected void run() throws IOException {
        logger.atInfo().log("Creating environment");
        JsonNode config = loadConfig();

        // Create context
        createContext(config);

        // Init agent
        if (agent instanceof AbstractAgentNN agentNN) {
            agent = agentNN.setPostTrainKpis(true);
        }

        boolean synchTraining = args.getBoolean("alternate");

        if (robot instanceof SimRobot robot1) {
            // Add the obstacles location changes
            robot1.readObstacleMap()
                    .subscribe(this::onObstacleMap);
            relocateButton.setEnabled(true);
        } else {
            relocateButton.setEnabled(false);
        }

        sessionDuration = this.args.getLong("localTime");
        logger.atInfo().log("Starting session ...");
        logger.atInfo().log("Session are running for {} sec...", sessionDuration);
        sessionDuration *= 1000;

        // Create panels
        createPanels();

        // Creates frames
        if (args.getBoolean("windows")) {
            createMultiFrames();
        } else {
            createSingleFrame();
        }

        // Creates the flows
        createFlows();

        agent.backup();

        // Starts the environment interaction
        active = true;
        startButton.setEnabled(false);
        frame.setVisible(true);
    }
}
