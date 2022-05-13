/*
 *
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.engines.statemachine;

import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.wheelly.model.ContactSensors;
import org.mmarini.wheelly.model.InferenceMonitor;
import org.mmarini.wheelly.model.MapStatus;
import org.mmarini.wheelly.model.WheellyStatus;

import java.util.Optional;

import static org.mmarini.wheelly.engines.statemachine.StateMachineContext.TIMEOUT_KEY;
import static org.mmarini.wheelly.engines.statemachine.StateTransition.*;
import static org.mmarini.wheelly.model.RobotController.STOP_DISTANCE;

/**
 *
 */
public abstract class AbstractEngineStatus implements EngineStatus {
    public final String name;
    private long entryTime;
    private long timeout;

    /**
     * Creates named engine status
     *
     * @param name the name
     */
    protected AbstractEngineStatus(String name) {
        this.name = name;
    }

    @Override
    public EngineStatus activate(StateMachineContext context, InferenceMonitor monitor) {
        entryTime = System.currentTimeMillis();
        timeout = context.getLong(name + "." + TIMEOUT_KEY, Long.MAX_VALUE);
        return this;
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - entryTime;
    }

    public long getEntryTime() {
        return entryTime;
    }

    public String getName() {
        return name;
    }

    public long getTimeout() {
        return timeout;
    }

    public AbstractEngineStatus setTimeout(long timeout) {
        this.timeout = timeout;
        return this;
    }

    public boolean isExpired() {
        return getElapsedTime() >= timeout;
    }

    protected Optional<StateTransition> safetyCheck(Timed<MapStatus> data, StateMachineContext context, InferenceMonitor monitor) {
        WheellyStatus status = data.value().getWheelly();
        if (status.isBlocked()) {
            return Optional.of(BLOCKED_TRANSITION);
        }
        if (!status.getCanMoveForward()) {
            return Optional.of(OBSTACLE_TRANSITION);
        }
        if (!status.getCanMoveBackward()) {
            context.setObstacle(status.getRelativeContact(ContactSensors.Direction.SOUTH));
            return Optional.of(OBSTACLE_TRANSITION);
        }
        boolean isNearObstacle = status.getSampleDistance() > 0 && status.getSampleDistance() <= STOP_DISTANCE;
        if (isNearObstacle) {
            status.getSampleLocation()
                    .ifPresent(context::setObstacle);
            return Optional.of(OBSTACLE_TRANSITION);
        }
        if (isExpired()) {
            return Optional.of(TIMEOUT_TRANSITION);
        }
        return Optional.empty();
    }
}
