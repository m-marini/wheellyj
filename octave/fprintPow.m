## usage: fprintPow(hFile, value)
##
## Print a base-10 exponent value and its corresponding power notation.
##
## hFile
##     File handle opened for writing where the formatted value is written.
##
## value
##     Numeric value representing a base-10 exponent. The value is
##     printed as the original number followed by its corresponding
##     power-of-10 representation in parentheses.
##
## Example:
##
##  hFile = fopen("output.txt", "w");
##  fprintPow(hFile, 3);
##  fclose(hFile);

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

