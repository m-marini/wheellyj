/*
 * Copyright 2026 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
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
 * END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.rl.agents;

import org.deeplearning4j.nn.graph.ComputationGraph;
import org.mmarini.rl.envs.ExecutionResult;

import static java.util.Objects.requireNonNull;

/**
 * DLAgentStatus is the state container of the Deep Learning agent.
 * <p>It keeps track of the active neural network, the training network, collected trajectory data,
 * training status, reward statistics, learning activation, and shutdown requests.
 * Its observe() method coordinates the transition from data collection to training,
 * while trained() restores the agent to its normal observation phase with the newly trained network.
 * </p>
 *
 * @param network          the online network used for acting
 * @param trainingNetwork  temporary copy of the network used during training
 * @param trajectoryBuffer the trajectory buffer
 * @param trajectory       the training trajectory
 * @param training         true if the agent is currently training
 * @param averageReward    the agent's current average reward
 * @param shuttingDown     true if a shutdown of the agent has been requested.
 * @param learning         true if the learning process is enabled.
 */
public record DLAgentStatus(ComputationGraph network,
                            ComputationGraph trainingNetwork,
                            TrajectoryBuffer trajectoryBuffer,
                            Trajectory trajectory,
                            boolean training, float averageReward, boolean shuttingDown, boolean learning)
        implements AutoCloseable {
    /**
     * Creates the status
     *
     * @param network          the online network used for acting
     * @param trainingNetwork  the training network
     * @param trajectoryBuffer the trajectory buffer
     * @param trajectory       the training trajectory
     * @param training         true if it is training
     * @param averageReward    the average reward
     * @param shuttingDown     true if shut down requested
     * @param learning         true if learning activated
     */
    public DLAgentStatus(ComputationGraph network, ComputationGraph trainingNetwork, TrajectoryBuffer trajectoryBuffer, Trajectory trajectory, boolean training, float averageReward, boolean shuttingDown, boolean learning) {
        this.network = requireNonNull(network);
        this.trajectoryBuffer = requireNonNull(trajectoryBuffer);
        this.trainingNetwork = trainingNetwork;
        this.trajectory = trajectory;
        this.training = training;
        this.averageReward = averageReward;
        this.shuttingDown = shuttingDown;
        this.learning = learning;
    }

    /**
     * Sets the average reward
     *
     * @param avgReward the average reward
     */
    public DLAgentStatus averageReward(float avgReward) {
        return this.averageReward != avgReward
                ? new DLAgentStatus(network, trainingNetwork, trajectoryBuffer, trajectory, training, avgReward, shuttingDown, learning)
                : this;
    }

    @Override
    public void close() {
        trajectoryBuffer.close();
        network.close();
        if (trainingNetwork != null) {
            trainingNetwork.close();
        }
        if (trajectory != null) {
            trajectory.close();
        }
    }

    /**
     * Sets the learning flag
     *
     * @param learning true if learning activated
     */
    public DLAgentStatus learning(boolean learning) {
        return this.learning == learning
                ? this
                : new DLAgentStatus(network, trainingNetwork, trajectoryBuffer, trajectory, training, averageReward, shuttingDown, learning);
    }

    /**
     * Registers the observation and prepare for training
     * The return value contains the training network and the trajectory if it is ready to train.
     *
     * @param result the observation
     */
    public DLAgentStatus observe(ExecutionResult result) {
        trajectoryBuffer.add(result);
        // Checks for training ready (not yet training and buffer full and learning activated
        if (!training && trajectoryBuffer.isFilled() && learning) {
            // Ready to train
            Trajectory trajectory = trajectoryBuffer.createTrajectory();
            ComputationGraph trainingNetwork = network.clone();
            return new DLAgentStatus(network, trainingNetwork, trajectoryBuffer.clear(), trajectory, true, averageReward, shuttingDown, learning);
        }
        return new DLAgentStatus(network, null, trajectoryBuffer, null, training, averageReward, shuttingDown, learning);
    }

    /**
     * Sets the shuttingDown flag
     *
     * @param shuttingDown true if shutdown requested
     */
    public DLAgentStatus shuttingDown(boolean shuttingDown) {
        return this.shuttingDown == shuttingDown
                ? this
                : new DLAgentStatus(network, trainingNetwork, trajectoryBuffer, trajectory, training, averageReward, shuttingDown, learning);
    }

    /**
     * After training has finished, the method:
     * <ul>
     * <li>creates a new status containing the newly trained network</li>
     * <li>resets the training state</li>
     * <li>the new network becomes the active online network.</li>
     * </ul>
     *
     * @param network       the trained network
     * @param averageReward the average reward
     */
    public DLAgentStatus trained(ComputationGraph network, float averageReward) {
        return new DLAgentStatus(network, null, trajectoryBuffer, null, false, averageReward, shuttingDown, learning);
    }
}
