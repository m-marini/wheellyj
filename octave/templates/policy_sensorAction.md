The strategy to determine the direction of the robot proxy sensor is provided by the agent by generating the probabilities of selecting a given direction.

The maximum probability is an indicator of how much the agent generates deterministic behavior.

**The minimum value of the maximum probability is 0.14**.

It occurs when all directions are equally probable.

$\frac{1}{num\;movement} = \frac{1}{7}$

ANN output layers are generally of the tanh -> softmax type, so the maximum value of the tanh layer is +1 and the minimum value -1.

**The maximum value of the maximum probability is 0.94**.

It occurs when one direction has value 1 of the tanh layer and the others have values -1.

$\frac{e^\frac{2}{T} }{n - 1 + e^{\frac{2}{T}}}$
