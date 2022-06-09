function [X, Y, MEAN, MODE] = meanchart(DATA, NPOINTS = 100, LENGTH = 100)
  N = size(DATA, 1);
  STRIDE = max(floor(N / NPOINTS), 1);
  [MEAN DUMMY MODE] = regression(DATA);
  if STRIDE > 1
    X = [1 : STRIDE : N]';
    L = max(LENGTH, STRIDE);
    Y = movmean(DATA, L)(X, :);
    MEAN = MEAN(X, :);
  else
    X = [1 : N]';
    Y = DATA;
  endif
endfunction
