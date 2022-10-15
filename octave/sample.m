function Y=sample(X, LENGTH, STRIDE, FUNC)
  N = size(X)(1);
  M = floor((N - LENGTH + STRIDE) / STRIDE);
  Y = zeros(M, 1);
  for I = [1 : M]
    I0 = (I - 1) * STRIDE + 1;
    I1 = min(N, I0 + LENGTH - 1);
    Y(I, 1) = FUNC(X(I0 : I1, 1));
  endfor
endfunction  