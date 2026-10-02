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
import org.mmarini.wheelly.apis.WheellyJsonSchemas;
import org.mmarini.wheelly.fsm.AgentAction;
import org.mmarini.yaml.Locator;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.function.Supplier;

import static org.mmarini.wheelly.fsm.HeadActionId.*;
import static org.mmarini.wheelly.fsm.MoveActionId.*;

/**
 * Connects the world modeller to reinforcement learning agent
 * generating state signals and converting actions to robot command
 */
public class DLTestMacroActionEnvironment extends DLMacroActionEnvironment {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/env-test-macro-actions-schema-0.1";
    public static final AgentAction HALT_STRAIGHT_ACTION = new AgentAction(HALT_ACTION, LOOK_STRIGHT_ACTION);
    public static final AgentAction CONTINUE_ACTION = new AgentAction(CONTINUE_MOVE_ACTION, CONTINUE_HEAD_ACTION);
    public static final String ACTION_ID = "action";
    public static final AgentAction HALT_SCAN_ACTION = new AgentAction(HALT_ACTION, SCAN_ACTION);
    public static final String FULL_SCAN_ID = "fullScan";
    public static final AgentAction TURN_RIGHT_SCAN = new AgentAction(TURN_RIGHT_SCAN_ACTION, LOOK_FACE_AT_NEAREST_OBSTACLE_ACTION);
    public static final AgentAction EXPLORE_ACTION = new AgentAction(EXPLORE_NEAREST_UNKNOWN_AREA, LOOK_STRIGHT_ACTION);
    public static final String EXPLORE_ID = "explore";

    /**
     * Creates the deep learning environment from a JSON configuration file.
     *
     * @param root the document root
     * @param file the configuration file
     * @throws IOException if a validation or reading error occurs
     */
    public static DLTestMacroActionEnvironment create(JsonNode root, File file) throws IOException {
        return create(root, Locator.root());
    }

    /**
     * Creates the deep learning environment from a JSON configuration file.
     *
     * @param root    the document
     * @param locator the locator of configuration
     * @throws IOException if a validation or reading error occurs
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
     * Creates the deep learning environment
     *
     * @param config         the configuration
     * @param random         the random generator
     * @param actionFunction the action function
     */
    public DLTestMacroActionEnvironment(DLMacroActionEnvConfig config, Random random, String actionFunction) {
        super(config, random);
        this.actionFunction = switch (actionFunction) {
            case FULL_SCAN_ID -> this::fullScan;
            case EXPLORE_ID -> this::explore;
            default -> this::haltStraight;
        };
    }

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

    private AgentAction fullScan() {
        return switch (ct = (ct + 1) % 40) {
            case 1 -> HALT_SCAN_ACTION;
            case 21 -> TURN_RIGHT_SCAN;
            default -> CONTINUE_ACTION;
        };
    }

    private AgentAction haltStraight() {
        return HALT_STRAIGHT_ACTION;
    }

    @Override
    public AgentAction nextAction() {
        return actionFunction.get();
    }
}
