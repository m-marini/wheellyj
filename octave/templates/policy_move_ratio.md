The strategy to determine the movement of the robot is provided by the agent by generating the probabilities of selecting a given movement.

The ratio between the maximum probability and the geometric mean of the probabilities is an indicator of how much the agent generates deterministic behavior .

This indicator tells us how many times the maximum action is more likely than other actions.

The minimum value of the ratio is 1 and occurs when all movement are equally probable.

ANN output layers are generally of the tanh -> softmax type, so the maximum value of the tanh layer is +1 and the minimum value -1.

The maximum value of the ratio is determined by the temperature $T$ of softmax layer and the number of actions $n$.

$\frac{1}{e^{-2 \frac{n-1}{n T}}}$

