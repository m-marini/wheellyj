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

package org.mmarini.wheelly.objectives;

import io.reactivex.rxjava3.core.Completable;
import org.mmarini.rl.envs.Environment;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.envs.RobotEnvironment;
import org.mmarini.wheelly.envs.WithRadarMap;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class MockEnvironment implements RobotEnvironment, WithRadarMap, WithRobotStatus {

    @Override
    public Map<String, SignalSpec> getActions() {
        return null;
    }

    @Override
    public RobotControllerApi getController() {
        return null;
    }

    @Override
    public RadarMap getRadarMap() {
        return null;
    }

    @Override
    public RobotStatus getRobotStatus() {
        return null;
    }

    @Override
    public Map<String, SignalSpec> getState() {
        return null;
    }

    @Override
    public Completable readShutdown() {
        return null;
    }

    @Override
    public void setOnAct(UnaryOperator<Map<String, Signal>> callback) {

    }

    @Override
    public void setOnCommand(Consumer<RobotCommands> callback) {

    }

    @Override
    public void setOnError(Consumer<Throwable> callback) {

    }

    @Override
    public void setOnInference(Consumer<RobotStatus> callback) {

    }

    @Override
    public void setOnReadLine(Consumer<String> onReadLine) {

    }

    @Override
    public void setOnResult(Consumer<Environment.ExecutionResult> callback) {

    }

    @Override
    public void setOnStatusReady(Consumer<RobotStatus> callback) {

    }

    @Override
    public void setOnWriteLine(Consumer<String> onWriteLine) {

    }

    @Override
    public void shutdown() {

    }

    @Override
    public void start() {

    }
}
