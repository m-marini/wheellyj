/*
 * Copyright (c) 2022-2025 Marco Marini, marco.marini@mmarini.org
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

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import org.mmarini.NotImplementedException;

import java.awt.geom.Point2D;
import java.io.IOException;

import static java.util.Objects.requireNonNull;

public class MockRobot implements RobotApi {
    public static final double CONTACT_RADIUS = 0.3;
    public static final int RECEPTIVE_ANGLE_DEG = 15;
    public static final double MAX_RADAR_DISTANCE = 3d;
    public static final RobotSpec ROBOT_SPEC = new RobotSpec(MAX_RADAR_DISTANCE, Complex.fromDeg(RECEPTIVE_ANGLE_DEG), CONTACT_RADIUS);
    public static final RobotStatusApi CONNECTED = new RobotStatusApi() {
        @Override
        public boolean configured() {
            return false;
        }

        @Override
        public boolean configuring() {
            return false;
        }

        @Override
        public boolean connected() {
            return true;
        }

        @Override
        public boolean connecting() {
            return false;
        }
    };
    public static final RobotStatusApi CONNECTING = new RobotStatusApi() {
        @Override
        public boolean configured() {
            return false;
        }

        @Override
        public boolean configuring() {
            return false;
        }

        @Override
        public boolean connected() {
            return false;
        }

        @Override
        public boolean connecting() {
            return true;
        }
    };
    public static final RobotStatusApi CONFIGURED = new RobotStatusApi() {
        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public boolean configuring() {
            return false;
        }

        @Override
        public boolean connected() {
            return true;
        }

        @Override
        public boolean connecting() {
            return false;
        }
    };
    public static final RobotStatusApi CONFIGURING = new RobotStatusApi() {
        @Override
        public boolean configured() {
            return false;
        }

        @Override
        public boolean configuring() {
            return true;
        }

        @Override
        public boolean connected() {
            return true;
        }

        @Override
        public boolean connecting() {
            return false;
        }
    };
    private static final RobotStatusApi UNCONNECTED = new RobotStatusApi() {
        @Override
        public boolean configured() {
            return false;
        }

        @Override
        public boolean configuring() {
            return false;
        }

        @Override
        public boolean connected() {
            return false;
        }

        @Override
        public boolean connecting() {
            return false;
        }
    };

    private final CompletableSubject closed;
    private final Point2D robotPos;
    private final Complex robotDir;
    private final PublishProcessor<WheellyMessage> messages;
    private final Flowable<CameraEvent> cameraEvents;
    private final Flowable<Throwable> errors;
    private final BehaviorProcessor<RobotStatusApi> robotLineStatus;
    private long simulationTime;

    public MockRobot(Point2D robotPos, Complex robotDir) {
        this.robotPos = requireNonNull(robotPos);
        this.robotDir = robotDir;
        this.closed = CompletableSubject.create();
        this.robotLineStatus = BehaviorProcessor.createDefault(UNCONNECTED);
        this.messages = PublishProcessor.create();
        this.cameraEvents = Flowable.empty();
        this.errors = Flowable.empty();
    }

    public MockRobot() {
        this(new Point2D.Float(), Complex.DEG0);
    }

    @Override
    public void close() throws IOException {
        closed.onComplete();
        robotLineStatus.onComplete();
        messages.onComplete();
    }

    @Override
    public void connect() {
        // Send the connection sequence
        Flowable.just(CONNECTING, CONNECTED, CONFIGURING, CONFIGURED)
                .subscribe(robotLineStatus::onNext);
    }

    @Override
    public boolean halt() {
        return true;
    }

    @Override
    public boolean isHalt() {
        return false;
    }

    @Override
    public boolean move(int dir, int speed) {
        return true;
    }

    @Override
    public Flowable<CameraEvent> readCamera() {
        return cameraEvents;
    }

    @Override
    public Flowable<Throwable> readErrors() {
        return errors;
    }

    @Override
    public Flowable<WheellyMessage> readMessages() {
        return messages;
    }

    @Override
    public Flowable<String> readReadLine() {
        throw new NotImplementedException();
    }

    @Override
    public Flowable<RobotStatusApi> readRobotStatus() {
        return robotLineStatus;
    }

    @Override
    public Flowable<String> readWriteLine() {
        throw new NotImplementedException();
    }

    @Override
    public void reconnect() {

    }

    @Override
    public RobotSpec robotSpec() {
        return ROBOT_SPEC;
    }

    @Override
    public boolean scan(int dir) {
        return true;
    }

    public MockRobot sendStatus(int count, long interval) {
        for (int i = 0; i < count; i++) {
            sendStatus(interval);
        }
        return this;
    }

    public MockRobot sendStatus(long time) {
        this.simulationTime += time;
        messages.onNext(new WheellyMotionMessage(
                System.currentTimeMillis(), simulationTime, simulationTime,
                robotPos.getX() / RobotStatus.DISTANCE_PER_PULSE,
                robotPos.getY() / RobotStatus.DISTANCE_PER_PULSE,
                robotDir.toIntDeg(),
                0, 0,
                0, true,
                0, 0,
                0, 0)
        );
        return this;
    }

    @Override
    public double simulationSpeed() {
        return 1;
    }

    @Override
    public long simulationTime() {
        return simulationTime;
    }
}
