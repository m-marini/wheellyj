/*
 * Copyright (c) 2023 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
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
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import org.mmarini.rl.envs.Environment;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.WithSignalsSpec;
import org.mmarini.wheelly.apis.*;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Manages the interaction between robot controller and TD agent
 */
public interface RobotEnvironment extends WithStatusFlowable, WithErrorFlowable, WithIOFlowable, WithSignalsSpec, WithCommandFlowable, WithControllerFlowable {

    /**
     * Returns the robot environment from configuration
     *
     * @param config     the json document
     * @param locator    the configuration locator
     * @param controller the controller
     */
    static RobotEnvironment fromConfig(JsonNode config, Locator locator, RobotControllerApi controller) {
        return Utils.createObject(config, locator, new Object[]{controller}, new Class[]{RobotControllerApi.class});
    }

    /**
     * Returns the robot environment from configuration
     *
     * @param file       the configuration file
     * @param controller the controller
     * @throws IOException in case of error
     */
    static RobotEnvironment fromFile(File file, RobotControllerApi controller) throws IOException {
        return Utils.createObject(file, new Object[]{controller}, new Class[]{RobotControllerApi.class});
    }

    /**
     * Returns the robot controller
     */
    RobotControllerApi getController();

    @Override
    default Flowable<RobotStatus> readCamera() {
        return getController().readCamera();
    }

    @Override
    default Flowable<RobotCommands> readCommand() {
        return getController().readCommand();
    }

    @Override
    default Flowable<RobotStatus> readContacts() {
        return getController().readContacts();
    }

    @Override
    default Flowable<String> readControllerStatus() {
        return getController().readControllerStatus();
    }

    @Override
    default Flowable<Throwable> readErrors() {
        return getController().readErrors();
    }

    @Override
    default Flowable<RobotStatus> readMotion() {
        return getController().readMotion();
    }

    @Override
    default Flowable<RobotStatus> readProxy() {
        return getController().readProxy();
    }

    @Override
    default Flowable<String> readReadLine() {
        return getController().readReadLine();
    }

    @Override
    default Flowable<RobotStatus> readRobotStatus() {
        return getController().readRobotStatus();
    }

    /**
     * Returns the completable shutdown
     */
    Completable readShutdown();

    @Override
    default Flowable<RobotStatus> readSupply() {
        return getController().readSupply();
    }

    @Override
    default Flowable<String> readWriteLine() {
        return getController().readWriteLine();
    }

    /**
     * Registers the call back of act
     *
     * @param callback the callback
     */
    void setOnAct(UnaryOperator<Map<String, Signal>> callback);

    /**
     * Registers the consumer of inference event
     *
     * @param callback the callback
     */
    void setOnInference(Consumer<RobotStatus> callback);

    /**
     * Registers the call back of result
     *
     * @param callback the callback
     */
    void setOnResult(Consumer<Environment.ExecutionResult> callback);

    /**
     * Shutdowns the environment
     */
    void shutdown();

    /**
     * Starts the environment
     */
    void start();
}
