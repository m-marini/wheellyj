clear all;
PATH = "../data/agent-30e-3";
HALT = max(tensor_read([PATH "/policy.halt"]), [], 2);
DIR = max(tensor_read([PATH "/policy.direction"]), [], 2);
SENS = max(tensor_read([PATH "/policy.sensorAction"]), [], 2);
SPEED = max(tensor_read([PATH "/policy.speed"]), [], 2);

N = size(HALT)(1);

MODE = {"mean", "min", "max", "lin", "exp"};

LEN = max(round(N / 100), 1);
STRIDE = max((LEN / 2), 1);

subplot(221);
plot_trend(HALT, LEN, STRIDE, MODE);
title("Halt max prob.");

subplot(222);
plot_trend(DIR, LEN, STRIDE, MODE);
title("Direction max prob");

subplot(223);
plot_trend(SPEED, LEN, STRIDE, MODE);
title("Speed max prob");

subplot(224);
plot_trend(SENS, LEN, STRIDE, MODE);
title("Sensor max prob");
