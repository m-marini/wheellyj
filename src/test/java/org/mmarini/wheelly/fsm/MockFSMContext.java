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
import org.mmarini.wheelly.apis.WorldModel;

public class MockFSMContext implements EnvironmentFSMContext {
    private WorldModel model;
    private boolean requestNextAction;
    private RobotCommands handleEventResult;
    private EnvironmentFSMEvent handleEvent;

    public MockFSMContext() {
        handleEventResult = RobotCommands.halt();
    }

    public MockFSMContext worldModel(WorldModel worldModel) {
        this.model = worldModel;
        return this;
    }

    public MockFSMContext clear() {
        this.requestNextAction = false;
        this.handleEvent = null;
        return this;
    }

    public boolean isRequestNextAction() {
        return this.requestNextAction;
    }

    @Override
    public WorldModel worldModel() {
        return model;
    }

    @Override
    public void requestNextAction() {
        requestNextAction = true;
    }

    public MockFSMContext handleEventResult(RobotCommands handleEventResult) {
        this.handleEventResult = handleEventResult;
        return this;
    }

    @Override
    public RobotCommands handle(EnvironmentFSMEvent event) {
        handleEvent = event;
        return handleEventResult;
    }

    public EnvironmentFSMEvent handleEvent() {
        return handleEvent;
    }
}
