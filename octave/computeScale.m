function [scale, ord] = computeScale(min, max)
  delta = max - min;
  if delta < 1e-12
    ord = 12
  else
    ord = floor(log10(delta) / 3) * 3;
  endif
  scale = 10 ^ -ord;
endfunction

