clear all;
N = 1;
MAX_SPEED = 50;
DATA_FILE = "motorData-1.csv";

function Y = fdis(X, k)
  Y = aprox(X, profile(k'));
endfunction

function ERR = sme(func, X, Y, k)
  ERR = mean((func(X, k) - Y).^2);
endfunction

seed = zeros(N * 4, 1);
seed(1 : N) = [1 : N] / (N + 1) - 1;
seed(N + 1 : 2 * N) = [1 : N] / (N + 1);
seed(2 * N + 1 :3 *  N) = [1 : N] / (N + 1) - 1;
seed(3 * N + 1 : end) = [1 : N] / (N + 1);

f2 = @(X, k) fdis(X, k);

data = csvread(DATA_FILE);

lSignal = data(:,1);
rSignal = data(:,2);

lSpeed = data(:,3) / MAX_SPEED;
rSpeed = data(:,4) / MAX_SPEED;

lObj = @(k) sme(f2, lSignal, lSpeed, k);
rObj = @(k) sme(f2, rSignal, rSpeed, k);

[lk, lerr] = sqp(seed, lObj, [], @(k) isValidProfile(k'));
[rk, rerr] = sqp(seed, rObj, [],  @(k) isValidProfile(k'));

lProfile = profile(lk')(:, [2,1])
lerr
rProfile = profile(rk')(:, [2,1])
rerr

subplot(2,1,1);
fplot(@(x) aprox(x, lProfile), [-1 1]);
hold on
scatter(lSpeed, lSignal)
hold off
grid on

subplot(2,1,2);
fplot(@(x) aprox(x, rProfile), [-1 1]);
hold on
scatter(rSpeed, rSignal)
hold off
grid on
