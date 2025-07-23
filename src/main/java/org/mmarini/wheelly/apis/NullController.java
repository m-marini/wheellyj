package org.mmarini.wheelly.apis;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import org.mmarini.NotImplementedException;

import java.util.function.Consumer;

import static io.reactivex.rxjava3.core.Completable.complete;
import static io.reactivex.rxjava3.core.Flowable.empty;

/**
 * Null controller throws not implemented exception.
 * It is used only to local environment for batch training.
 */
public class NullController implements RobotControllerApi {
    @Override
    public RobotControllerApi connectRobot(RobotApi robot) {
        return this;
    }

    @Override
    public void execute(RobotCommands command) {
    }

    @Override
    public Flowable<RobotCommands> readCommand() {
        throw new NotImplementedException();
    }

    @Override
    public Flowable<RobotControllerStatusApi> readControllerStatus() {
        throw new NotImplementedException();
    }

    @Override
    public Flowable<Throwable> readErrors() {
        throw new NotImplementedException();
    }

    @Override
    public Flowable<String> readReadLine() {
        throw new NotImplementedException();
    }

    @Override
    public Flowable<RobotStatus> readRobotStatus() {
        return empty();
    }

    @Override
    public Completable readShutdown() {
        return complete();
    }

    @Override
    public void reconnect() {
    }

    @Override
    public Flowable<String> readWriteLine() {
        return empty();
    }

    @Override
    public void setOnInference(Consumer<RobotStatus> callback) {
    }

    @Override
    public void setOnLatch(Consumer<RobotStatus> callback) {
    }

    @Override
    public void shutdown() {
    }

    @Override
    public double simRealSpeed() {
        return 1;
    }

    @Override
    public void start() {
    }
}
