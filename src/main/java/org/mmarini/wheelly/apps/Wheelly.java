/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
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
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jetbrains.annotations.NotNull;
import org.mmarini.Tuple2;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.swing.Messages;
import org.mmarini.wheelly.behaviours.*;
import org.mmarini.wheelly.control.ControllerApi;
import org.mmarini.wheelly.control.RobotController;
import org.mmarini.wheelly.control.RobotStatus;
import org.mmarini.wheelly.gyms.GymAdapter;
import org.mmarini.wheelly.gyms.RewardFunction;
import org.mmarini.wheelly.gyms.WithRewardCallback;
import org.mmarini.wheelly.mqtt.MqttRobot;
import org.mmarini.wheelly.perception.FiducialMarker;
import org.mmarini.wheelly.perception.GridMap;
import org.mmarini.wheelly.perception.Perceptor;
import org.mmarini.wheelly.perception.WorldModel;
import org.mmarini.wheelly.rlagents.Agent;
import org.mmarini.wheelly.rlagents.RLAgent;
import org.mmarini.wheelly.rlagents.TrainingKpis;
import org.mmarini.wheelly.robots.*;
import org.mmarini.wheelly.storage.serde.InferenceFileWriter;
import org.mmarini.wheelly.storage.serde.InferenceWriter;
import org.mmarini.wheelly.storage.serde.KeyBinWriter;
import org.mmarini.wheelly.swing.*;
import org.mmarini.wheelly.utils.Angle;
import org.mmarini.wheelly.utils.AppYaml;
import org.mmarini.wheelly.utils.WheellyJsonSchemas;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.reactivex.rxjava3.core.Flowable.interval;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.swing.Utils.*;
import static org.mmarini.yaml.Utils.fromFile;

/**
 * Run a test to check for robot environment with random behaviour agent
 */
public class Wheelly {
    public static final String WHEELLY_SCHEMA_YML = "https://mmarini.org/wheelly/wheelly-schema-4.0";
    public static final int LAYOUT_INTERVAL = 300;
    public static final MacroAction HALT_ACTION = new MacroAction(MoveActionId.HALT_ACTION, HeadActionId.LOOK_STRAIGHT_ACTION);
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
        parser.addArgument("-c", "--config")
                .setDefault("wheelly.yml")
                .help("specify yaml configuration path");
        parser.addArgument("-i", "--inference")
                .help("specify inference output path");
        parser.addArgument("-k", "--kpis")
                .help("specify kpis output path");
        parser.addArgument("-p", "--parallel")
                .action(Arguments.storeTrue())
                .help("specify concurrent training");
        parser.addArgument("-s", "--silent")
                .action(Arguments.storeTrue())
                .help("specify silent shuttingDown (no window messages)");
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

    @NotNull
    private static Container createWaitPanel() {
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setString("   Waiting for completion ...   ");
        progressBar.setStringPainted(true);
        Container panel = new GridLayoutHelper<>(new JPanel())
                .modify("insets,5")
                .add(progressBar)
                .getContainer();
        panel.setPreferredSize(new Dimension(300, 50));
        return panel;
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
        } catch (Throwable e) {
            logger.atError().setCause(e).log("IO exception");
            System.exit(1);
        }
    }

    protected final EnvironmentPanel envPanel;
    private final WheellyToolBar toolBar;
    private final DoubleReducedValue reactionRobotTime;
    private final DoubleReducedValue reactionRealTime;
    private final ComMonitor comMonitor;
    private final SensorMonitor sensorMonitor;
    private final KpisPanel kpisPanel;
    private final CompletableSubject completion;
    private final Namespace args;
    private final JButton relocateButton;
    private final AtomicBoolean shuttingDown;
    private final JFrame waitFrame;
    private final AtomicBoolean active;
    private long autosaveInstant;
    private long robotStartTimestamp;
    private Long sessionDuration;
    private GridPanel gridPanel;
    private long prevRobotStep;
    private long prevStep;
    private List<JFrame> allFrames;
    private RobotApi robot;
    private ControllerApi controller;
    private Perceptor perceptor;
    private GymAdapter adapter;
    private Agent agent;
    private JFrame frame;
    private InferenceWriter inferenceWriter;
    private KeyBinWriter kpisWriter;
    private long savingInterval;

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
        this.sensorMonitor = new SensorMonitor();
        this.toolBar = new WheellyToolBar();
        this.robotStartTimestamp = -1;
        this.reactionRobotTime = DoubleReducedValue.mean();
        this.reactionRealTime = DoubleReducedValue.mean();
        this.prevRobotStep = -1;
        this.prevStep = -1;
        this.completion = CompletableSubject.create();
        this.relocateButton = SwingUtils.getInstance().initButton(new JButton(), "Wheelly.relocateButton");
        this.waitFrame = center(createFrame("Shutdown", createWaitPanel()));
        this.shuttingDown = new AtomicBoolean(false);
        this.active = new AtomicBoolean(true);

        comMonitor.setPrintTimestamp(true);
        waitFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    }

    /**
     * Creates the context from configuration
     *
     * @param config the configuration
     * @throws IOException in case of error
     */
    private void createContext(JsonNode config) throws Throwable {
        logger.atInfo().log("Creating robot ...");
        this.robot = AppYaml.robotFromJson(config);

        logger.atInfo().log("Creating controller ...");
        this.controller = AppYaml.controllerFromJson(config);

        logger.atInfo().log("Creating world modeller ...");
        this.perceptor = AppYaml.perceptorFromJson(config);

        logger.atInfo().log("Creating behaviour ...");
        BehaviourApi behaviour = AppYaml.behaviourFromJson(config);

        logger.atInfo().log("Creating RL environment ...");
        this.adapter = AppYaml.adapterFromFile(config);

        logger.atInfo().log("Create reward function ...");
        RewardFunction rewardFunc = AppYaml.rewardFromJson(config);

        logger.atInfo().log("Creating agent ...");
        this.agent = Agent.fromFile(
                new File(Locator.locate("agent").getNode(config).asText()));
        if (agent instanceof RLAgent dlAgent) {
            dlAgent = dlAgent.concurrentTraining(args.getBoolean("parallel"))
                    .onKpis(this::onKpis)
                    .onRewards(this::onRewards);
            agent = dlAgent;
        }

        controller.init(robot);

        perceptor.init(robot.robotSpec(), controller);
        perceptor.decisionMaker(behaviour);

        behaviour.decisionMaker(this::decide);

        adapter.agent(agent);
        adapter.rewardFunc(rewardFunc);
        adapter.worldModelSpec(perceptor.worldModelSpec());

        agent.validate(adapter);

        this.savingInterval = Locator.locate("savingInterval").getNode(config).asLong();
        autosaveInstant = System.currentTimeMillis() + savingInterval;

        kpisPanel.addActionColumns(adapter.actionSpec()
                .keySet().stream().sorted().toArray(String[]::new));

        String kpisPath = args.getString("kpis");
        if (kpisPath != null) {
            kpisWriter = new KeyBinWriter(new File(kpisPath));
        }

        // Creates the model dumper
        String inference = this.args.getString("inference");
        if (inference != null) {
            this.inferenceWriter = InferenceFileWriter.fromFile(new File(inference));
            behaviour.onDecision(t -> {
                try {
                    inferenceWriter.write(t._1, t._2);
                } catch (IOException e) {
                    logger.atError().setCause(e).log("Error writing decision");
                }
            });
        }

        if (!(robot instanceof SimRobot)) {
            toolBar.relocateButton().setEnabled(false);
        }
    }

    /**
     * Creates the flows of events
     */
    private void createFlows() {
        toolBar.pauseButton().addActionListener(this::onStopButton);
        toolBar.playButton().addActionListener(this::onStartButton);
        toolBar.resetButton().addActionListener(this::onResetButton);
        toolBar.clearMapButton().addActionListener(this::onClearMapButton);
        toolBar.relocateButton().addActionListener(this::onRelocateButton);
        toolBar.learningButton().addActionListener(this::onLearningToggle);

        // Robot monitoring
        if (robot instanceof MqttRobot mqttRobot) {
            comMonitor.addRobot(mqttRobot);
        }

        // Controller monitoring
        controller.addOnRobotStatus(this::onStatusReady);
        if (controller instanceof RobotController robotController) {
            robotController.onMotionStatus(this::onMotionStatus);
            robotController.onHeadStatus(this::onHeadStatus);
        }
        controller.readErrors()
                .observeOn(hu.akarnokd.rxjava3.swing.SwingSchedulers.edt())
                .subscribe(err -> {
                    comMonitor.onError(err);
                    logger.atError().setCause(err).log();
                });
        controller.readControllerStatus()
                .map(ControllerStatusMapper::map)
                .distinct()
                .observeOn(hu.akarnokd.rxjava3.swing.SwingSchedulers.edt())
                .subscribe(this::onControllerStatus);
        controller.readShutdown()
                .observeOn(Schedulers.io())
                .subscribe(this::onControllerShutdown);
        controller.readShutdown().andThen(agent.readShutdown())
                .observeOn(Schedulers.io())
                .subscribe(this::onShutdownCompleted);

        // Perception monitoring
        perceptor.onDecision(this::onWorldModellerDecision);

        // Adapter monitoring
        if (adapter instanceof WithRewardCallback rewardProducer) {
            rewardProducer.onReward(reward -> {
                logger.atInfo().log("on reward");
                envPanel.setReward(reward);
                sensorMonitor.onReward(reward);
            });
        }

        // Agent monitoring
        agent.readShutdown()
                .observeOn(hu.akarnokd.rxjava3.swing.SwingSchedulers.edt())
                .subscribe(this::onAgentShutdown);

        for (JFrame allFrame : allFrames) {
            allFrame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    onWindowClosing(e);
                }
            });
        }
        completion.onComplete();
    }

    /**
     * Creates all the frames
     */
    private void createMultiFrames() {
        // Create multiple frame app
        JFrame gridFrame = createFixFrame(Messages.getString("Grid.title"), gridPanel);

        // Create kpis frame
        JFrame kpisFrame = kpisPanel.createFrame();
        frame = createFrame(Messages.getString("Wheelly.title"), new JScrollPane(envPanel));
        frame.getContentPane().add(toolBar, BorderLayout.NORTH);
        SwingObservable.window(frame, SwingObservable.WINDOW_ACTIVE)
                .filter(ev ->
                        ev.getID() == WindowEvent.WINDOW_OPENED)
                .subscribe(this::onWindowOpened);

        JFrame comFrame = comMonitor.createFrame();
        JFrame sensorFrame = sensorMonitor.createFrame();

        // Collects all frames
        allFrames = new ArrayList<>();
        allFrames.add(frame);
        allFrames.add(gridFrame);
        if (kpisFrame != null) {
            allFrames.add(kpisFrame);
        }
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
        this.gridPanel = new GridPanel();
    }

    /**
     * Creates the single application frame
     */
    private void createSingleFrame() {
        JTabbedPane panel = new JTabbedPane();
        panel.addTab(Messages.getString("Wheelly.tabPanel.envMap"), new JScrollPane(envPanel));
        panel.addTab(Messages.getString("Wheelly.tabPanel.gridMap"), new JScrollPane(gridPanel));
        panel.addTab(Messages.getString("Wheelly.tabPanel.kpi"), new JScrollPane(kpisPanel));
        panel.addTab(Messages.getString("Wheelly.tabPanel.sensor"), new JScrollPane(sensorMonitor));
        panel.addTab(Messages.getString("Wheelly.tabPanel.com"), new JScrollPane(comMonitor));

        // Create single frame app
        this.frame = createFrame(Messages.getString("Wheelly.title"), panel);
        frame.getContentPane().add(toolBar, BorderLayout.NORTH);
        SwingObservable.window(frame, SwingObservable.WINDOW_ACTIVE)
                .filter(ev ->
                        ev.getID() == WindowEvent.WINDOW_OPENED)
                .subscribe(this::onWindowOpened);
        center(frame);
        allFrames = List.of(frame);
    }

    private BehaviourAction decide(BehaviourState state) {
        return active.get()
                ? adapter.decide(state)
                : HALT_ACTION;
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
     * Handles the agent shutdown
     */
    private void onAgentShutdown() {
        // Shutting down
        if (kpisWriter != null) {
            try {
                kpisWriter.close();
            } catch (Exception e) {
                logger.atError().setCause(e).log("Error shuttingDown kpisWriter");
            }
            kpisWriter = null;
            logger.atInfo().log("kpi writer closed");
        }
    }

    /**
     * Handles the clear map button event
     *
     * @param actionEvent the event
     */
    private void onClearMapButton(ActionEvent actionEvent) {
        perceptor.clearRadarMap();
    }

    private void onControllerShutdown() {
        if (inferenceWriter != null) {
            try {
                inferenceWriter.close();
            } catch (Exception e) {
                logger.atError().setCause(e).log("Error shuttingDown model dumper");
            }
            logger.atInfo().log("Model dumper closed");
        }
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

    private void onHeadStatus(HeadStatus status) {
        SwingUtilities.invokeLater(() ->
                sensorMonitor.onHeadStatusCommand(status));
    }

    /**
     * Handles the kpis event
     *
     * @param kpis the key performance indicators
     */
    private void onKpis(TrainingKpis kpis) {
        kpisPanel.print(kpis);
        if (kpisWriter != null) {
            try {
                kpis.write(kpisWriter);
            } catch (IOException e) {
                logger.atError().setCause(e).log("Error dumping kpis");
            }
        }
    }

    /**
     * Handles the learning toggle button
     *
     * @param actionEvent the event
     */
    private void onLearningToggle(ActionEvent actionEvent) {
        boolean learning = toolBar.learningButton().isSelected();
        agent.learning(learning);
    }

    private void onMotionStatus(MotionStatus status) {
        SwingUtilities.invokeLater(() ->
                sensorMonitor.onMotionStatus(status));
    }

    /**
     * Handles the obstacle map
     *
     * @param map the obstacle map
     */
    private void onObstacleMap(Collection<Obstacle> map) {
        SwingUtilities.invokeLater(() ->
                envPanel.obstacles(map)
        );
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
        agent.init();
    }

    /**
     * Handles the reward flow
     *
     * @param rewards the rewards
     */
    private void onRewards(INDArray rewards) {
        if (kpisWriter != null) {
            try {
                kpisWriter.write(Map.of("rewards", rewards));
            } catch (IOException e) {
                logger.atError().setCause(e).log("Error dumping rewards");
            }
        }
    }

    private void onShutdownCompleted() {
        // Close waiting frame
        waitFrame.dispose();

        // Show the completion frame if requested
        if (!args.getBoolean("silent")) {
            logger.atInfo().log("Waiting for completion frame closure.");
            JOptionPane.showMessageDialog(null,
                    "Completed", "Information", JOptionPane.INFORMATION_MESSAGE);
        }
        // Close all frame
        allFrames.forEach(JFrame::dispose);
        // Notify completion
        logger.atInfo().log("Shutdown completed.");
    }

    /**
     * Handles the start button event
     *
     * @param actionEvent the event
     */
    private void onStartButton(ActionEvent actionEvent) {
        active.set(true);
        toolBar.pauseButton().setEnabled(true);
        toolBar.playButton().setEnabled(false);
    }

    /**
     * Handles the status ready event
     *
     * @param status the status
     */
    private void onStatusReady(RobotStatus status) {
        if (robotStartTimestamp < 0) {
            robotStartTimestamp = status.robotTime();
        }
        SwingUtilities.invokeLater(() -> {
            envPanel.robotStatus(status);
            sensorMonitor.onStatus(status);
            envPanel.setTimeRatio(controller.simRealSpeed());
        });
        long now = System.currentTimeMillis();
        if (now > autosaveInstant) {
            agent.backup();
            autosaveInstant = now + savingInterval;
        }
        long robotElapsed = status.robotTime() - robotStartTimestamp;
        if (robotElapsed > sessionDuration) {
            shutDown();
        }
    }

    /**
     * Handles stop button
     *
     * @param actionEvent the action event
     */
    private void onStopButton(ActionEvent actionEvent) {
        active.set(false);
        toolBar.pauseButton().setEnabled(false);
        toolBar.playButton().setEnabled(true);
    }

    /**
     * Handles the window shuttingDown events
     *
     * @param ignoredWindowEvent the event
     */
    private void onWindowClosing(WindowEvent ignoredWindowEvent) {
        shutDown();
    }

    /**
     * Handles the windows opened
     * Initialises the agent
     *
     * @param e the event
     */
    private void onWindowOpened(WindowEvent e) {
        controller.start();
    }

    /**
     * Handles the inference event
     *
     * @param inferenceResult the inference result
     */
    private void onWorldModellerDecision(Tuple2<WorldModel, RobotCommand> inferenceResult) {
        logger.atDebug().log("on Inference");
        WorldModel worldModel = inferenceResult._1;
        RobotStatus robotStatus = worldModel.robotStatus();
        Map<Integer, FiducialMarker> markers = worldModel.markers();
        GridMap map = worldModel.gridMap();
        long robotClock = robotStatus.robotTime();
        long time = System.currentTimeMillis();
        Angle robotDir = robotStatus.direction();
        Point2D center = map.center();
        /*
         * Transforms the marker locations to grid map coordinates
         */
        AffineTransform tr = AffineTransform.getRotateInstance(map.direction().toRad());
        tr.translate(-center.getX(), -center.getY());

        List<Point2D> mks = markers.values().stream()
                .map(p -> tr.transform(p.location(), null))
                .toList();
        SwingUtilities.invokeLater(() -> {
            envPanel.radarMap(worldModel.radarMap());

            envPanel.markers(markers.values());

            gridPanel.setGridMap(map);
            gridPanel.setRobotDirection(robotDir.sub(map.direction()));

            gridPanel.setMarkers(mks);

            if (prevRobotStep >= 0) {
                envPanel.setReactionRealTime(reactionRealTime.add(time - prevStep).value() * 1e-3);
                envPanel.setReactionRobotTime(reactionRobotTime.add(robotClock - prevRobotStep).value() * 1e-3);
            }
        });
        prevRobotStep = robotClock;
        prevStep = time;
        logger.atDebug().log("on Inference end");
    }

    /**
     * Starts the application
     */
    protected void run() throws Throwable {
        logger.atInfo().log("Creating environment");
        JsonNode config = loadConfig();

        // Create context
        createContext(config);

        if (robot instanceof SimRobot robot1) {
            // Add the obstacles location changes
            robot1.onObstacleMap(this::onObstacleMap);
            relocateButton.setEnabled(true);
        } else {
            relocateButton.setEnabled(false);
        }

        sessionDuration = this.args.getLong("localTime");
        logger.atInfo().log("Starting session ...");
        logger.atInfo().log("Session are running for {} sec...", sessionDuration);
        sessionDuration *= 1000;

        // Create agent backup
        agent.backup();

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

        // Starts the environment interaction
        toolBar.playButton().setEnabled(false);
        toolBar.learningButton().setSelected(true);
        frame.setVisible(true);
    }

    /**
     * Shutdown task
     */
    private void shutDown() {
        if (!shuttingDown.getAndSet(true)) {
            logger.atInfo().log("Shutdown");
            waitFrame.setVisible(true);
            Completable.fromRunnable(() -> {
                        controller.shutdown();
                        agent.close();
                    })
                    .subscribeOn(Schedulers.io())
                    .subscribe();
        }
    }
}
