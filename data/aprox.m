function Y = aprox(X, P)
  I = findLowerIdx(X,P);
  Y = (X - P(I, 1)) .* (P(I + 1, 2) - P(I, 2)) ./ (P(I + 1, 1) - P(I, 1)) + P(I, 2);
endfunction
