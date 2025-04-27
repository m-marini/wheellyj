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
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mmarini.Tuple2;
import org.mmarini.rl.agents.AbstractAgentNN;
import org.mmarini.rl.agents.Agent;
import org.mmarini.rl.agents.AgentConnector;
import org.mmarini.rl.agents.KpiBinWriter;
import org.mmarini.rl.envs.ExecutionResult;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.WithSignalsSpec;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.envs.EnvironmentApi;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.wheelly.envs.WorldEnvironment;
import org.mmarini.wheelly.swing.*;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
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
import java.util.concurrent.atomic.AtomicReference;
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
        parser.addArgument("-d", "--dump")
                .help("specify dump signal path");
        parser.addArgument("-i", "--inference")
                .help("specify inference output path");
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
    private final DoubleReducedValue avgRewards;
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
    private final AtomicReference<AgentTrainer> trainer;
    private final AgentConnector inferenceMediator;
    private JFrame kpisFrame;
    private long robotStartTimestamp;
    private Long sessionDuration;
    private PolarPanel polarPanel;
    private GridPanel gridPanel;
    private long prevRobotStep;
    private long prevStep;
    private ComDumper dumper;
    private List<JFrame> allFrames;
    private boolean active;
    private KpiBinWriter kpiWriter;
    private RobotControllerApi controller;
    private WorldModeller worldModeller;
    private EnvironmentApi environment;
    private RobotApi robot;
    private Agent agent;
    private JFrame frame;
    private InferenceFile modelDumper;

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
        this.trainer = new AtomicReference<>();
        this.robotStartTimestamp = -1;
        this.avgRewards = DoubleReducedValue.mean();
        this.reactionRobotTime = DoubleReducedValue.mean();
        this.reactionRealTime = DoubleReducedValue.mean();
        this.prevRobotStep = -1;
        this.prevStep = -1;
        this.completion = CompletableSubject.create();
        this.relocateButton = SwingUtils.getInstance().initButton(new JButton(), "Wheelly.relocateButton");
        this.stopButton = SwingUtils.getInstance().initButton(new JButton(), "Wheelly.stopButton");
        this.startButton = SwingUtils.getInstance().initButton(new JButton(), "Wheelly.runButton");
        this.inferenceMediator = new AgentConnector() {
            @Override
            public Map<String, Signal> act(Map<String, Signal> state) {
                return mediateAct(state);
            }

            @Override
            public Agent observe(ExecutionResult result) {
                return mediateObserve(result);
            }
        };
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

        logger.atInfo().log("Creating RL environment ...");
        this.environment = AppYaml.envFromJson(config);
        environment.connect(worldModeller);
        environment.connect(inferenceMediator);

        logger.atInfo().log("Create reward function ...");
        RewardFunction rewardFunc = AppYaml.rewardFromJson(config);
        environment.setRewardFunc(rewardFunc);

        logger.atInfo().log("Creating agent ...");
        Function<WithSignalsSpec, Agent> agentBuilder = Agent.fromFile(
                new File(Locator.locate("agent").getNode(config).asText()));
        this.agent = agentBuilder.apply(environment);
        // Create the kpis writer
        String kpis = this.args.getString("kpis");
        if (!kpis.isEmpty()) {
            // Create kpis frame
            this.kpiWriter = KpiBinWriter.createFromLabels(new File(kpis), this.args.getString("labels"));
        }

        // Creates the com line dumper
        Optional.ofNullable(this.args.getString("dump")).ifPresent(file -> {
            try {
                this.dumper = ComDumper.fromFile(file);
            } catch (IOException e) {
                logger.atError().setCause(e).log("Error dumping to {}", file);
            }
        });

        // Creates the model dumper
        Optional.ofNullable(this.args.getString("inference")).ifPresent(file -> {
            try {
                this.modelDumper = InferenceFile.fromFile(
                                worldModeller.worldModelSpec(),
                                worldModeller.radarModeller().topology(),
                                new File(this.args.getString("inference")))
                        .append();
            } catch (IOException e) {
                logger.atError().setCause(e).log("Error dumping inference to {}", file);
            }
        });
   }

    /**
     * Creates the flows of events
     */
    private void createFlows() {
        controller.readRobotStatus().observeOn(Schedulers.io()).doOnNext(this::handleStatusReady)
                .subscribe();
        controller.readReadLine().observeOn(Schedulers.io()).doOnNext(this::handleReadLine)
                .subscribe();
        controller.readWriteLine().observeOn(Schedulers.io()).doOnNext(this::handleWrittenLine).subscribe();
        controller.readCommand().observeOn(Schedulers.io()).doOnNext(sensorMonitor::onCommand).subscribe();
        controller.readErrors().observeOn(Schedulers.io()).doOnNext(err -> {
            comMonitor.onError(err);
            logger.atError().setCause(err).log();
        }).subscribe();
        controller.readControllerStatus().observeOn(Schedulers.io()).doOnNext(this::handleControllerStatus).subscribe();
        controller.readShutdown().doOnComplete(this::handleShutdown)
                .subscribe();
        worldModeller.readInference().doOnNext(this::handleInference)
                .subscribe();

        learnPanel.readActionAlphas()
                .doOnNext(alphas -> trainer.updateAndGet(t ->
                        t.alphas(alphas)))
                .subscribe();
        learnPanel.readEtas()
                .doOnNext(eta -> trainer.updateAndGet(t ->
                        t.eta(eta)))
                .subscribe();
        Observable<WindowEvent>[] windowObs = allFrames.stream()
                .map(f -> SwingObservable.window(f, SwingObservable.WINDOW_ACTIVE))
                .toArray(Observable[]::new);
        Observable.mergeArray(windowObs)
                .filter(ev -> ev.getID() == WindowEvent.WINDOW_CLOSING)
                .doOnNext(this::handleWindowClosing)
                .subscribe();

        if (kpiWriter!=null) {
            agent.readKpis().observeOn(Schedulers.io(), true)
                    .doOnNext(this::handleKpis)
                    .doOnError(ex -> logger.atError().setCause(ex).log("Error writing kpis"))
                    .doOnComplete(() -> {
                        logger.atInfo().log("Closing kpis writer ...");
                        kpiWriter.close();
                        logger.atInfo().log("Kpis writer closed");
                        completion.onComplete();
                    }).subscribe();
        } else {
            completion.onComplete();
        }
    }

    /**
     * Creates all the frames
     */
    private void createMultiFrames() {
        // Create multiple frame app
        JFrame radarFrame = createFixFrame(Messages.getString("Radar.title"), polarPanel);
        JFrame gridFrame = createFixFrame(Messages.getString("Grid.title"), gridPanel);

        if (!this.args.getString("kpis").isEmpty()) {
            // Create kpis frame
            this.kpisFrame = kpisPanel.createFrame();
        }
        frame = createFrame(Messages.getString("Wheelly.title"), new JScrollPane(envPanel));
        frame.getContentPane().add(createToolBar(), BorderLayout.NORTH);
        SwingObservable.window(frame, SwingObservable.WINDOW_ACTIVE)
                .filter(ev ->
                        ev.getID() == WindowEvent.WINDOW_OPENED)
                .doOnNext(this::handleWindowOpened).subscribe();

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
        interval(LAYOUT_INTERVAL, TimeUnit.MILLISECONDS).limit(allFrames.size()).doOnNext(i -> allFrames.get(allFrames.size() - 1 - Math.toIntExact(i)).setVisible(true)).subscribe();
    }

    /**
     * Creates the panels
     */
    private void createPanels() {
        Agent agent = this.trainer.get().agent();
        kpisPanel.addActionKpis(agent);
        learnPanel.setEta(agent.eta());
        learnPanel.setActionAlphas(agent.alphas());

        this.polarPanel = new PolarPanel();
        RobotSpec robotSpec = robot.robotSpec();
        double radarMaxDistance = robotSpec.maxRadarDistance();
        polarPanel.setRadarMaxDistance(radarMaxDistance);
        envPanel.setMarkerSize((float) worldModeller.worldModelSpec().markerSize());
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
        if (!this.args.getString("kpis").isEmpty()) {
            panel.addTab(Messages.getString("Wheelly.tabPanel.kpi"), new JScrollPane(kpisPanel));
        }
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
                .doOnNext(this::handleWindowOpened).subscribe();
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
        stopButton.addActionListener(this::handleStopButton);
        startButton.addActionListener(this::handleStartButton);
        resetButton.addActionListener(this::handleResetButton);
        clearMapButton.addActionListener(this::handleClearMapButton);
        relocateButton.addActionListener(this::handleRelocateButton);
        return toolBar;
    }

    /**
     * Handles the clear map button event
     *
     * @param actionEvent the event
     */
    private void handleClearMapButton(ActionEvent actionEvent) {
        worldModeller.clearRadarMap();
    }

    /**
     * Handles the controller status event
     *
     * @param status the controller status text
     */
    private void handleControllerStatus(String status) {
        sensorMonitor.onControllerStatus(status);
        comMonitor.onControllerStatus(status);
    }

    /**
     * Handles the inference event
     *
     * @param inferenceResult the inference result
     */
    private void handleInference(Tuple2<WorldModel, RobotCommands> inferenceResult) {
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
        envPanel.setRobotStatus(robotStatus);
        sensorMonitor.onStatus(robotStatus);
        envPanel.setRadarMap(worldModel.radarMap());
        envPanel.setMarkers(markers.values());
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

    /**
     * Handles the kpis
     *
     * @param kpis the kpis
     * @throws IOException in case of error
     */
    private void handleKpis(Map<String, INDArray> kpis) throws IOException {
        kpisPanel.addKpis(kpis);
        this.kpiWriter.write(kpis);
    }

    /**
     * Handles obstacle changeLock
     *
     * @param simRobot the sim robot
     */
    private void handleObstacleChanged(SimRobot simRobot) {
        simRobot.obstaclesMap().ifPresent(envPanel::setObstacles);
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

    /**
     * @param actionEvent the action event
     */
    private void handleRelocateButton(ActionEvent actionEvent) {
        if (this.robot instanceof SimRobot simRobot) {
            simRobot.safeRelocateRandom();
        }
    }

    /**
     * Handles reset button
     *
     * @param actionEvent the action event
     */
    private void handleResetButton(ActionEvent actionEvent) {
        trainer.updateAndGet(AgentTrainer::resetAgent);
    }

    /**
     * Handles the application shutdown
     */
    private void handleShutdown() {
        // Open wait frame
        logger.atInfo().log("Shutdown");
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setString("   Waiting for completion ...   ");
        progressBar.setStringPainted(true);
        Container panel = new GridLayoutHelper<>(new JPanel())
                .add(progressBar)
                .getContainer();
        JFrame waitFrame = center(createFrame("Shutdown", panel));
        waitFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        waitFrame.setVisible(true);
        // Shutting down
        try {
            trainer.get().agent().close();
        } catch (IOException e) {
            logger.atError().setCause(e).log("Error closing agent");
        }
        if (dumper != null) {
            try {
                dumper.close();
            } catch (IOException e) {
                logger.atError().setCause(e).log("Error closing dumper");
            }
        }
        if (modelDumper != null) {
            try {
                modelDumper.close();
            } catch (Exception e) {
                logger.atError().setCause(e).log("Error closing model dumper");
            }
        }
        // Wait for completion
        completion.doOnComplete(() -> {
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
        }).subscribe();
    }

    /**
     * Handles the start button event
     *
     * @param actionEvent the event
     */
    private void handleStartButton(ActionEvent actionEvent) {
        active = true;
        stopButton.setEnabled(true);
        startButton.setEnabled(false);
    }

    /**
     * Handles the status ready event
     *
     * @param status the status
     */
    private void handleStatusReady(RobotStatus status) {
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
    private void handleStopButton(ActionEvent actionEvent) {
        active = false;
        stopButton.setEnabled(false);
        startButton.setEnabled(true);
    }

    /**
     * Handles the window closing events
     *
     * @param windowEvent the event
     */
    private void handleWindowClosing(WindowEvent windowEvent) {
        controller.shutdown();
    }

    /**
     * Handles the windows opened
     * Initializes the agent
     *
     * @param e the event
     */
    private void handleWindowOpened(WindowEvent e) {
        Optional.ofNullable(robot)
                .filter(r -> r instanceof SimRobot)
                .flatMap(r -> ((SimRobot) r).obstaclesMap())
                .ifPresent(envPanel::setObstacles);
        controller.start();
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

    /**
     * Returns the validated application configuration
     *
     * @throws IOException in case of error
     */
    private JsonNode loadConfig() throws IOException {
        File configFile = new File(this.args.getString("config"));
        JsonNode config = fromFile(configFile);
        JsonSchemas.instance().validateOrThrow(config, WHEELLY_SCHEMA_YML);
        return config;
    }

    /**
     * Returns the action signals resulting from the action of inference
     *
     * @param signals the input signals
     */
    private Map<String, Signal> mediateAct(Map<String, Signal> signals) {
        return active ? trainer.get().agent().act(signals) : ((WorldEnvironment) environment).haltActions();
    }

    /**
     * Handle the result of execution control step
     * Lets the agent to observer and eventually
     * runs the training process
     * Returns the agent
     *
     * @param result the result
     */
    private Agent mediateObserve(ExecutionResult result) {
        if (active) {
            double reward = result.reward();
            // Update ui
            envPanel.setReward(avgRewards.add((float) reward).value());
            sensorMonitor.onReward(reward);
            // Observe the result
            Maybe<AgentTrainer> tr = trainer.updateAndGet(t ->
                            t.observeResult(result))
                    .trainer();
            if (tr != null) {
                // If asynchronous training is required prepare for run
                Maybe<AgentTrainer> trainer1 = tr.subscribeOn(Schedulers.computation())
                        .doOnSuccess(trainer2 ->
                                trainer.updateAndGet(t ->
                                        t.setTrainedAgent(trainer2)));
                // Reset asynchronous trainer
                trainer.updateAndGet(t ->
                        t.trainer(null));
                // Run training
                trainer1.subscribe();
            }
        }
        return trainer.get().agent();
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
        long savingInterval = Locator.locate("savingInterval").getNode(config).asLong();
        if (agent instanceof AbstractAgentNN agentNN) {
            agent = agentNN.setPostTrainKpis(true);
        }

        boolean synchTraining = args.getBoolean("alternate");

        this.trainer.set(AgentTrainer.create(agent, synchTraining, savingInterval));
        if (robot instanceof SimRobot robot1) {
            // Add the obstacles location changes
            robot1.setOnObstacleChanged(this::handleObstacleChanged);
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
