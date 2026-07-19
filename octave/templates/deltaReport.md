---

## Deltas

In the average-reward setting, the quality of a policy is defined as the average rate of reward, or simply average reward, while following that policy.

Returns are defined in terms of differences between rewards and the average reward.

This is known as the differential return, and the corresponding value functions are known as differential value functions.

The TD error $\delta$ measures the difference between the estimated differential value of $s_t$ and the TD target $R(t+1) + V(s_{t+1})$.

