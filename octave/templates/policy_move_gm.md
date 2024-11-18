The strategy to determine the change in direction of the robot is provided by the agent by generating the probabilities of selecting a given direction.

The geometric mean of the probabilities is an indicator of how much the agent generates deterministic behavior .

This indicator tells us how the probability is distributed among the actions.

The maximum value occurs when all directions are equally probable

$\frac{1}{n}$

ANN output layers are generally of the tanh -> softmax type, so the maximum value of the tanh layer is +1 and the minimum value -1.

The minimum value is determined by the temperature $T$ of softmax layer and the number of actions $n$ and indicates that the behaviours is quite deterministic.

$\frac{e^\frac{2}{n  T}}{e^\frac{2}{T} + n - 1}$

