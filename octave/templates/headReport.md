## Head action

The strategy to determine the direction of the robot proxy sensor is provided by the agent by generating the
probabilities of selecting a given direction.

The maximum probability is an indicator of how much the agent generates deterministic behavior.

The minimum value of the maximum probability occurs when all directions are equally probable.

$P(a) = \frac{1}{n}, \forall a \in A$

With $n$ the number of head directions

For $n = 7$

$P(a) \approx 0.1429$.

In logarithmic scale it corresponde to

$\log_{10}(P(a)) = -\log_{10}(n) \approx -0.854$.

### Head log10 ratio chart

| # samples | mean | min | max |
|-----------|------|-----|-----|
