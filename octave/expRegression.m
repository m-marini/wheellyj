function [Y P] = expRegression(X)
  [Y P] = linearRegression(log(X));
  Y = exp(Y);
endfunction
