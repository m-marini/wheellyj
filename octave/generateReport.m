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
  generateKpiReport(hFile, [dataPath "/grads/critic/maxAbs"], reportPath, "eta_delta_critic", "Critic delta", mode);
  generateKpiReport(hFile, [dataPath "/reward"], reportPath, "reward", "Reward", mode);
  generateKpiReport(hFile, [dataPath "/delta"], reportPath, "delta", "Delta", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/direction/values/maxAbs"], reportPath, "policy_direction", "Direction max prob.", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/direction/values/maxMeanRatio"], reportPath, "policy_direction_ratio", "Direction max/mean ratio", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/speed/values/maxAbs"], reportPath, "policy_speed", "Speed max prob.", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/speed/values/maxMeanRatio"], reportPath, "policy_speed_ratio", "Speed max/mean ratio", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/sensorAction/values/maxAbs"], reportPath, "policy_sensorAction", "Sensor direction max prob.", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/sensorAction/values/maxMeanRatio"], reportPath, "policy_sensorAction_ratio", "Sensor direction  max/mean ratio", mode);
  generateKpiReport(hFile, [dataPath "/deltaGrads/critic/maxAbs"], reportPath, "eta_delta_critic", "Critic delta", mode);
  generateKpiReport(hFile, [dataPath "/deltaGrads/direction/maxAbs"], reportPath, "eta_delta_direction", "Direction delta", mode);
  generateKpiReport(hFile, [dataPath "/deltaGrads/speed/maxAbs"], reportPath, "eta_delta_speed", "Speed delta", mode);
  generateKpiReport(hFile, [dataPath "/deltaGrads/sensorAction/maxAbs"], reportPath, "eta_delta_sensorAction", "Sensor direction delta", mode);

  fclose(hFile);

  close(fig);
endfunction


