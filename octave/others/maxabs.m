function y = maxabs(x)
  n = size(x, 1);
  [ _ idx ] = max(abs(x), [], 2);
  y = zeros(n, 1);
  for i = 1 : n
    y(i) = x(i, idx(i));
  endfor
endfunction

