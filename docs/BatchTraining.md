The batch training is based on kpi collected during online training.

The necessary kpis are:
- `s0` the status inputs at $t_0$
- `s1` the status inputs at $t_1$
- `reward` the reward after $t_0$
- `terminal` the terminal status flag after $t_0$
- `actions` the actions after $t_0$

```
-l s0,s1,reward,terminal,actions
```

# Training

The training consists of cycles training each composed by two phases.

In the first phase begins the outputs of the critic and the
actor of each sample is calculated.
Then the expected outputs are calculated from the rewards.

In the second phase the training of the networks with weights
correction are perfomed to reduce the error.
