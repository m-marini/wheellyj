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

