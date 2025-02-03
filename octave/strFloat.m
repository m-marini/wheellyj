function result = strFloat(value, prec = 3)
  if abs(value) < 1e-12
    result = sprintf("%.G", value);
  elseif abs(value) > 1e15
    result = sprintf("%.G", value);
  else
    exponent = floor(log10(abs(value)));
    ord = floor(exponent / 3) * 3;
    scale = 10 ^ -ord;
    n = exponent - ord;
    dec = prec - n - 1;
    if dec < 0
      dec = 0;
    endif
    fmt = sprintf("%%.%df",dec);
    if ord == 0
      result = sprintf(fmt, value * scale);
    elseif ord == 3
      result = sprintf([fmt " K"], value * scale);
    elseif ord == 6
      result = sprintf([fmt " M"], value * scale);
    elseif ord == 9
      result = sprintf([fmt " G"], value * scale);
    elseif ord == 12
      result = sprintf([fmt " T"], value * scale);
    elseif ord == -3
      result = sprintf([fmt " m"], value * scale);
    elseif ord == -6
      result = sprintf([fmt " u"], value * scale);
    elseif ord == -9
      result = sprintf([fmt " n"], value * scale);
    elseif ord == -12
      result = sprintf([fmt " p"], value * scale);
    else
      result = sprintf("%.G", value);
    endif
  endif
endfunction

