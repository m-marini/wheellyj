function X = analyze(FILE = "../analysis-simple.csv")
  X = csvread(FILE);
  REWARDS = X(:, 1);
  SCORES = X(:, 2);
  V0STAR = X(:, 3);
  DELTA = X(:, 4);
  AVG = X(:, 5);
  J0 = X(:, 6);
  J1 = X(:, 7);
  HALT_ALPHA = X(:, 8);
  DIR_ALPHA = X(:, 9);
  SPEED_ALPHA = X(:, 10);
  SENS_ALPHA = X(:, 11);
  HALT_H = X(:, 12 : 13);
  DIR_H = X(:, 14 : 37);
  SPEED_H = X(:, 38 : 58);
  SENS_H = X(:, 59 : 67);
  analyzeAgent(J0, J1, REWARDS, DELTA);
endfunction

