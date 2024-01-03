package org.mmarini.wheelly.apis;

/**
 * Converts the simulation time and from remote time
 */
public interface ClockConverter {
    ClockConverter IDENTITY = new ClockConverter() {
        @Override
        public long fromRemote(long time) {
            return time;
        }

        @Override
        public long fromSimulation(long time) {
            return time;
        }
    };

    /**
     * Returns the identity converter
     */
    static ClockConverter identity() {
        return IDENTITY;
    }

    /**
     * Returns the simulation time from remote time
     *
     * @param time the remote time
     */
    long fromRemote(long time);

    /**
     * Returns the robot time from symulation time
     *
     * @param time the simulation time
     */
    long fromSimulation(long time);

}
