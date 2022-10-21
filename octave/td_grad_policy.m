clear all;
GAMMA = 0.99;
PATH = default_path();
HALT = max(abs(tensor_read([PATH "/gradPolicy.halt"])), [], 2);
DIR = max(abs(tensor_read([PATH "/gradPolicy.direction"])), [], 2);
SENS = max(abs(tensor_read([PATH "/gradPolicy.sensorAction"])), [], 2);
SPEED = max(abs(tensor_read([PATH "/gradPolicy.speed"])), [], 2);

N = size(HALT, 1);
MODE = {"mean", "min", "max", "lin", "exp"};

LEN = max(round(N / 100), 1);
STRIDE = max((LEN / 2), 1);

subplot(221);
plot_trend(HALT, LEN, STRIDE, MODE);
title("Halt max gradient");

subplot(222);
plot_trend(DIR, LEN, STRIDE, MODE);
title("Direction max gradient");

subplot(223);
plot_trend(SPEED, LEN, STRIDE, MODE);
title("Speed max gradient");

subplot(224);
plot_trend(SENS, LEN, STRIDE, MODE);
title("Sensor max gradient");
