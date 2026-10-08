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

package org.mmarini.wheelly.envs;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.mmarini.NotImplementedException;
import org.mmarini.rl.agents.AgentConnector;
import org.mmarini.rl.envs.ExecutionResult;
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.fsm.*;
import org.mmarini.wheelly.rrt.RRTPathFinder;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.DoubleConsumer;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.engines.SearchRefreshState.FREE_PROB;
import static org.mmarini.wheelly.engines.SearchRefreshState.NEAREST_TARGET_PROB;

/**
 * Connects the world modeller to reinforcement learning agent
 * generating state signals and converting actions to robot command
 */
public class DLMacroActionEnvironment implements EnvironmentApi, EnvFSMContext, EnvState {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/env-macro-actions-schema-0.1";
    public static final String SEED_ID = "seed";
    public static final String MOVE_ID = "move";
    public static final String HEAD_ID = "head";
    private static final Logger logger = LoggerFactory.getLogger(DLMacroActionEnvironment.class);
    private static final double FIXED_SAFETY_MARGIN = 10e-3;
    private static final ActionFunction MACRO_ACTION_FUNCTION = new ActionFunction() {
        @Override
        public List<RobotCommand> commands(Map<String, Signal> actions, WorldModel... states) {
            throw new NotImplementedException();
        }

        @Override
        public Map<String, SignalSpec> spec() {
            return Map.of(
                    HEAD_ID, new IntSignalSpec(new long[]{1, 1}, HeadActionId.values().length),
                    MOVE_ID, new IntSignalSpec(new long[]{1, 1}, MoveActionId.values().length)
            );
        }
    };

    /**
     * Creates the deep learning environment from a JSON configuration file.
     *
     * @param root the document root
     * @param file the configuration file
     * @throws IOException if a validation or reading error occurs
     */
    public static DLMacroActionEnvironment create(JsonNode root, File file) throws IOException {
        return create(root, Locator.root());
    }

    /**
     * Creates the deep learning environment from a JSON configuration file.
     *
     * @param root    the document
     * @param locator the locator of configuration
     * @throws IOException if a validation or reading error occurs
     */
    public static DLMacroActionEnvironment create(JsonNode root, Locator locator) throws IOException {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        DLMacroActionEnvConfig config = DLMacroActionEnvConfig.fromJson(root, locator);
        long seed = locator.path(SEED_ID).getNode(root).asLong();
        Random random = seed == 0 ? new Random() : new Random(seed);
        return new DLMacroActionEnvironment(config, random);
    }

    private final DLMacroActionEnvConfig config;
    private final Random random;
    private final CoordinatedMotionState fsmState;
    private StateFunction stateFunc;
    private RewardFunction rewardFunc;
    private volatile AgentConnector agent;
    private volatile EnvironmentStepState stepState;
    private WorldModel currentWorldModel;
    private DoubleConsumer onReward;

    /**
     * Creates the deep learning environment
     *
     * @param config the configuration
     * @param random the random generator
     */
    public DLMacroActionEnvironment(DLMacroActionEnvConfig config, Random random) {
        this.config = requireNonNull(config);
        this.random = requireNonNull(random);
        this.fsmState = CoordinatedMotionState.create(config.fsmConfig());
        logger.atDebug().log("Created");
    }

    /**
     * Returns the action function
     */
    public ActionFunction actionFunction() {
        return MACRO_ACTION_FUNCTION;
    }

    @Override
    public Map<String, SignalSpec> actionSpec() {
        return actionFunction().spec();
    }

    /**
     *
     * Returns the agent
     */
    public AgentConnector agent() {
        return agent;
    }

    public List<Point2D> computePathToNearestMarker() {
        WorldModel worldModel = worldModel();
        RadarMap map = worldModel.radarMap();
        RobotStatus status = worldModel.robotStatus();
        Point2D robotLocation = status.location();
        List<Point2D> markers = worldModel.markers().values().stream()
                .map(LabelMarker::location)
                .filter(location -> {
                    double d = robotLocation.distance(location);
                    return d >= config.fsmConfig().minMarkerDistance();
                })
                .toList();
        if (markers.isEmpty()) {
            // no marker -> return empty path
            return List.of();
        }
        RRTPathFinder.Config config1 = new RRTPathFinder.Config(config.growthDistance(), NEAREST_TARGET_PROB, FREE_PROB, robotLocation);
        RRTPathFinder pathFinder = RRTPathFinder.createMarkerTargets(config1, map,
                config.maxRobotGoalToMarkerDistance(), config.pathSafetyDistance() + FIXED_SAFETY_MARGIN,
                random, markers.stream());
        return searchPath(pathFinder);
    }

    private List<Point2D> computePathToNearestUnknownArea() {
        WorldModel worldModel = worldModel();
        RadarMap map = worldModel.radarMap();
        RobotStatus status = worldModel.robotStatus();
        RRTPathFinder.Config config1 = new RRTPathFinder.Config(config.growthDistance(), NEAREST_TARGET_PROB, FREE_PROB, status.location());
        RRTPathFinder pathFinder = RRTPathFinder.createUnknownTargets(config1, map,
                config.pathSafetyDistance() + FIXED_SAFETY_MARGIN, random);
        return searchPath(pathFinder);
    }

    @Override
    public void connect(WorldModellerConnector connector) {
        requireNonNull(connector);
        WorldModelSpec worldSpec = connector.worldModelSpec();
        this.stateFunc = config.stateFunctionBuilder().apply(worldSpec);
    }

    @Override
    public void connect(AgentConnector agent) {
        requireNonNull(agent);
        agent.validate(stateSpec(), actionSpec());
        this.agent = agent;
    }

    public DLMacroActionEnvironment currentWorldModel(WorldModel worldModel) {
        this.currentWorldModel = requireNonNull(worldModel);
        return this;
    }

    private AgentAction decodeAction(Map<String, Signal> actions) {
        int moveCode = actions.get(MOVE_ID).getInt(0, 0);
        MoveActionId moveId = MoveActionId.values()[moveCode];
        int headCode = actions.get(HEAD_ID).getInt(0, 0);
        HeadActionId headId = HeadActionId.values()[headCode];
        return new AgentAction(moveId, headId);
    }

    /**
     * Validates that all required operational dependencies are connected.
     */
    private void ensureConnected() {
        if (agent == null) {
            throw new IllegalStateException("Environment is not connected to any AgentConnector.");
        }
        if (stateFunc == null) {
            throw new IllegalStateException("Environment is not connected to any WorldModellerConnector.");
        }
    }

    @Override
    public AgentAction nextAction() {
        WorldModel model = worldModel();
        BasicEnvState state = new BasicEnvState(model);
        Map<String, Signal> signals1 = state(state);
        Map<String, Signal> actions = agent.act(signals1);

        AgentAction commands = decodeAction(actions);

        // Process observation if we have a recorded previous step

        EnvironmentStepState stepState = this.stepState;
        if (stepState != null) {
            double reward = reward(stepState.prevState(), stepState.prevCommands(), state);
            this.agent = agent.observe(new ExecutionResult(
                    stepState.signals0(), stepState.prevActions(), reward, signals1
            ));
            if (onReward != null) {
                onReward.accept(reward);
            }
        }

        // Split status
        this.stepState = new EnvironmentStepState(state, signals1, commands, actions);
        return commands;
    }

    @Override
    public RobotCommand onInference(WorldModel state) {
        requireNonNull(state);
        ensureConnected();
        currentWorldModel(state);
        return this.fsmState.tick(this);
    }

    /**
     * Returns the rewards flow
     */
    public DLMacroActionEnvironment onReward(DoubleConsumer callback) {
        this.onReward = onReward != null ? onReward : callback;
        return this;
    }

    @Override
    public Single<List<Point2D>> pathToNearestMarker() {
        return Single.fromSupplier(this::computePathToNearestMarker)
                .subscribeOn(Schedulers.computation());
    }

    @Override
    public Single<List<Point2D>> pathToNearestUnknownArea() {
        return Single.fromSupplier(this::computePathToNearestUnknownArea)
                .subscribeOn(Schedulers.computation());
    }

    @Override
    public double reward(EnvState state0, EnvAction actions, EnvState state1) {
        return rewardFunc != null ? rewardFunc.reward(state0, actions, state1) : 0;
    }

    /**
     * Returns the path to target
     *
     * @param pathFinder the pathfinder
     */
    protected List<Point2D> searchPath(RRTPathFinder pathFinder) {
        if (pathFinder == null) {
            logger.atDebug().log("No path finder");
            return List.of();
        }
        pathFinder.init();
        long searchTimeout = System.currentTimeMillis() + config.maxSearchTime();
        // Look for the maximum time interval
        for (int i = 0; i < config.maxIterations()
                && !pathFinder.isCompleted()
                && pathFinder.rrt().goals().size() < config.minGoals()
                && System.currentTimeMillis() <= searchTimeout; i++) {
            pathFinder.grow();
        }
        if (!pathFinder.isFound()) {
            logger.atDebug().log("No path found");
            return List.of();
        }
        List<Point2D> path = pathFinder.path().stream()
                .skip(1) // skip the start point
                .toList();
        logger.atDebug().log("Path found");
        return path;
    }

    @Override
    public void setRewardFunc(RewardFunction rewardFunc) {
        this.rewardFunc = rewardFunc;
    }

    @Override
    public Map<String, Signal> state(EnvState model) {
        requireNonNull(model);
        ensureConnected();
        return stateFunc.signals(model);
    }

    @Override
    public Map<String, SignalSpec> stateSpec() {
        return stateFunc.spec();
    }

    @Override
    public WorldModel worldModel() {
        return currentWorldModel;
    }

    /**
     * Immutable container representing the historical context of the environment step.
     */
    private record EnvironmentStepState(
            BasicEnvState prevState,
            Map<String, Signal> signals0,
            AgentAction prevCommands,
            Map<String, Signal> prevActions
    ) {
        private EnvironmentStepState {
            requireNonNull(prevState);
            requireNonNull(signals0);
            requireNonNull(prevCommands);
            requireNonNull(prevActions);
        }
    }
}
