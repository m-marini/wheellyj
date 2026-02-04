/*
 * Copyright (c) 2022-2026 Marco Marini, marco.marini@mmarini.org
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
import org.jbox2d.common.Vec2;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

import static java.lang.Math.*;

/**
 * Utilities functions
 */
public interface Utils {
    double SIN_1DEG = sin(toRadians(1));
    Logger logger = LoggerFactory.getLogger(Utils.class);
    double MM = 1e-3;

    static double clip(double value, double min, double max) {
        return Math.min(Math.max(value, min), max);
    }

    static float clip(float value, float min, float max) {
        return Math.min(Math.max(value, min), max);
    }

    static int clip(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    /**
     * Returns a random number with exponential distribution probability
     *
     * @param random the random generator
     * @param mean   the mean value
     */
    static long expRandom(Random random, long mean) {
        double u = random.nextDouble();
        return round(-mean * log(1 - u));
    }

    static float linear(float x, float xmin, float xmax, float ymin, float ymax) {
        return (x - xmin) * (ymax - ymin) / (xmax - xmin) + ymin;
    }

    static double linear(double x, double xmin, double xmax, double ymin, double ymax) {
        return (x - xmin) * (ymax - ymin) / (xmax - xmin) + ymin;
    }

    static int[] loadIntArray(JsonNode root, Locator locator) {
        return !locator.getNode(root).isMissingNode()
                ? locator.elements(root)
                .mapToInt(l ->
                        l.getNode(root).asInt()
                ).toArray()
                : null;
    }

    /**
     * Returns the string array located in the JSON document
     *
     * @param root    the json document
     * @param locator the locator
     */
    static String[] loadStringArray(JsonNode root, Locator locator) {
        return !locator.getNode(root).isMissingNode()
                ? locator.elements(root)
                .map(l ->
                        l.getNode(root).asText()
                ).toArray(String[]::new)
                : null;
    }

    /**
     * Returns the distance in millimeters from meters
     *
     * @param distance the distance (m)
     */
    static int m2mm(double distance) {
        return (int) round(distance / MM);
    }

    /**
     * Returns the distance in meters from millimeters
     *
     * @param distance the distance (mm)
     */
    static double mm2m(int distance) {
        return distance * MM;
    }

    static Vec2 vec2(float[] x) {
        return vec2(x[0], x[1]);
    }

    static Vec2 vec2(float x, float y) {
        Vec2 vec2 = new Vec2();
        vec2.x = x;
        vec2.y = y;
        return vec2;
    }

    static Vec2 vec2(double[] x) {
        return vec2(x[0], x[1]);
    }

    static Vec2 vec2(double x, double y) {
        return vec2((float) x, (float) y);
    }
}
