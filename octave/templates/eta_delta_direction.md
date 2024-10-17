Proximal Policy Optimization (PPO)
methods try to maximize a surrogate objective limiting the changes of the probability ratio that would lead to an excessively large policy update.

The amounts of adjusting weights are proportional to the TD error (delta) by the learning parameter (eta) by the direction policy learning rate (alpha).

The direction error RMS is the root mean square of the adjusting weights and indicates the amount of changes from the direction output layer.

Values tending towards zero means that the ANN undergoes minimal variations in weights.