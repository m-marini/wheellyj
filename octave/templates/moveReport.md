## Move action

The strategy to determine the movement of the robot is provided by the agent by generating the probabilities of
selecting a given movement.

The maximum probability is an indicator of how much the agent generates deterministic behavior.

The minimum value of the maximum probability occurs when all movements are equally probable

$P(a) = \frac{1}{n}, \forall a \in A$

With $n$ the number of move actions

For $n = 1941$

$P(a) \approx 0.516 \times 10^{-3}$.

In logarithmic scale it corresponde to

$\log_{10}(P(a)) = -\log_{10}(n) \approx -3.288$.

### Move stats

| # samples | mean | min | max |
|-----------|------|-----|-----|
