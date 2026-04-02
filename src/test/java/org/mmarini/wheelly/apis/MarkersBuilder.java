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

package org.mmarini.wheelly.apis;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class MarkersBuilder {
    public static final double GRID_SIZE = 0.2;
    public static final double EPSILON = 1e-5;
    public static final int DECAY = 1000;

    private final List<Supplier<List<LabelMarker>>> markerBuilders;
    private final long cleanTime;
    private long simulationTime;

    public MarkersBuilder() {
        this.markerBuilders = new ArrayList<>();
        this.simulationTime = 1;
        cleanTime = Long.MAX_VALUE;
    }

    public MarkersBuilder addMarker(LabelMarker marker) {
        return addMarkers(() -> List.of(marker));
    }

    public MarkersBuilder addMarker(String id, Point2D location) {
        return addMarker(new LabelMarker(id, location, 1, simulationTime, cleanTime));
    }

    public MarkersBuilder addMarkers(Supplier<List<LabelMarker>> markers) {
        markerBuilders.add(markers);
        return this;
    }

    public MarkersBuilder addSimulationTime(long deltaTime) {
        simulationTime += deltaTime;
        return this;
    }

    public Map<String, LabelMarker> build() {
        Map<String, LabelMarker> markers = new HashMap<>();
        for (Supplier<List<LabelMarker>> builder : markerBuilders) {
            for (LabelMarker marker : builder.get()) {
                markers.put(marker.label(), marker);
            }
        }
        return markers;
    }

    public MarkersBuilder markers(GridTopology topology, String mapText) {
        return addMarkers(() ->
                RadarMapBuilder.parseMap(topology, mapText)
                        .filter(t ->
                                RadarMapBuilder.isMarker(t._2))
                        .map(t ->
                                new LabelMarker(t._2, t._1, 1, simulationTime, cleanTime))
                        .toList());
    }

    public MarkersBuilder simulationTime(long simulationTime) {
        this.simulationTime = simulationTime;
        return this;
    }
}
