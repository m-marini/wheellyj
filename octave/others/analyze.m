  FILE = "../kpi-simple.csv";
  X = csvread(FILE);
  REWARDS = X(:, 1);
  SCORES = X(:, 2);
  V0STAR = X(:, 3);
  DELTA = X(:, 4);
  AVG = X(:, 5);
  J0 = X(:, 6);
  J1 = X(:, 7);

  DIR_ALPHA = X(:, 9);
  SPEED_ALPHA = X(:, 10);
  SENS_ALPHA = X(:, 11);

  DIR_H = X(:, 14 : 37);
  SPEED_H = X(:, 38 : 58);
  SENS_H = X(:, 59 : 67);

  DIR_HSTAR = X(:, 70 : 93);
  SPEED_HSTAR = X(:, 94 : 114);
  SENS_HSTAR = X(:, 115 : 123);

  analyzeAgent(J0, J1, REWARDS, DELTA,
    DIR_ALPHA, DIR_HSTAR - DIR_H,
    SPEED_ALPHA, SPEED_HSTAR - SPEED_H,
    SENS_ALPHA, SENS_HSTAR - SENS_H);
#  plotPolicy(  HALT_H, DIR_H, SPEED_H, SENS_H);
