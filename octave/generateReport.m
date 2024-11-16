# Generate the report
function generateReport(dataPath, reportPath)
  filename = [reportPath, "/report-" strftime("%Y%m%d%H%M", localtime(time ())), ".md"];
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
  generateKpiReport(hFile, [dataPath "/avgReward"], reportPath, "avgReward", "Average reward", mode);
  generateKpiReport(hFile, [dataPath "/dr"], reportPath, "dr", "Differential reward", mode);
  generateKpiReport(hFile, [dataPath "/dv"], reportPath, "dv", "Differential prediction", mode);
  generateComparison(hFile, [dataPath "/dv"], [dataPath "/dr"], reportPath, "dvdr", "Differential comparison", {"Prediction", "Reward"}, mode);
  generateKpiReport(hFile, [dataPath "trainingLayers/critic/values"], reportPath, "critic", "Critic", mode);
  generateKpiReport(hFile, [dataPath "/delta"], reportPath, "delta", "Delta", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/move/values/max"], reportPath, "policy_move", "Move max prob.", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/move/values/gm"], reportPath, "policy_move_gm", "Move prob. geometric mean", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/move/values/maxGMRatio"], reportPath, "policy_move_ratio", "Move max/mean ratio", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/move/values/maxMinRatio"], reportPath, "policy_move_ratio_min", "Move max/min ratio", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/sensorAction/values/max"], reportPath, "policy_sensorAction", "Sensor direction max prob.", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/sensorAction/values/gm"], reportPath, "policy_sensorAction_gm", "Sensor direction prob. geometric mean.", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/sensorAction/values/maxGMRatio"], reportPath, "policy_sensorAction_ratio", "Sensor direction  max/mean ratio", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/sensorAction/values/maxMinRatio"], reportPath, "policy_sensorAction_ratio_min", "Sensor direction  max/min ratio", mode);
  generateKpiReport(hFile, [dataPath "/deltaGrads/critic"], reportPath, "eta_delta_critic", "Critic Error RMS", mode);
  generateKpiReport(hFile, [dataPath "/deltaGrads/move/sum"], reportPath, "eta_delta_move", "Move error RMS", mode);
  generateKpiReport(hFile, [dataPath "/deltaGrads/sensorAction/sum"], reportPath, "eta_delta_sensorAction", "Sensor error RMS", mode);
  generateKpiReport(hFile, [dataPath "/move/delta"], reportPath, "move_delta", "Move Correction Rate RMS", mode);
  generateKpiReport(hFile, [dataPath "/sensorAction/delta"], reportPath, "sensorAction_delta", "Sensor Direction Correction Rate RMS", mode);

  fclose(hFile);

  close(fig);
endfunction


