# Generate the report
function generateReport(dataPath, reportPath)
  filename = [reportPath "/report.md"];
  mkdir(reportPath);

  hFile = fopen(filename, "w");
  # Writes the header section
  fprintf(hFile, "# Report\n");
  fprintf(hFile, "\n");
  fprintf(hFile, "%s\n", strftime ("%e %B %Y, %R", localtime(time ())));
  fprintf(hFile, "Data folder `%s`\n", dataPath);
  fprintf(hFile, "Report folder `%s`\n", reportPath);

  fprintf(hFile, "\n");
  fprintf(hFile, "[TOC]\n");

  mode = {"mean", "min", "max", "lin", "exp"};
  #mode = {"mean", "lin", "exp"};

  fig = figure();

  fprintf(hFile, "\n");
  generateKpiReport(hFile, [dataPath "/reward"], reportPath, "reward", "Reward", mode);
  generateKpiReport(hFile, [dataPath "/deltaPhase1"], reportPath, "deltaPhase1", "Delta Phase 1", mode);
  generateKpiReport(hFile, [dataPath "/delta"], reportPath, "delta", "Delta", mode);
  generateKpiReport(hFile, [dataPath "/policy/direction"], reportPath, "policy_direction", "Direction max prob.", mode);
  generateKpiReport(hFile, [dataPath "/policy/speed"], reportPath, "policy_speed", "Speed max prob.", mode);
  generateKpiReport(hFile, [dataPath "/policy/sensorAction"], reportPath, "policy_sensorAction", "Sensor direction max prob.", mode);
  generateKpiReport(hFile, [dataPath "/netGrads/direction"], reportPath, "grad_direction", "Direction Gradient", mode);
  generateKpiReport(hFile, [dataPath "/netGrads/speed"], reportPath, "grad_speed", "Speed Gradient", mode);
  generateKpiReport(hFile, [dataPath "/netGrads/sensorAction"], reportPath, "grad_sensorAction", "Sensor direction Gradient", mode);

  fclose(hFile);

  close(fig);
endfunction


