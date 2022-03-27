function IDX = findLowerIdx(X, P)
  n = size(X, 1);
  m = size(P, 1);
  IDX = zeros(n, 1);
  for i = 1 : n
    IDX(i) = m - 1;
    for j = 2 : m - 1
      if X(i) < P(j, 1)
        IDX(i) = j - 1;
        break
      endif
    endfor
  endfor
endfunction
