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
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mmarini.rl.agents.Agent;
import org.mmarini.rl.agents.KpiBinWriter;
import org.mmarini.rl.agents.TDAgentSingleNN;
import org.mmarini.rl.envs.Environment;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.wheelly.apis.ObstacleMap;
import org.mmarini.wheelly.apis.RobotApi;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apis.SimRobot;
import org.mmarini.wheelly.envs.PolarRobotEnv;
import org.mmarini.wheelly.envs.RobotEnvironment;
import org.mmarini.wheelly.envs.WithPolarMap;
import org.mmarini.wheelly.envs.WithRadarMap;
import org.mmarini.wheelly.swing.*;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.reactivex.rxjava3.core.Flowable.interval;
import static java.lang.String.format;
import static org.mmarini.wheelly.swing.Utils.*;
import static org.mmarini.yaml.Utils.fromFile;

/**
 * Run a test to check for robot environment with random behavior agent
 */
public class Wheelly {
    public static final String WHEELLY_SCHEMA_YML = "https://mmarini.org/wheelly/wheelly-schema-1.0";
    private static final Logger logger = LoggerFactory.getLogger(Wheelly.class);
    public static final int LAYOUT_INTERVAL = 300;

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
        new Wheelly().start(args);
    }

    protected final EnvironmentPanel envPanel;
    private final MeanValue avgRewards;
    private final MeanValue reactionRobotTime;
    private final MeanValue reactionRealTime;
    private final ComMonitor comMonitor;
    private final SensorMonitor sensorMonitor;
    private final KpisPanel kpisPanel;
    private JFrame kpisFrame;
    private final CompletableSubject completion;
    protected Namespace args;
    private long robotStartTimestamp;
    private Long sessionDuration;
    private PolarPanel polarPanel;
    private JFrame radarFrame;
    private RobotEnvironment environment;
    private Agent agent;
    private long prevRobotStep;
    private long prevStep;
    private ComDumper dumper;
    private final LearnPanel learnPanel;
    private List<JFrame> allFrames;

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
     * Creates the server reinforcement learning engine server
     */
    public Wheelly() {
        this.envPanel = new EnvironmentPanel();
        this.kpisPanel = new KpisPanel();
        this.comMonitor = new ComMonitor();
        comMonitor.setPrintTimestamp(true);
        this.learnPanel = new LearnPanel();
        this.sensorMonitor = new SensorMonitor();
        this.robotStartTimestamp = -1;
        this.avgRewards = MeanValue.create();
        this.reactionRobotTime = MeanValue.create();
        this.reactionRealTime = MeanValue.create();
        this.prevRobotStep = -1;
        this.prevStep = -1;
        this.completion = CompletableSubject.create();
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
        long time = System.currentTimeMillis();
        if (prevRobotStep >= 0) {
            envPanel.setReactionRealTime(reactionRealTime.add(time - prevStep).value() * 1e-3);
            envPanel.setReactionRobotTime(reactionRobotTime.add(robotClock - prevRobotStep).value() * 1e-3);
        }
        prevRobotStep = robotClock;
        prevStep = time;
    }

    private void handleResult(Environment.ExecutionResult result) {
        double reward = result.getReward();
        envPanel.setReward(avgRewards.add(reward).value());
        sensorMonitor.onReward(reward);
        agent.observe(result);
    }

    /**
     * Creates all the frames
     */
    private void createFrames() {
        JScrollPane scrollEnv = new JScrollPane(envPanel);
        JFrame frame = createFrame(Messages.getString("Wheelly.title"), scrollEnv);
        SwingObservable.window(frame, SwingObservable.WINDOW_ACTIVE)
                .filter(ev -> ev.getID() == WindowEvent.WINDOW_OPENED)
                .doOnNext(this::handleWindowOpened)
                .subscribe();
        JFrame comFrame = comMonitor.createFrame();
        JFrame sensorFrame = sensorMonitor.createFrame();

        // Collects all frames
        allFrames = new ArrayList<>();
        allFrames.add(frame);
        if (radarFrame != null) {
            allFrames.add(radarFrame);
        }
        if (kpisFrame != null) {
            allFrames.add(kpisFrame);
        }
        allFrames.add(learnPanel.createFrame());
        allFrames.add(sensorFrame);
        allFrames.add(comFrame);

        // Open all the windows
        JFrame[] frameAry = this.allFrames.toArray(JFrame[]::new);

        Observable<WindowEvent>[] x = allFrames.stream()
                .map(f -> SwingObservable.window(f, SwingObservable.WINDOW_ACTIVE))
                .toArray(Observable[]::new);

        Observable.mergeArray(x)
                .filter(ev -> ev.getID() == WindowEvent.WINDOW_CLOSING)
                .doOnNext(this::handleWindowClosing)
                .subscribe();

        layHorizontally(frameAry);
        interval(LAYOUT_INTERVAL, TimeUnit.MILLISECONDS)
                .limit(allFrames.size())
                .doOnNext(i ->
                        allFrames.get(allFrames.size() - 1 - Math.toIntExact(i)).setVisible(true))
                .subscribe();

        comFrame.setState(JFrame.ICONIFIED);
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
            agent.close();
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
        RobotApi robot = environment.getController().getRobot();
        if (robot instanceof SimRobot) {
            Optional<ObstacleMap> obstaclesMap = ((SimRobot) robot).obstaclesMap();
            obstaclesMap.map(ObstacleMap::points)
                    .ifPresent(envPanel::setObstacleMap);
            obstaclesMap.map(ObstacleMap::gridSize)
                    .ifPresent(envPanel::setObstacleSize);
        }
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
     *
     * @param args the command line argument
     */
    protected void start(String[] args) {
        ArgumentParser parser = createParser();
        try {
            this.args = parser.parseArgs(args);
            logger.atInfo().log("Creating environment");
            JsonNode config = fromFile(this.args.getString("config"));
            this.environment = AppYaml.envFromJson(config, Locator.root(), WHEELLY_SCHEMA_YML);
            Locator agentLocator = Locator.locate(Locator.locate("agent").getNode(config).asText());
            if (agentLocator.getNode(config).isMissingNode()) {
                throw new IllegalArgumentException(format("Missing node %s", agentLocator));
            }
            this.agent = Agent.fromConfig(config, agentLocator, environment);
            kpisPanel.setKeys(agent.getActions().keySet().toArray(String[]::new));
            learnPanel.setLearningRates(((TDAgentSingleNN) agent).alphas());

            logger.atInfo().log("Creating agent");
            if (environment instanceof PolarRobotEnv) {
                this.polarPanel = new PolarPanel();
                double radarMaxDistance = ((PolarRobotEnv) environment).getMaxRadarDistance();
                polarPanel.setRadarMaxDistance(radarMaxDistance);
                radarFrame = createFixFrame(Messages.getString("Radar.title"), polarPanel);
                SwingObservable.window(radarFrame, SwingObservable.WINDOW_ACTIVE)
                        .filter(ev -> ev.getID() == WindowEvent.WINDOW_CLOSING)
                        .doOnNext(this::handleWindowClosing)
                        .subscribe();
            }

            sessionDuration = this.args.getLong("localTime");
            logger.atInfo().log("Starting session ...");
            logger.atInfo().log("Session are running for {} sec...", sessionDuration);
            sessionDuration *= 1000;

            String kpis = this.args.getString("kpis");
            if (!kpis.isEmpty()) {
                // Create kpis frame
                this.kpisFrame = kpisPanel.createFrame();
                SwingObservable.window(kpisFrame, SwingObservable.WINDOW_ACTIVE)
                        .filter(ev -> ev.getID() == WindowEvent.WINDOW_CLOSING)
                        .doOnNext(this::handleWindowClosing)
                        .subscribe();
                KpiBinWriter kpiWriter = KpiBinWriter.createFromLabels(new File(kpis), this.args.getString("labels"));
                agent.readKpis()
                        .observeOn(Schedulers.io(), true)
                        .doOnNext(kpisPanel::addKpis)
                        .doOnNext(kpiWriter::write)
                        .doOnError(ex -> logger.atError().setCause(ex).log("Error writing kpis"))
                        .doOnComplete(() -> {
                                    logger.atInfo().log("Closing kpis writer ...");
                                    kpiWriter.close();
                                    logger.atInfo().log("Kpis writer closed");
                                    completion.onComplete();
                                }
                        ).subscribe();
            } else {
                completion.onComplete();
            }
            Optional.ofNullable(this.args.getString("dump"))
                    .ifPresent(file -> {
                        try {
                            this.dumper = ComDumper.fromFile(file);
                        } catch (IOException e) {
                            logger.atError().setCause(e).log("Error dumping to {}", file);
                        }
                    });
            environment.readRobotStatus()
                    .observeOn(Schedulers.io())
                    .doOnNext(this::handleStatusReady)
                    .subscribe();
            environment.readReadLine()
                    .observeOn(Schedulers.io())
                    .doOnNext(this::handleReadLine)
                    .subscribe();
            environment.readWriteLine()
                    .observeOn(Schedulers.io())
                    .doOnNext(this::handleWrittenLine)
                    .subscribe();
            environment.readCommand()
                    .observeOn(Schedulers.io())
                    .doOnNext(sensorMonitor::onCommand)
                    .subscribe();
            environment.readErrors()
                    .observeOn(Schedulers.io())
                    .doOnNext(err -> {
                        comMonitor.onError(err);
                        logger.atError().setCause(err).log();
                    })
                    .subscribe();
            environment.readControllerStatus()
                    .observeOn(Schedulers.io())
                    .doOnNext(this::handleControllerStatus)
                    .subscribe();
            environment.readShutdown()
                    .doOnComplete(this::handleShutdown)
                    .subscribe();

            environment.setOnInference(this::handleInference);
            environment.setOnAct(agent::act);
            environment.setOnResult(this::handleResult);

            learnPanel.readLearningRates()
                    .doOnNext(t -> ((TDAgentSingleNN) agent).alphas(t))
                    .subscribe();

            createFrames();

            environment.start();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        } catch (IOException e) {
            logger.atError().setCause(e).log("IO exception");
            System.exit(1);
        }
    }
}
