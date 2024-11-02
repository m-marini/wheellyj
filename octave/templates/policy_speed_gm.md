The strategy to determine the change in direction of the robot is provided by the agent by generating the probabilities of selecting a given speed.

The geometric mean of the probabilities is an indicator of how much the agent generates deterministic behavior .

This indicator tells us how the probability is distributed among the actions.

**The maximum value is 0.1**
 and occurs when all speed are equally probable

1 / n

ANN output layers are generally of the tanh -> softmax type, so the maximum value of the tanh layer is +1 and the minimum value -1.

**The minimum value is to 0.0145** determined by the temperature T=0.434 of softmax layer and the number of actions n=10 and indicates that the behaviours is quite deterministic.

e ^ (2 / n / T) / [e^(2 / T) + n - 1]
