package org.mmarini.wheelly.apis;

import java.util.function.Consumer;

/**
 *
 */
public interface WithCommandCallback {

    /**
     * Registers the consumer of commands
     *
     * @param callback the callback
     */
    void setOnCommand(Consumer<RobotCommands> callback);

}
