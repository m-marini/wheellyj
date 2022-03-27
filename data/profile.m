function P = profile(K)
  n = size(K, 2);
  nk = n / 2;
  K = reshape(K, nk, 2);
  hk = nk / 2;
  m = nk + 3;
  P = zeros(m, 2);
  P(1, :) = [-1, -1];
  P(end, :) = [1, 1];
  P(2 : hk + 1, :) = K(1 : hk, :);
  P(hk + 3: end - 1, :) = K(hk + 1 : end, :);
endfunction
