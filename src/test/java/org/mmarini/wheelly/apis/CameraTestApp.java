/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
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

import java.io.IOException;

class CameraTestApp {
    private static final Logger logger = LoggerFactory.getLogger(CameraTestApp.class);

    public static void main(String[] arg) {
        new CameraTestApp().run();
    }

    private void onCamera(CameraEvent cameraEvent) {
        logger.atInfo().log("{}", cameraEvent);
    }

    private void run() {
        String host = "localhost";
        int port = 8100;
        long connectionTimeout = 10000;
        long readTimeout = 3000;
        try (Camera camera = new Camera(host, port, connectionTimeout, readTimeout)) {
            camera.setOnCamera(this::onCamera);
            camera.connect();
            for (int i = 0; i < 10; i++) {
                camera.tick(100);
            }
        } catch (IOException e) {
            logger.atError().setCause(e).log("Error");
        }
    }
}