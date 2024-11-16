The strategy to determine the movement of the robot is provided by the agent by generating the probabilities of selecting a given movement.

The maximum probability is an indicator of how much the agent generates deterministic behavior.

**The minimum value of the maximum probability is $4.6 \cdot 10^{-3}$**.

It occurs when all movements are equally probable

$\frac{1}{num\;movement} = \frac{1}{216}$

ANN output layers are generally of the tanh -> softmax type, so the maximum value of the tanh layer is +1 and the minimum value -1.

**The maximum value of the maximum probability is 0.82**.

It occurs when one action has value 1 of the tanh layer and the others have values -1 and depend on number of actions $n=216$ and temperature of softmax layer $T=0.29$.

$\frac{e^\frac{2}{T} }{n - 1 + e^{\frac{2}{T}}}$
