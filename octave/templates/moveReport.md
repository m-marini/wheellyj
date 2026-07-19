---

## Move action

The agent determines the robot movement strategy by generating the probability distribution over the available actions.

The maximum action probability is an indicator of policy determinism.

The minimum value of the maximum probability occurs when all movements are equally probable.

For a uniform policy, every action has the same probability.

$$
P(a) = \frac{1}{|A|}, \forall a \in A
$$

where $|A|$ is the number of available move actions.

For $|A| = 1941$

$$
P(a) \approx 5.16 \times 10^{-4}$$.

In logarithmic scale it corresponds to

$$
\log_{10}(P(a)) = -\log_{10}(|A|) \approx -3.288
$$.

The report displays logarithmic values together with their corresponding probability values.

