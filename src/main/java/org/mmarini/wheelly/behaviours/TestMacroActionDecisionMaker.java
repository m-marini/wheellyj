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
import org.mmarini.yaml.Locator;

import java.io.File;
import java.io.IOException;
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
public class TestMacroActionDecisionMaker implements DecisionMaker<MacroActionState, BehaviourMacroAction> {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/env-test-macro-actions-schema-0.1";
    public static final BehaviourMacroAction HALT_STRAIGHT_ACTION = new BehaviourMacroAction(HALT_ACTION, LOOK_STRAIGHT_ACTION);
    public static final BehaviourMacroAction CONTINUE_ACTION = new BehaviourMacroAction(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION);
    public static final String ACTION_ID = "action";
    public static final BehaviourMacroAction HALT_SCAN_ACTION = new BehaviourMacroAction(HALT_ACTION, SCAN_ACTION);
    public static final String FULL_SCAN_ID = "fullScan";
    public static final BehaviourMacroAction TURN_RIGHT_SCAN = new BehaviourMacroAction(TURN_RIGHT_SCAN_ACTION, LOOK_FACE_AT_OBSTACLE_ACTION);
    public static final BehaviourMacroAction EXPLORE_ACTION = new BehaviourMacroAction(EXPLORE_UNKNOWN_AREA, LOOK_STRAIGHT_ACTION);
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
     * @return a fully initialised {@link TestMacroActionDecisionMaker} instance
     * @throws IOException if schema validation rules fail or data reading is disrupted
     */
    public static TestMacroActionDecisionMaker create(JsonNode root, File file) throws IOException {
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
     * @return a fully initialised {@link TestMacroActionDecisionMaker} instance
     * @throws IOException if schema validation rules fail or data reading is disrupted
     */
    public static TestMacroActionDecisionMaker create(JsonNode root, Locator locator) throws IOException {
        String action = locator.path(ACTION_ID).getNode(root).asText();
        return new TestMacroActionDecisionMaker(action);
    }

    private final Supplier<BehaviourMacroAction> actionFunction;
    private int ct;

    /**
     * Constructs a {@code TestMacroActionDecisionMaker} and maps the requested action identifier
     * to its matching deterministic supplier function.
     *
     * @param actionFunction the identifier string selecting the behavioural test script pattern
     */
    public TestMacroActionDecisionMaker(String actionFunction) {
        this.actionFunction = switch (actionFunction) {
            case FULL_SCAN_ID -> this::fullScan;
            case EXPLORE_ID -> this::explore;
            default -> this::haltStraight;
        };
    }

    /**
     * Fetches the next scripted or deterministic action instruction mapping this testing routine cycle.
     *
     * @return the computed {@link BehaviourMacroAction} dispatched by the current active testing routine
     */
    @Override
    public BehaviourMacroAction decide(MacroActionState state) {
        return actionFunction.get();
    }

    /**
     * Executes a deterministic exploration script loop modulated across a 160-tick execution cycle.
     *
     * @return the sequential {@link BehaviourMacroAction} assigned for the current step interval
     */
    private BehaviourMacroAction explore() {
        return switch (ct = (ct + 1) % 160) {
            case 1, 41, 81, 121 -> HALT_SCAN_ACTION;
            case 21, 61, 101 -> TURN_RIGHT_SCAN;
            case 141 -> EXPLORE_ACTION;
            default -> CONTINUE_ACTION;
        };
    }

    /**
     * Executes a periodic scanning sequence script modulated across a 40-tick execution cycle.
     *
     * @return the sequential {@link BehaviourMacroAction} assigned for the current step interval
     */
    private BehaviourMacroAction fullScan() {
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
    private BehaviourMacroAction haltStraight() {
        return HALT_STRAIGHT_ACTION;
    }
}
