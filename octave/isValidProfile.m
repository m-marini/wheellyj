function B = isValidProfile(K)
  n = size(K, 2);
  hn = n / 2;
  qn = hn / 2;
  B = ones(1, n);
  # Test incresing parameters
  B(1 : hn - 1) = K(1 : hn - 1) < K(2 : hn);
  B(hn + 1 : end - 1) = K(hn + 1 : end - 1) < K(hn + 2 : end);
  # Test parameters range
  B(1 : qn) = B(1 : qn) & (K(1 : qn) > -1 & K(1 : qn) < 0);
  B(qn + 1 : hn) = B(qn +1 : hn) & (K(qn + 1 : hn) > 0 & K(qn + 1 : hn) < 1);
  B(hn + 1 : hn + qn) = B(hn + 1 : hn + qn) & (K(hn + 1 : hn + qn) > -1 & K(hn + 1 : hn + qn)  < 0);
  B(hn + qn + 1 : end) = B(hn + qn + 1 : end) & (K(hn + qn + 1 : end) > 0 & K(hn + qn + 1 : end) < 1);
endfunction
