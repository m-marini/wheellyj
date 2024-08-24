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
  generateKpiReport(hFile, [dataPath "/delta"], reportPath, "delta", "Delta", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/direction/values/max"], reportPath, "policy_direction", "Direction max prob.", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/direction/values/maxGMRatio"], reportPath, "policy_direction_ratio", "Direction max/mean ratio", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/direction/values/maxMinRatio"], reportPath, "policy_direction_ratio_min", "Direction max/min ratio", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/speed/values/max"], reportPath, "policy_speed", "Speed max prob.", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/speed/values/maxGMRatio"], reportPath, "policy_speed_ratio", "Speed max/mean ratio", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/speed/values/maxMinRatio"], reportPath, "policy_speed_ratio_min", "Speed max/min ratio", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/sensorAction/values/max"], reportPath, "policy_sensorAction", "Sensor direction max prob.", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/sensorAction/values/maxGMRatio"], reportPath, "policy_sensorAction_ratio", "Sensor direction  max/mean ratio", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/sensorAction/values/maxMinRatio"], reportPath, "policy_sensorAction_ratio_min", "Sensor direction  max/min ratio", mode);
  generateKpiReport(hFile, [dataPath "/deltaGrads/critic"], reportPath, "eta_delta_critic", "Critic Error RMS", mode);
  generateKpiReport(hFile, [dataPath "/deltaGrads/direction/sum"], reportPath, "eta_delta_direction", "Direction error RMS", mode);
  generateKpiReport(hFile, [dataPath "/deltaGrads/speed/sum"], reportPath, "eta_delta_speed", "Speed error RMS", mode);
  generateKpiReport(hFile, [dataPath "/deltaGrads/sensorAction/sum"], reportPath, "eta_delta_sensorAction", "Sensor error RMS", mode);
  generateKpiReport(hFile, [dataPath "/direction/delta"], reportPath, "direction_delta", "Direction Correction Rate RMS", mode);
  generateKpiReport(hFile, [dataPath "/speed/delta"], reportPath, "speed_delta", "Speed Correction Rate RMS", mode);
  generateKpiReport(hFile, [dataPath "/sensorAction/delta"], reportPath, "sensorAction_delta", "Sensor Direction Correction Rate RMS", mode);

  fclose(hFile);

  close(fig);
endfunction


