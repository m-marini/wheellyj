/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.engines;

import org.mmarini.wheelly.apis.WorldModel;

import java.awt.geom.Point2D;
import java.util.*;

public class MockProcessorContext implements ProcessorContextApi {
    private final WorldModel worldModel;
    private final Deque<Object> stack;
    private final Map<String, Object> properties;

    MockProcessorContext(WorldModel worldModel) {
        this.worldModel = worldModel;
        this.stack = new ArrayDeque<>();
        this.properties = new HashMap<>();
    }

    MockProcessorContext() {
        this(null);
    }

    @Override
    public void clearMap() {
    }

    @Override
    public StateNode currentNode() {
        return null;
    }

    @Override
    public <T> T get(String key) {
        return (T) properties.get(key);
    }

    @Override
    public ProcessorContextApi path(List<Point2D> path) {
        return this;
    }

    @Override
    public <T> T peek() {
        return (T) stack.getLast();
    }

    @Override
    public <T> T pop() {
        return (T) stack.pollLast();
    }

    @Override
    public <T> ProcessorContextApi push(T value) {
        stack.offerLast(value);
        return this;
    }

    @Override
    public <T> ProcessorContextApi put(String key, T value) {
        properties.put(key, value);
        return this;
    }

    @Override
    public void remove(String key) {
        properties.remove(key);
    }

    @Override
    public int stackSize() {
        return stack.size();
    }

    @Override
    public WorldModel worldModel() {
        return worldModel;
    }

    @Override
    public ProcessorContextApi target(Point2D target) {
        return this;
    }
}
