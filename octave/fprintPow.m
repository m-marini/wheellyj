function fprintPow(hFile, value)
  pow10 = 10.^ value;
  if  value <= -3 || value > 4
    fprintf(hFile, "%.3f (%.2e)", value, pow10)
  elseif value < -1
    fprintf(hFile, "%.3f (%.5f)", value, pow10)
  elseif value < 3
    fprintf(hFile, "%.3f (%.3f)", value, pow10)
  else
    fprintf(hFile, "%.3f (%.0f)", value, pow10)
  endif
endfunction

