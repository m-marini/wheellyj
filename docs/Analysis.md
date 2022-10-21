# Analysis of performance

Weelly application can produce the kpis of training session by 
setting `-k <path>` command line option.

```
java org.mmarini.wheelly.app.Wheelly -k <path>
```

## Reward

The main indicator of an agent's performance is the reward.
The agent's goal is to maximize average rewards over the long time.


The octave program `td_plot_perf.m` plots the reward statistic in a variable time window.
The program plots the average, minimum, maximum values, the linear and possibly exponential trend (regression).

__A decreasing trend indicates that the agent does not find the optimal solution and is behaving worse and worse.
Possible causes of this behavior are the incorrect learning rate `criticAlpha` and` policyAlpha` parameters or the `lambda` parameter of the temporal difference.__

## Correction factor $\delta_i$

The other parameter ploted by octave program is the `delta` correction factor.

During training, the agent calculates the `delta` correction value by analyzing the reward received and the reward expected by the critic or the difference of residual advantage between the state before and after the action performed.

The `delta` is used to modify the parameters of the network (the weights of the connections), reinforcing or weakening the action performed if the reward is greater or less than expected.

The `delta` factor indicates how much the agent is correcting the neural networks, so a __large `delta` value indicates that the agent is still looking for the best behavior, while a small value indicates that the agent has found a solution and is refining it__.


### Learing rate `criticAlpha` $\alpha_c$

The `criticAlpha` parameter controls the amount of changes in the agent's critical neural network.
The critic estimates the agent's behaviors by predicting the residual advantage value of an environmental state.

__A small value of `criticAlpha` produces small changes on the neural network and can converge towards the optimal solution, but requires a large number of steps increasing the learning time.__

On the other hand, __a great value produces great changes by reducing the number of steps required to find the optimal solution but may fail to converge because it cannot modify the network in a fine way.__

A wrong prediction causes bad `delta` correction values and wrong behaviors.

### Learing rate `policyAlpha` $\alpha_p$

The `policyAlpha` parameter controls the amount of changes in the agent actor's neural network.
The actor estimates the probability distributions of the actions to be performed in a given environmental state.

The changes are proportional to `policyAlpha`, __a small value produces small changes and can converge towards optimal solutions, but requires many steps increasing the training time; a large value produces large changes by reducing the number of steps but may not converge towards a solution__.

### Temporal difference parameter `lambda` TD($\lambda$)

The `lambda` parameter modulates the decay of the eligibility traces.
Eligibility traces track network changes over time allowing weighted corrections to past effects.
They somehow simulate the Hebb effect of neuronal synapses.


A zero value of `lambda` generates the effects of the TD(0) algorithm so only immediate effects are used to correct the network.
A value of 1 generates the effect of the Montecarlo algorithm with averaged effects over infinite time.
In practice, values ​​lower than 1 are used.

__Too high values weighs more on past events, obscuring the short-term effects.__

## Policy statistics

The `td_plot_policy.m` octave program plots the maximum probability of actions during the training session.

If agent discover a deterministic best behavior the max probability should increase to the value 1.

__Gradually lower values ​​indicate instead that the agent has not found a deterministic behavior that maximizes the reward and therefore randomly selects the actions.__

## Policy `delta_pi` $|\Delta\pi(a)|$

The `td policy delta.m` octave program plots the maximum change in the probability of actions, i.e. the difference of probability between before and after the training.

This value indicates how much the agent's action strategy has changed.

__Low values indicate small variations in the strategy__.
This may be due to reaching an optimal solution or too low `policyAlpha` learning parameter value.

## Critic statistics

The `td_plot_critic.m` octave program plots the estimation of advantage residual value by the critic agent.

The critic's goal is to find the behavior that maximizes this value.

__A decreasing trend in value indicates that none of the behaviors implemented resulted in improvements in rewards__.

## Dynamic analysis


The analysis of the dynamics of learning takes in to account how the agent's indicators and policy vary over time.

Training an agent occurs with a cycle of interactions between environment and agent.
At first the agent has no knowledge of decision making to learn and thus generates random actions.

The rewards received which naturally depend on the actions taken by the agent can be positive, negative or null.

The agent changes its behavior by strengthening the actions that produce positive rewards and weaken the actions that produce negative rewards.

As the training progresses the agent should then select the actions that produce positive rewards and therefore the discount return should increase.

A first indicator of the dynamics of learning can be the distribution of the discount rewards in the training interval.

__The median of the discount return tells us, for example, the threshold for which the agent has received a discount rewards lower and higher than this threshold for the same time.__

The `td dyna.m` octave program plots the discount rewards and the distribution of discount rewards.

The time intervals in which the discount return exceeds a certain threshold can also be interesting.

The time elapsed from the start of training and the first time the discount return exceeds a certain threshold can give an indication of the learning rate.

But exceeding the threshold cannot alone be considered an indicator of improvement, it could in fact be a completely random or temporary fact.
It is therefore also necessary to analyze the time in which the average rewrds remains above the threshold and the frequency with which it exceeds such threshold.

## Analisi dei gradienti