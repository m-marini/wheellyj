package org.mmarini.wheelly.apis;

import io.reactivex.rxjava3.core.Flowable;

/**
 *
 */
public interface WithCommandFlowable {

    /**
     * Returns the flowable of commands
     */
    Flowable<RobotCommands> readCommand();

}
