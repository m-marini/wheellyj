The strategy to determine the speed of the robot movement is provided by the agent by generating the probabilities of selecting a given speed.

The maximum probability is an indicator of how much the agent generates deterministic behavior.

**The minimum value of the maximum probability is 0.1**.

It occurs when all speeds are equally probable.

1 / num of speeds = 1 / 10

ANN output layers are generally of the tanh -> softmax type, so the maximum value of the tanh layer is +1 and the minimum value -1.

**The maximum value of the maximum probability is 0.92**.

It occurs when one direction has value 1 of the tanh layer and the others have values -1.

e^(2 / T) / (n - 1 + e^(2 /T))