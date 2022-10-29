#
# Plot the reward and abs(delta) of tdagent
#

clear all;
PATH = default_path();
REWARDS = tensor_read([PATH "/reward"]);
DELTA = tensor_read([PATH "/delta"]);
HALT = max(tensor_read([PATH "/policy.halt"]), [], 2);
DIR = max(tensor_read([PATH "/policy.direction"]), [], 2);

N = size(REWARDS)(1);

MODE = {"mean", "min", "max", "lin", "exp"};

LEN = max(round(N / 100), 1);
STRIDE = max((LEN / 2), 1);

subplot(121);
plot_trend(REWARDS, LEN, STRIDE, MODE, "Rewards");
title("Reward");

subplot(122);
plot_trend(abs(DELTA), LEN, STRIDE, MODE, "Delta");
title("DELTA");

