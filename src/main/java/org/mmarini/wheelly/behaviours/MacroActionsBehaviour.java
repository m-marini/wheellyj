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

package org.mmarini.wheelly.behaviours;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.behaviours.fsm.FSMContext;
import org.mmarini.wheelly.behaviours.fsm.HeadMacroActionState;
import org.mmarini.wheelly.behaviours.fsm.MacroActionConfig;
import org.mmarini.wheelly.behaviours.fsm.MotionMacroActionState;
import org.mmarini.wheelly.perception.DecisionMaker;
import org.mmarini.wheelly.perception.PerceptionConnector;
import org.mmarini.wheelly.perception.WorldModel;
import org.mmarini.wheelly.robots.HeadStatus;
import org.mmarini.wheelly.robots.MotionStatus;
import org.mmarini.wheelly.robots.RobotCommand;
import org.mmarini.wheelly.utils.WheellyJsonSchemas;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Connects the world modeller to the reinforcement learning agent.
 * <p>
 * This class is responsible for generating state signals from environmental data
 * and converting agent actions back into low-level robot commands.
 * </p>
 */
public class MacroActionsBehaviour implements BehaviourApi, FSMContext {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/behave-macro-actions-schema-0.1";
    public static final String SEED_ID = "seed";
    private static final Logger logger = LoggerFactory.getLogger(MacroActionsBehaviour.class);

    /**
     * Creates the deep learning environment from a JSON configuration file.
     *
     * @param root the document root node
     * @param file the configuration file
     * @return the initialised {@link MacroActionsBehaviour} instance
     * @throws IOException if a validation or reading error occurs
     */
    public static MacroActionsBehaviour create(JsonNode root, File file) throws IOException {
        return create(root, Locator.root());
    }

    /**
     * Creates the deep learning environment from a JSON configuration file.
     *
     * @param root    the document
     * @param locator the locator of configuration
     * @throws IOException if a validation or reading error occurs
     */
    public static MacroActionsBehaviour create(JsonNode root, Locator locator) throws IOException {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        MacroActionConfig config = MacroActionConfig.fromJson(root, locator);
        long seed = locator.path(SEED_ID).getNode(root).asLong();
        Random random = seed == 0 ? new Random() : new Random(seed);
        return new MacroActionsBehaviour(config, random);
    }

    private final MotionMacroActionState motionState;
    private final HeadMacroActionState headState;
    private WorldModel currentWorldModel;
    private DecisionMaker<BehaviourState, BehaviourAction> decisionMaker;
    private Consumer<Tuple2<BehaviourState, BehaviourAction>> onDecision;

    /**
     * Creates the deep learning environment
     *
     * @param config the configuration
     * @param random the random generator
     */
    public MacroActionsBehaviour(MacroActionConfig config, Random random) {
        requireNonNull(config);
        this.motionState = new MotionMacroActionState(config, requireNonNull(random));
        this.headState = new HeadMacroActionState(config);
        logger.atDebug().log("Created");
    }

    public void connect(PerceptionConnector connector) {
        requireNonNull(connector);
    }

    /**
     * Sets the current world model context.
     *
     * @param worldModel the world model instance to store
     * @return this environment instance for method chaining
     */
    public MacroActionsBehaviour currentWorldModel(WorldModel worldModel) {
        this.currentWorldModel = requireNonNull(worldModel);
        return this;
    }

    @Override
    public RobotCommand decide(WorldModel state) {
        requireNonNull(state);
        ensureConnected();
        currentWorldModel(state);
        if (headState.completed()
                || headState.expired()
                || motionState.expired()
                || motionState.completed()) {
            // Generates a new macro action from inference engine
            MacroActionsState state1 = new MacroActionsState(state, motionState.currentAction(), headState.currentAction());
            BehaviourAction nextAction = decisionMaker.decide(state1);
            headState.init(this, nextAction.headActionId());
            motionState.init(this, nextAction.moveActionId());
            if (onDecision != null) {
                onDecision.accept(Tuple2.of(state1, nextAction));
            }
        }
        MotionStatus motionCmd = motionState.tick(this);
        HeadStatus headCmd = headState.tick(this);
        return new RobotCommand(motionCmd, headCmd);
    }

    @Override
    public void decisionMaker(DecisionMaker<BehaviourState, BehaviourAction> decisionMaker) {
        this.decisionMaker = requireNonNull(decisionMaker);
    }

    /**
     * Validates that all required operational dependencies are connected.
     *
     * @throws IllegalStateException if the agent or the state function is missing
     */
    private void ensureConnected() {
        if (decisionMaker == null) {
            throw new IllegalStateException("Environment is not connected to any decision maker.");
        }
    }

    @Override
    public void onDecision(Consumer<Tuple2<BehaviourState, BehaviourAction>> callback) {
        this.onDecision = onDecision == null ? callback : onDecision.andThen(callback);
    }

    /**
     * Returns the current world model containing the latest telemetry and environmental data.
     * <p>
     * This snapshot represents the robot's internal belief and perception of its surroundings
     * at the current execution step.
     * </p>
     *
     * @return the active {@link WorldModel} instance
     */
    public WorldModel worldModel() {
        return currentWorldModel;
    }
}
