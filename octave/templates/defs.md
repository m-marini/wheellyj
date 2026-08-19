---

## Policy Entropy Definition

The policy entropy is an indicator of how much the policy is exploring rather than exploiting.

$$
H = - \frac{1}{\ln |A|}\sum_{a \in A} \pi(a \mid s_t) \ln(\pi(a \mid s_t))
$$

This normalization ensures $H \in [0, 1].$

- The minimum value (H = 0) occurs when a single action is certain.
- The maximum value (H = 1) occurs when the policy is uniform over all actions:
$P(a) = \frac{1}{|A|}, \forall a \in A$

Entropy values close to 1 indicate a near-uniform policy.
Entropy values close to 0 indicate a near-deterministic policy.

