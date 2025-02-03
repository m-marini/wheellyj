function y = rms(x)
  y = sqrt(mean(x .* x));
endfunction

