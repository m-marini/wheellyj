clear all;
GAMMA = 0.999;
PATH = default_path();
REWARDS = tensor_read([PATH "/reward"]);
DISCOUNT = discount(REWARDS, GAMMA);
MR = median(DISCOUNT);

N = size(DISCOUNT, 1);
MODE = {"mean", "min", "max", "lin", "exp"};

LEN = max(round(N / 100), 1);
STRIDE = max((LEN / 2), 1);

subplot(121);
plot_trend(DISCOUNT, LEN, STRIDE, MODE);
text(1, MR, sprintf("median %0.2g", MR));
title("Discount rewards");

subplot(122);
hist(DISCOUNT, 20);
grid on;
title("Discount rewards distribution");

