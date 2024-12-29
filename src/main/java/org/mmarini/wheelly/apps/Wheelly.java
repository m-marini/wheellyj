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
import org.mmarini.rl.agents.AbstractAgentNN;
import org.mmarini.rl.agents.Agent;
import org.mmarini.rl.agents.KpiBinWriter;
import org.mmarini.rl.envs.Environment;
import org.mmarini.rl.envs.Signal;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apis.SimRobot;
import org.mmarini.wheelly.envs.*;
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
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.reactivex.rxjava3.core.Flowable.interval;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.swing.Utils.*;
import static org.mmarini.yaml.Utils.fromFile;

/**
 * Run a test to check for robot environment with random behavior agent
 */
public class Wheelly {
    public static final String WHEELLY_SCHEMA_YML = "https://mmarini.org/wheelly/wheelly-schema-2.0";
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
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-c", "--config")
                .setDefault("wheelly.yml")
                .help("specify yaml configuration file");
        parser.addArgument("-k", "--kpis")
                .setDefault("")
                .help("specify kpis path");
        parser.addArgument("-l", "--labels")
                .setDefault("default")
                .help("specify kpi label regex comma separated (all for all kpi, batch for batch training kpis)");
        parser.addArgument("-s", "--silent")
                .action(Arguments.storeTrue())
                .help("specify silent closing (no window messages)");
        parser.addArgument("-a", "--alternate")
                .action(Arguments.storeTrue())
                .help("specify alternate act/training");
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
    private final MeanValue avgRewards;
    private final MeanValue reactionRobotTime;
    private final MeanValue reactionRealTime;
    private final ComMonitor comMonitor;
    private final SensorMonitor sensorMonitor;
    private final KpisPanel kpisPanel;
    private final CompletableSubject completion;
    private final LearnPanel learnPanel;
    private final Namespace args;
    private final JButton stopButton;
    private final JButton startButton;
    private JFrame kpisFrame;
    private long robotStartTimestamp;
    private Long sessionDuration;
    private PolarPanel polarPanel;
    private GridPanel gridPanel;
    private JFrame radarFrame;
    private RobotEnvironment environment;
    private long prevRobotStep;
    private long prevStep;
    private ComDumper dumper;
    private List<JFrame> allFrames;
    private boolean active;
    private KpiBinWriter kpiWriter;
    private AtomicReference<AgentTrainer> trainer;
    private JFrame gridFrame;

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
        comMonitor.setPrintTimestamp(true);
        this.learnPanel = new LearnPanel();
        this.sensorMonitor = new SensorMonitor();
        this.stopButton = new JButton();
        this.startButton = new JButton();
        this.robotStartTimestamp = -1;
        this.avgRewards = MeanValue.zeros();
        this.reactionRobotTime = MeanValue.zeros();
        this.reactionRealTime = MeanValue.zeros();
        this.prevRobotStep = -1;
        this.prevStep = -1;
        this.completion = CompletableSubject.create();
        SwingUtils.getInstance().initButton(stopButton, "Wheelly.stopButton");
        SwingUtils.getInstance().initButton(startButton, "Wheelly.runButton");
    }

    /**
     * Creates the flows of events
     */
    private void createFlows() {
        environment.readRobotStatus().observeOn(Schedulers.io()).doOnNext(this::handleStatusReady)
                .subscribe();
        environment.readReadLine().observeOn(Schedulers.io()).doOnNext(this::handleReadLine)
                .subscribe();
        environment.readWriteLine().observeOn(Schedulers.io()).doOnNext(this::handleWrittenLine).subscribe();
        environment.readCommand().observeOn(Schedulers.io()).doOnNext(sensorMonitor::onCommand).subscribe();
        environment.readErrors().observeOn(Schedulers.io()).doOnNext(err -> {
            comMonitor.onError(err);
            logger.atError().setCause(err).log();
        }).subscribe();
        environment.readControllerStatus().observeOn(Schedulers.io()).doOnNext(this::handleControllerStatus).subscribe();
        environment.readShutdown().doOnComplete(this::handleShutdown)
                .subscribe();

        environment.setOnInference(this::handleInference);
        environment.setOnAct(this::handleAct);
        environment.setOnResult(this::handleResult);

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
    }

    /**
     * Creates all the frames
     */
    private void createMultiFrames() {
        // Create multiple frame app
        if (environment instanceof WithPolarMap) {
            radarFrame = createFixFrame(Messages.getString("Radar.title"), polarPanel);
        }
        if (environment instanceof WithGridMap) {
            this.gridFrame = createFixFrame(Messages.getString("Grid.title"), gridPanel);
        }

        if (!this.args.getString("kpis").isEmpty()) {
            // Create kpis frame
            this.kpisFrame = kpisPanel.createFrame();
        }
        JFrame frame = createFrame(Messages.getString("Wheelly.title"), new JScrollPane(envPanel));
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
        if (radarFrame != null) {
            allFrames.add(radarFrame);
        }
        if (gridFrame != null) {
            allFrames.add(gridFrame);
        }
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
        kpisPanel.setKeys(agent.getActions().keySet().toArray(String[]::new));
        learnPanel.setEta(agent.eta());
        learnPanel.setActionAlphas(agent.alphas());
        if (environment instanceof PolarRobotEnv env) {
            this.polarPanel = new PolarPanel();
            double radarMaxDistance = env.getMaxRadarDistance();
            polarPanel.setRadarMaxDistance(radarMaxDistance);
            this.gridPanel = new GridPanel();
        }
    }

    /**
     * Creates the single application frame
     */
    private void createSingleFrame() {
        JTabbedPane panel = new JTabbedPane();
        panel.addTab(Messages.getString("Wheelly.tabPanel.envMap"), new JScrollPane(envPanel));

        if (environment instanceof WithPolarMap) {
            panel.addTab(Messages.getString("Wheelly.tabPanel.polarMap"), new JScrollPane(polarPanel));
        }
        if (environment instanceof WithGridMap) {
            panel.addTab(Messages.getString("Wheelly.tabPanel.gridMap"), new JScrollPane(gridPanel));
        }
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
        JFrame frame = createFrame(Messages.getString("Wheelly.title"), panel);
        frame.getContentPane().add(createToolBar(), BorderLayout.NORTH);
        SwingObservable.window(frame, SwingObservable.WINDOW_ACTIVE)
                .filter(ev ->
                        ev.getID() == WindowEvent.WINDOW_OPENED)
                .doOnNext(this::handleWindowOpened).subscribe();
        center(frame);
        allFrames = List.of(frame);
        frame.setVisible(true);
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
        stopButton.addActionListener(this::handleStopButton);
        startButton.addActionListener(this::handleStartButton);
        resetButton.addActionListener(this::handleResetButton);
        clearMapButton.addActionListener(this::handleClearMapButton);
        return toolBar;
    }

    /**
     * Returns the action signals resulting from the action of inference
     *
     * @param signals the input signals
     */
    private Map<String, Signal> handleAct(Map<String, Signal> signals) {
        return active ? trainer.get().agent().act(signals) : ((AbstractRobotEnv) environment).haltActions();
    }

    /**
     * Handles the clear map button event
     *
     * @param actionEvent the event
     */
    private void handleClearMapButton(ActionEvent actionEvent) {
        if (environment instanceof PolarRobotEnv env) {
            env.clearRadarMap();
        }
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
     * @param status the robot status
     */
    private void handleInference(RobotStatus status) {
        long robotClock = status.simulationTime();
        envPanel.setRobotStatus(status);
        sensorMonitor.onStatus(status);
        if (environment instanceof WithRadarMap) {
            envPanel.setRadarMap(((WithRadarMap) environment).getRadarMap());
        }
        if (environment instanceof WithPolarMap) {
            polarPanel.setPolarMap(((WithPolarMap) environment).getPolarMap());
        }
        if (environment instanceof WithGridMap) {
            gridPanel.setGridMap(((WithGridMap) environment).gridMap());
        }
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
        simRobot.obstaclesMap().ifPresent(map -> {
                    envPanel.setHinderedPoints(map.hindered().toList());
                    envPanel.setLabeledPoints(map.labeled().toList());
                }
        );
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
     * Handles reset button
     *
     * @param actionEvent the action event
     */
    private void handleResetButton(ActionEvent actionEvent) {
        trainer.updateAndGet(AgentTrainer::resetAgent);
    }

    /**
     * Handle the result of execution control step
     * Lets the agent to observer and eventually
     * runs the training process
     *
     * @param result the result
     */
    private void handleResult(Environment.ExecutionResult result) {
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
            logger.atError().setCause(e).log();
        }
        if (dumper != null) {
            try {
                dumper.close();
            } catch (IOException e) {
                logger.atError().setCause(e).log();
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
        envPanel.setTimeRatio(environment.getController().simRealSpeed());
        if (robotElapsed > sessionDuration) {
            environment.shutdown();
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
        environment.shutdown();
    }

    /**
     * Handles the windows opened
     * Initializes the agent
     *
     * @param e the event
     */
    private void handleWindowOpened(WindowEvent e) {
        Optional.ofNullable(environment.getController().getRobot())
                .filter(r -> r instanceof SimRobot)
                .flatMap(r -> ((SimRobot) r).obstaclesMap())
                .ifPresent(map -> {
                    envPanel.setObstacleSize(map.gridSize());
                    envPanel.setHinderedPoints(map.hindered().toList());
                    envPanel.setLabeledPoints(map.labeled().toList());
                });
        environment.start();
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
     * Starts the application
     */
    protected void run() throws IOException {
        logger.atInfo().log("Creating environment");
        File configFile = new File(this.args.getString("config"));
        JsonNode config = fromFile(configFile);
        this.environment = AppYaml.envFromJson(config, WHEELLY_SCHEMA_YML);
        long savingInterval = Locator.locate("savingInterval").getNode(config).asLong();

        logger.atInfo().log("Creating agent");
        Agent agent = Agent.fromFile(
                new File(Locator.locate("agent").getNode(config).asText()),
                environment);
        if (agent instanceof AbstractAgentNN agentNN) {
            agent = agentNN.setPostTrainKpis(true);
        }
        boolean synchTraining = args.getBoolean("alternate");
        this.trainer = new AtomicReference<>(AgentTrainer.create(agent, synchTraining, savingInterval));
        if (environment.getController().getRobot() instanceof SimRobot robot1) {
            // Add the obstacles location changes
            robot1.setOnObstacleChanged(this::handleObstacleChanged);
        }

        sessionDuration = this.args.getLong("localTime");
        logger.atInfo().log("Starting session ...");
        logger.atInfo().log("Session are running for {} sec...", sessionDuration);
        sessionDuration *= 1000;

        // Create the kpis writer
        String kpis = this.args.getString("kpis");
        if (!kpis.isEmpty()) {
            // Create kpis frame
            this.kpiWriter = KpiBinWriter.createFromLabels(new File(kpis), this.args.getString("labels"));
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

        // Creates the com line dumper
        Optional.ofNullable(this.args.getString("dump")).ifPresent(file -> {
            try {
                this.dumper = ComDumper.fromFile(file);
            } catch (IOException e) {
                logger.atError().setCause(e).log("Error dumping to {}", file);
            }
        });

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
        active = true;
        startButton.setEnabled(false);
        agent.backup();
        environment.start();
    }
}
