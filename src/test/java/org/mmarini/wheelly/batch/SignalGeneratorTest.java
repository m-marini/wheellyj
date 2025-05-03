/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.batch;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.rl.agents.AbstractAgentNN;
import org.mmarini.rl.agents.BinArrayFile;
import org.mmarini.rl.agents.PPOAgent;
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotSpec;
import org.mmarini.wheelly.apis.WorldModeller;
import org.mmarini.wheelly.envs.WorldEnvironment;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class SignalGeneratorTest {

    public static final File FILE = new File("tmp/dump.bin");
    public static final double GRID_SIZE = 0.3;
    public static final Complex RECEPTIVE_ANGLE = Complex.fromDeg(15);
    public static final double MAX_RADAR_DISTANCE = 3d;
    public static final double CONTACT_RADIUS = 0.28;
    public static final RobotSpec ROBOT_SPEC = new RobotSpec(MAX_RADAR_DISTANCE, RECEPTIVE_ANGLE, CONTACT_RADIUS);
    public static final File OUTPUT_PATH = new File("tmp");
    public static final double EPSILON = 1e-6;
    private static final String MODELLER_DEF = """
            ---
            $schema: https://mmarini.org/wheelly/world-modeller-schema-0.1
            class: org.mmarini.wheelly.apis.WorldModeller
            radarWidth: 51
            radarHeight: 51
            radarGrid: 0.2
            echoPersistence: 300000
            contactPersistence: 300000
            radarCleanInterval: 30000
            correlationInterval: 2000
            radarDecay: 120000
            numSectors: 24
            minRadarDistance: 0.3
            markerSize: 0.3
            markerDecay: 1000
            markerCleanDecay: 300000
            gridMapSize: 31
            """;
    private static final String ENV_DEF = """
            ---
            $schema: https://mmarini.org/wheelly/env-world-schema-0.1
            class: org.mmarini.wheelly.envs.WorldEnvironment
            numSpeeds: 5
            numDirections: 8
            numSensorDirections: 7
            markerLabels:
              - A
            """;
    private static final String[] SIGNAL_KEYS = new String[]{
            "canMoveFeatures",
            "markerStates",
    };
    private static final String[] ACTION_KEYS = new String[]{
            "move",
            "sensorAction"
    };
    private SignalGenerator generator;

    @Test
    void generate() throws IOException {
        Map<String, BinArrayFile> files = generator.generate();
        assertNotNull(files);
        assertThat(files, hasKey("reward"));
        assertThat(files, hasKey("masks.move"));
        assertThat(files, hasKey("masks.sensorAction"));
        assertThat(files, hasKey("s0.canMoveFeatures"));
        assertThat(files, hasKey("s0.markerStates"));

        assertEquals(1, files.get("reward").size());

        BinArrayFile sensorActionFile = files.get("masks.sensorAction");
        assertEquals(1, sensorActionFile.size());
        assertThat(sensorActionFile.seek(0).read(1),
                matrixCloseTo(new long[]{1, 7}, EPSILON,
                        0, 0, 0, 1, 0, 0, 0));

        BinArrayFile moveFile = files.get("masks.move");
        assertEquals(1, moveFile.size());
        INDArray move = moveFile.seek(0).read(1);
        assertThat(move, matrixCloseTo(new long[]{1, 40}, EPSILON,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 1, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0
        ));

        BinArrayFile canMoveFile = files.get("s0.canMoveFeatures");
        assertEquals(2, canMoveFile.size());
        INDArray canMove = canMoveFile.seek(0).read(2);
        assertThat(canMove, matrixCloseTo(new long[]{2, 6}, EPSILON,
                0, 0, 0, 1, 0, 0,
                0, 0, 0, 1, 0, 0));

        BinArrayFile MarkerState = files.get("s0.markerStates");
        assertEquals(2, MarkerState.size());
        INDArray state = MarkerState.seek(0).read(2);
        assertThat(state, matrixCloseTo(new long[]{2, 1}, EPSILON,
                0, 1));
    }

    @BeforeEach
    void setUp() throws IOException {
        WorldModeller modeller = WorldModeller.create(Utils.fromText(MODELLER_DEF), Locator.root());
        modeller.setRobotSpec(ROBOT_SPEC);
        WorldEnvironment environment = WorldEnvironment.create(Utils.fromText(ENV_DEF), Locator.root());
        environment.connect(modeller);
        JsonNode spec = Utils.fromResource("/rlAgent.yml");
        AbstractAgentNN agent = PPOAgent.create(spec, environment);
        this.generator = new SignalGenerator(FILE, modeller, environment, agent, OUTPUT_PATH, SIGNAL_KEYS, ACTION_KEYS);
    }
}