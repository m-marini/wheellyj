function [Y1 P] = linearRegression(Y)
  X = [1 : size(Y, 1)]';
  Sxy = cov(X, Y);
  Sxx = cov(X);
  M = Sxy / Sxx;
  Q = mean(Y) - M * mean(X);
  P = [M Q];
  Y1 = X * M + Q;
endfunction
