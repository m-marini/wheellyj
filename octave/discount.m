function Y = discount(X, LAMBDA)
  Y = zeros(size(X));
  N = size(X, 1);
  XM = mean(X, 1);
  for I = [1 : N]
    XM = (XM - X(I , :)) .* LAMBDA + X(I , :);
    Y(I, :) = XM;
  endfor
endfunction
