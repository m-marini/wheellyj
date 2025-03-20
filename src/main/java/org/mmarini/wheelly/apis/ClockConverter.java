package org.mmarini.wheelly.apis;

/**
 * Converts the simulation markerTime and from remote markerTime
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
     * Returns the simulation markerTime from remote markerTime
     *
     * @param time the remote markerTime
     */
    long fromRemote(long time);

    /**
     * Returns the robot markerTime from symulation markerTime
     *
     * @param time the simulation markerTime
     */
    long fromSimulation(long time);

}
