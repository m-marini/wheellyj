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
 * Keeps the status og DL Agent
 *
 * @param network          the online network used for acting
 * @param trainingNetwork  the training network
 * @param trajectoryBuffer the trajectory buffer
 * @param trajectory       the training trajectory
 * @param training         true if it is training
 * @param averageReward    the average reward
 */
public record DLAgentStatus(ComputationGraph network,
                            ComputationGraph trainingNetwork,
                            TrajectoryBuffer trajectoryBuffer,
                            Trajectory trajectory,
                            boolean training, float averageReward)
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
     */
    public DLAgentStatus(ComputationGraph network, ComputationGraph trainingNetwork, TrajectoryBuffer trajectoryBuffer, Trajectory trajectory, boolean training, float averageReward) {
        this.network = requireNonNull(network);
        this.trajectoryBuffer = requireNonNull(trajectoryBuffer);
        this.trainingNetwork = trainingNetwork;
        this.trajectory = trajectory;
        this.training = training;
        this.averageReward = averageReward;
    }

    /**
     * Sets the average reward
     *
     * @param avgReward the average reward
     */
    public DLAgentStatus averageReward(float avgReward) {
        return this.averageReward != avgReward
                ? new DLAgentStatus(network, trainingNetwork, trajectoryBuffer, trajectory, training, avgReward)
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
     * Registers the observation and prepare for training
     * The return value contains the training network and the trajectory if it is ready to train.
     *
     * @param result the observation
     */
    public DLAgentStatus observe(ExecutionResult result) {
        trajectoryBuffer.add(result);
        if (!training && trajectoryBuffer.isFilled()) {
            // Ready to train
            Trajectory trajectory = trajectoryBuffer.trajectory();
            ComputationGraph trainingNetwork = network.clone();
            return new DLAgentStatus(network, trainingNetwork, trajectoryBuffer.clear(), trajectory, true, averageReward);
        }
        return new DLAgentStatus(network, null, trajectoryBuffer, null, training, averageReward);
    }

    /**
     * Stores the trained network and reset the trining flag
     *
     * @param network       the trained network
     * @param averageReward the average reward
     */
    public DLAgentStatus trained(ComputationGraph network, float averageReward) {
        return new DLAgentStatus(network, null, trajectoryBuffer, null, false, averageReward);
    }
}
