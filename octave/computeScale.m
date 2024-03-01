function [scale, ord] = computeScale(min, max)
  delta = max - min;
  ord = floor(log10(delta) / 3) * 3;
  scale = 10 ^ ord;
endfunction

