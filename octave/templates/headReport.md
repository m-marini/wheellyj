---

## Head action

The agent determines the robot head sensor direction by generating the probability distribution over the available directions.

The maximum probability indicates how deterministic the policy is.

The minimum value of the maximum probability occurs when all directions are equally probable.

$$
P(a) = \frac{1}{|A|}, \forall a \in A
$$

where $|A|$ is the number of available head directions.

For $|A| = 7$

$$
P(a) \approx 0.1429
$$.

In logarithmic scale it corresponds to

$$
\log_{10}(P(a)) = -\log_{10}(|A|) \approx -0.854
$$.

The report displays logarithmic values together with their corresponding probability values.

