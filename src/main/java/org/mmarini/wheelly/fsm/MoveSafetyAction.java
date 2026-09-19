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

package org.mmarini.wheelly.fsm;

import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apis.WorldModel;

import java.awt.geom.Point2D;

public class MoveSafetyAction extends AbstractCommitmentAction {
    private final double safetyDistance;
    private boolean completed;
    private Point2D safetyTarget;

    public MoveSafetyAction(int commitmentTime, double safetyDistance) {
        super(commitmentTime);
        this.safetyDistance = safetyDistance;
    }

    @Override
    public boolean completed() {
        return completed;
    }

    @Override
    protected RobotCommands executeAction(MacroActionContext context, WorldModel state) {
        RobotStatus robotStatus = state.robotStatus();
        RobotCommands result = RobotCommands.halt();
        if (!robotStatus.canMoveForward()) {
            if (robotStatus.canMoveBackward()) {
                // Breaking contact
                computeSafetyTarget(robotStatus, -safetyDistance);
                result = RobotCommands.backward(safetyTarget);
            }
        }
        if (completed || expired()){
            context.requestNextAction();
        }
        return result;
    }

    private void computeSafetyTarget(RobotStatus robotStatus, double distance) {
        safetyTarget = robotStatus.direction().at(robotStatus.location(), distance);
    }
}
