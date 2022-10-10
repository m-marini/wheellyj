clear all;
PATH = "../data/kpi";
REWARDS = tensor_read([PATH "/reward"]);
DELTA= tensor_read([PATH "/delta"]);

subplot(1, 2, 1);
plotTrend(REWARDS, true, 300);
title("Reward");

subplot(1, 2, 2);
plotTrend(DELTA, true, 300);
title("Delta");
