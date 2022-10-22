package org.mmarini.wheelly.apis;

import java.awt.geom.Point2D;
import java.io.Closeable;
import java.util.Optional;

import static java.lang.Math.*;

/**
 * API Interface for robot
 */
public interface RobotApi extends Closeable {
    float OBSTACLE_SIZE = 0.2f;

    /**
     * Returns the move backward sensors
     */
    boolean getCanMoveBackward();

    /**
     * Returns the move forward sensors
     */
    boolean getCanMoveForward();

    /**
     * Returns the contact sensors
     */
    int getContacts();

    /**
     * Returns the time since last reset in millis
     */
    long getElapsed();

    /**
     * Returns the obstacle map if any
     */
    Optional<ObstacleMap> getObstaclesMap();

    /**
     * Returns the robot direction
     */
    int getRobotDir();

    /**
     * Returns the robot position
     */
    Point2D getRobotPos();

    /**
     * Returns the sensor direction in DEG
     */
    int getSensorDir();

    /**
     * Returns the sensor distance
     */
    float getSensorDistance();

    /**
     * Returns the obstacle location
     */
    default Optional<Point2D> getSensorObstacle() {
        float dist = getSensorDistance();
        if (dist > 0) {
            float d = dist + OBSTACLE_SIZE / 2;
            double angle = toRadians(90 - getRobotDir() - getSensorDir());
            Point2D pos = getRobotPos();
            float x = (float) (d * cos(angle) + pos.getX());
            float y = (float) (d * sin(angle) + pos.getY());
            return Optional.of(new Point2D.Float(x, y));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Returns the robot status
     */
    WheellyStatus getStatus();

    /**
     * Returns the robot time in millis
     */
    long getTime();

    /**
     * Halts the robot
     */
    void halt();

    /**
     * Moves robot to direction at speed
     *
     * @param dir   the directino in DEG
     * @param speed the speed in -1, 1 range
     */
    void move(int dir, float speed);

    /**
     * Resets the robot
     */
    void reset();

    /**
     * Moves the sensor to a direction
     *
     * @param dir the direction in DEG
     */
    void scan(int dir);

    /**
     * Starts the robot interface
     */
    void start();

    /**
     * Advances time by a time interval
     *
     * @param dt the interval in millis
     */
    void tick(long dt);

}
