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
import org.mmarini.wheelly.utils.WheellyJsonSchemas;
import org.mmarini.yaml.Locator;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.function.Supplier;

import static org.mmarini.wheelly.behaviours.HeadActionId.*;
import static org.mmarini.wheelly.behaviours.MoveActionId.*;

/**
 * Represents a specialized test environment designed to validate macro-action transitions
 * and simulate predictable agent behaviours.
 * <p>
 * This class extends the standard deep learning environment framework, substituting the neural
 * policy with deterministic execution cycles. It provides scripted action routines such as
 * full panoramic field sweeps and targeted area explorations to evaluate state telemetry synchronisation.
 * </p>
 */
public class DLTestMacroActionEnvironment extends DLMacroActionEnvironment {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/env-test-macro-actions-schema-0.1";
    public static final AgentAction HALT_STRAIGHT_ACTION = new AgentAction(HALT_ACTION, LOOK_STRAIGHT_ACTION);
    public static final AgentAction CONTINUE_ACTION = new AgentAction(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION);
    public static final String ACTION_ID = "action";
    public static final AgentAction HALT_SCAN_ACTION = new AgentAction(HALT_ACTION, SCAN_ACTION);
    public static final String FULL_SCAN_ID = "fullScan";
    public static final AgentAction TURN_RIGHT_SCAN = new AgentAction(TURN_RIGHT_SCAN_ACTION, LOOK_FACE_AT_OBSTACLE_ACTION);
    public static final AgentAction EXPLORE_ACTION = new AgentAction(EXPLORE_UNKNOWN_AREA, LOOK_STRAIGHT_ACTION);
    public static final String EXPLORE_ID = "explore";

    /**
     * Creates the test deep learning environment from a JSON configuration file.
     * <p>
     * This utility delegates execution initialisation to the root locator block, ignoring the physical
     * file instance to parse raw configuration blocks directly from the document node tree.
     * </p>
     *
     * @param root the root node containing the environment JSON data definition
     * @param file the physical configuration file reference (currently unused)
     * @return a fully initialised {@link DLTestMacroActionEnvironment} instance
     * @throws IOException if schema validation rules fail or data reading is disrupted
     */
    public static DLTestMacroActionEnvironment create(JsonNode root, File file) throws IOException {
        return create(root, Locator.root());
    }

    /**
     * Creates the test deep learning environment from a JSON configuration block.
     * <p>
     * This method validates the targets against the dedicated testing schema, extracts structural seed values
     * for deterministic pseudo-randomisation, and maps the action identifier string to a specific behaviour.
     * </p>
     *
     * @param root    the root node containing the environment JSON data definition
     * @param locator the context tracker pointing to the relative configuration scope
     * @return a fully initialised {@link DLTestMacroActionEnvironment} instance
     * @throws IOException if schema validation rules fail or data reading is disrupted
     */
    public static DLTestMacroActionEnvironment create(JsonNode root, Locator locator) throws IOException {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        DLMacroActionEnvConfig config = DLMacroActionEnvConfig.fromJson(root, locator);
        long seed = locator.path(SEED_ID).getNode(root).asLong();
        Random random = seed == 0 ? new Random() : new Random(seed);
        String action = locator.path(ACTION_ID).getNode(root).asText();
        return new DLTestMacroActionEnvironment(config, random, action);
    }

    private final Supplier<AgentAction> actionFunction;
    private int ct;

    /**
     * Constructs a {@code DLTestMacroActionEnvironment} and maps the requested action identifier
     * to its matching deterministic supplier function.
     *
     * @param config         the configuration parameters defining environment specifications
     * @param random         the pseudo-random generator utilised for internal kinematics variations
     * @param actionFunction the identifier string selecting the behavioural test script pattern
     */
    public DLTestMacroActionEnvironment(DLMacroActionEnvConfig config, Random random, String actionFunction) {
        super(config, random);
        this.actionFunction = switch (actionFunction) {
            case FULL_SCAN_ID -> this::fullScan;
            case EXPLORE_ID -> this::explore;
            default -> this::haltStraight;
        };
    }

    /**
     * Executes a deterministic exploration script loop modulated across a 160-tick execution cycle.
     *
     * @return the sequential {@link AgentAction} assigned for the current step interval
     */
    private AgentAction explore() {
        return switch (ct = (ct + 1) % 160) {
            case 1 -> HALT_SCAN_ACTION;
            case 21 -> TURN_RIGHT_SCAN;
            case 41 -> HALT_SCAN_ACTION;
            case 61 -> TURN_RIGHT_SCAN;
            case 81 -> HALT_SCAN_ACTION;
            case 101 -> TURN_RIGHT_SCAN;
            case 121 -> HALT_SCAN_ACTION;
            case 141 -> EXPLORE_ACTION;
            default -> CONTINUE_ACTION;
        };
    }

    /**
     * Executes a periodic scanning sequence script modulated across a 40-tick execution cycle.
     *
     * @return the sequential {@link AgentAction} assigned for the current step interval
     */
    private AgentAction fullScan() {
        return switch (ct = (ct + 1) % 40) {
            case 1 -> HALT_SCAN_ACTION;
            case 21 -> TURN_RIGHT_SCAN;
            default -> CONTINUE_ACTION;
        };
    }

    /**
     * Returns a baseline stationary safety fallback action pattern.
     *
     * @return the default constant {@link #HALT_STRAIGHT_ACTION}
     */
    private AgentAction haltStraight() {
        return HALT_STRAIGHT_ACTION;
    }

    /**
     * Fetches the next scripted or deterministic action instruction mapping this testing routine cycle.
     *
     * @return the computed {@link AgentAction} dispatched by the current active testing routine
     */
    @Override
    public AgentAction nextAction() {
        return actionFunction.get();
    }
}
