The strategy to determine the change in direction of the robot is provided by the agent by generating the probabilities of selecting a given direction.

The maximum probability is an indicator of how much the agent generates deterministic behavior.

**The minimum value of the maximum probability is 0.042**.

It occurs when all directions are equally probable

1 / num of speeds = 1 / 24

ANN output layers are generally of the tanh -> softmax type, so the maximum value of the tanh layer is +1 and the minimum value -1.

**The maximum value of the maximum probability is 0.81**.

It occurs when one direction has value 1 of the tanh layer and the others have values -1.

e^(2 / T) / (n - 1 + e^(2 /T))