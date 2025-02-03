function generatePPOReport(hFile, r)
  fprintf(hFile, "$\\varepsilon_{ppo}$ = %s\n", strFloat(r.ppoEpsilon));
endfunction

