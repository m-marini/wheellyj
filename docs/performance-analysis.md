# Performance analysis

# Rewards

The reward should increases and converge to the optimal maximum value.

# Delta

The delta indicates the error on estimation of advantage state value.
it should decrease and converge to zero value.

# V0

The estimation of advantage state value by the critic network should converge to the maximum average reward within the
reward range.

# Delta actions probabilities

The difference of probability of the selected action between the value after the training and the previous value
indicates the factor of correction of the policy network.
It should have the same sign of delta (note that the probability is limiteted to one values so the difference tents to
decrease as the pobability goes near the one value).

# Weight updates

The weight updates during the training should converge to zero as the netwrok fir the optimal behavior.
A convergence to a value not equals zero means the learing rate is to high.
