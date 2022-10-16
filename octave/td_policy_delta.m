#clear all;
PATH = "../data/agent-30e-3";
HALT = tensor_read([PATH "/policy.halt"]);
DIR = tensor_read([PATH "/policy.direction"]);
SENS = tensor_read([PATH "/policy.sensorAction"]);
SPEED = tensor_read([PATH "/policy.speed"]);

HALT1 = tensor_read([PATH "/trainedPolicy.halt"]);
DIR1 = tensor_read([PATH "/trainedPolicy.direction"]);
SENS1 = tensor_read([PATH "/trainedPolicy.sensorAction"]);
SPEED1 = tensor_read([PATH "/trainedPolicy.speed"]);

D_HALT = max(abs(HALT1 - HALT) ./ HALT, [], 2);
D_DIR = max(abs(DIR1 - DIR) ./ DIR, [], 2);
D_SPEED = max(abs(SPEED1 - SPEED) ./ SPEED, [], 2);
D_SENS = max(abs(SENS1 - SENS) ./ SENS, [], 2);

N = size(HALT)(1);

MODE = {"mean", "min", "max", "lin", "exp"};

LEN = max(round(N / 100), 1);
STRIDE = max((LEN / 2), 1);

subplot(221);
plot_trend(D_HALT, LEN, STRIDE, MODE);
title("\Delta Halt prob. (%)");

subplot(222);
plot_trend(D_DIR, LEN, STRIDE, MODE);
title("\Delta direction prob. (%)");

subplot(223);
plot_trend(D_SPEED, LEN, STRIDE, MODE);
title("\Delta speed prob. (%)");

subplot(224);
plot_trend(D_SENS, LEN, STRIDE, MODE);
title("\Delta sensor prob. (%)");
