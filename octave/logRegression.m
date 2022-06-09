function [Y P] = logRegression(X)
  [Y P] = linearRegression(exp(X));
  Y = log(Y);
endfunction
