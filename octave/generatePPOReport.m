function generatePPOReport(hFile, r)
  fprintf(hFile, "$\\varepsilon_{ppo} = %.3G$\n", r.ppoEpsilon);
endfunction

