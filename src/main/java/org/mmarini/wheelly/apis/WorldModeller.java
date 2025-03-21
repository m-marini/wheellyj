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

package org.mmarini.wheelly.apis;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Models the world by interpreting the event flow
 */
public class WorldModeller implements WorldModellerApi {

    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/world-modeller-schema-0.1";

    /**
     * Returns the world map modeller
     *
     * @param root the document
     * @param file the locator of radar map definition
     */
    public static WorldModeller create(JsonNode root, File file) {
        return create(root, Locator.root());
    }

    /**
     * Returns the world map modeller
     *
     * @param root    the document
     * @param locator the locator of radar map definition
     */
    public static WorldModeller create(JsonNode root, Locator locator) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        RadarModeller radarModeller = RadarModeller.create(root, locator);
        PolarMapModeller polarModeller = PolarMapModeller.create(root, locator);
        MarkerLocator markerModeller = MarkerLocator.create(root, locator);
        int gridSize = locator.path("gridMapSize").getNode(root).asInt();
        return new WorldModeller(radarModeller, polarModeller, markerModeller, gridSize);
    }

    /**
     * Returns the world modeller loaded from the modeller configuration file
     *
     * @param file the filename
     * @throws IOException in case of error
     */
    public static WorldModeller fromFile(File file) throws IOException {
        return Utils.createObject(file);
    }

    private final RadarModeller radarModeller;
    private final PolarMapModeller polarModeller;
    private final MarkerLocator markerLocator;
    private final PublishProcessor<Tuple2<WorldModel, RobotCommands>> inferenceProcessor;
    private WorldModelSpec worldSpec;
    private WorldModel currentModel;
    private Function<WorldModel, RobotCommands> onInference;
    private RobotControllerConnector controller;

    /**
     * Creates the world modeller
     *
     * @param radarModeller the radar modeller
     * @param polarModeller the polar map modeller
     * @param markerLocator the marker locator
     * @param gridSize      the grid map size
     */
    public WorldModeller(RadarModeller radarModeller, PolarMapModeller polarModeller, MarkerLocator markerLocator, int gridSize) {
        this.radarModeller = requireNonNull(radarModeller);
        this.polarModeller = requireNonNull(polarModeller);
        this.worldSpec = new WorldModelSpec(null, polarModeller.numSectors(), gridSize, markerLocator.markerSize());
        this.markerLocator = requireNonNull(markerLocator);
        this.inferenceProcessor = PublishProcessor.create();
    }

    @Override
    public WorldModel clearRadarMap() {
        WorldModel model = currentModel;
        model = model.setRadarMap(model.radarMap().clean());
        currentModel = model;
        return model;
    }

    @Override
    public WorldModeller connectController(RobotControllerConnector controller) {
        this.controller = requireNonNull(controller);
        controller.setOnLatch(this::onLatch);
        controller.setOnInference(this::onInference);
        return this;
    }

    /**
     * Handles the robot status inference
     *
     * @param robotStatus the robot status
     */
    public void onInference(RobotStatus robotStatus) {
        WorldModel model = this.updateForInference(this.currentModel);
        if (onInference != null) {
            RobotCommands commands = onInference.apply(model);
            controller.execute(commands);
            inferenceProcessor.onNext(Tuple2.of(model, commands));
        }
    }

    /**
     * Handles the robot status latch
     *
     * @param robotStatus the robot status
     */
    void onLatch(RobotStatus robotStatus) {
        WorldModel model = currentModel;
        if (model == null) {
            RadarMap radarMap = radarModeller.empty();
            Map<String, LabelMarker> markers = Map.of();
            model = new WorldModel(worldSpec, robotStatus, radarMap, markers, null, null, null, null, false);
        }
        model = this.updateStatus(model, robotStatus);
        currentModel = model;
    }

    @Override
    public Flowable<Tuple2<WorldModel, RobotCommands>> readInference() {
        return inferenceProcessor;
    }

    @Override
    public WorldModeller setOnInference(Function<WorldModel, RobotCommands> onInference) {
        this.onInference = onInference;
        return this;
    }

    /**
     * Sets the robot specification
     *
     * @param robotSpec the robot specification
     */
    public WorldModeller setRobotSpec(RobotSpec robotSpec) {
        this.worldSpec = worldSpec.setRobotSpec(robotSpec);
        return this;
    }

    /**
     * Returns the new world model updated for inference purpose
     * It creates the polar map
     *
     * @param model the model
     */
    public WorldModel updateForInference(WorldModel model) {
        RobotStatus robotStatus = model.robotStatus();
        PolarMap polarMap = polarModeller.create(model.radarMap(), robotStatus.location(), robotStatus.direction(), robotStatus.robotSpec().maxRadarDistance());
        GridMap gridMap = GridMap.create(model.radarMap(), robotStatus.location(), robotStatus.direction(), worldSpec.gridSize());
        return model.setPolarMap(polarMap).setGridMap(gridMap);
    }

    /**
     * Returns the new world model updated by current robot status
     * It updates just the robot status, the markers and the radar map in the world model
     *
     * @param model  the current world model
     * @param status the current robot status
     */
    public WorldModel updateStatus(WorldModel model, RobotStatus status) {
        RadarMap newRadarMap = radarModeller.update(model.radarMap(), status);
        model = model.setRobotStatus(status).setRadarMap(newRadarMap);
        CameraEvent camera = status.cameraEvent();
        WheellyProxyMessage proxy = status.proxyMessage();
        CameraEvent prevCamera = model.prevCameraEvent();
        WheellyProxyMessage prevProxy = model.prevProxyMessage();
        if (!Objects.equals(camera, prevCamera)) {
            // Camera event changed
            if (Objects.equals(proxy, prevProxy)) {
                // new camera, proxy not changed: store camera and set wait for first proxy
                return model.setPrevCameraEvent(camera)
                        .setWaitingForProxy(true);
            } else {
                // new camera event, new proxy: store camera event and proxy event and reset wait for first proxy
                Map<String, LabelMarker> newMarker = markerLocator.update(model.markers(), camera, proxy, status.robotSpec());
                return model.setMarkers(newMarker)
                        .setPrevCameraEvent(camera)
                        .setPrevProxyMessage(proxy)
                        .setWaitingForProxy(false);
            }
        } else if (!Objects.equals(proxy, prevProxy)) {
            // proxy event changed
            if (model.waitingForProxy()) {
                // camera isn't changed, proxy changed, wait for first proxy: store proxy reset wait for first proxy
                Map<String, LabelMarker> newMarker = markerLocator.update(model.markers(), camera, proxy, status.robotSpec());
                return model.setMarkers(newMarker).setPrevProxyMessage(proxy)
                        .setWaitingForProxy(false);
            } else {
                // camera isn't changed, proxy changed, not wait for first proxy: store proxy clean markers
                return model.setPrevProxyMessage(proxy);
            }
        }
        return model;
    }

    @Override
    public WorldModelSpec worldModelSpec() {
        return worldSpec;
    }

}
