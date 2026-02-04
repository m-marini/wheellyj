/*
 * Copyright (c) 2023-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.apis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Load and cache JSON schemas
 */
public interface WheellyJsonSchemas {
    Logger logger = LoggerFactory.getLogger(WheellyJsonSchemas.class);

    org.mmarini.yaml.JsonSchemas singleton = org.mmarini.yaml.JsonSchemas.load("/schemas/action-func-rl-schema.yml",
            "/schemas/action-func-dl-schema.yml",
            "/schemas/agent-single-nn-schema.yml",
            "/schemas/agent-state-machine-schema.yml",
            "/schemas/batch-schema.yml",
            "/schemas/camera-calibration-schema.yml",
            "/schemas/checkup-schema.yml",
            "/schemas/controller-schema.yml",
            "/schemas/dl-agent-schema.yml",
            "/schemas/dl-agent-builder-schema.yml",
            "/schemas/env-world-schema.yml",
            "/schemas/env-dl-schema.yml",
            "/schemas/executor-schema.yml",
            "/schemas/map-schema.yml",
            "/schemas/monitor-schema.yml",
            "/schemas/mqtt-robot-schema.yml",
            "/schemas/network-list-schema.yml",
            "/schemas/network-schema.yml",
            "/schemas/ppo-agent-schema.yml",
            "/schemas/ppo-agent-spec-schema.yml",
            "/schemas/tdagent-spec-schema.yml",
            "/schemas/objective-avoid-contact-schema.yml",
            "/schemas/objective-cautious-schema.yml",
            "/schemas/objective-nomove-schema.yml",
            "/schemas/objective-explore-schema.yml",
            "/schemas/objective-stuck-schema.yml",
            "/schemas/objective-constant-schema.yml",
            "/schemas/objective-label-schema.yml",
            "/schemas/objective-moveToLabel-schema.yml",
            "/schemas/objective-sensor-label-schema.yml",
            "/schemas/objective-action-set-schema.yml",
            "/schemas/real-robot-schema.yml",
            "/schemas/sim-robot-schema.yml",
            "/schemas/signal-schema.yml",
            "/schemas/state-avoid-schema.yml",
            "/schemas/state-clear-map-schema.yml",
            "/schemas/state-func-rl-schema.yml",
            "/schemas/state-func-dl-schema.yml",
            "/schemas/state-search-label-schema.yml",
            "/schemas/state-search-refresh-schema.yml",
            "/schemas/state-search-unknown-schema.yml",
            "/schemas/state-halt-schema.yml",
            "/schemas/state-label-stuck-schema.yml",
            "/schemas/state-mapping-schema.yml",
            "/schemas/state-move-path-schema.yml",
            "/schemas/wheelly-schema.yml",
            "/schemas/world-modeller-schema.yml");

    /**
     * Returns the singleton instance
     */
    static org.mmarini.yaml.JsonSchemas instance() {
        return singleton;
    }
}
