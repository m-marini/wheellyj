---

## Head action

The agent determines the robot head sensor direction by generating the probability distribution over the available directions.

The maximum probability indicates how deterministic the policy is.

The minimum value of the maximum probability occurs when all directions are equally probable.

$$
P(a) = \frac{1}{|A|}, \forall a \in A
$$

where $|A|$ is the number of available head directions.
