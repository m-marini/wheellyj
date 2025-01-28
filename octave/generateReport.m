# Generate the report
function generateReport(dataPath, reportPath)
  filename = [reportPath, "/report-" strftime("%Y%m%d%H%M", localtime(time ())), ".md"];

  reportParms = ReadYaml([dataPath, "/report.yml"]);

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

  generateKpiReport(hFile, [dataPath "/reward"], reportPath, "reward", "Reward", 0, mode);
  generateKpiReport(hFile, [dataPath "/avgReward"], reportPath, "avgReward", "Average reward", 0, mode);
  generateKpiReport(hFile, [dataPath "/dr"], reportPath, "dr", "Differential reward", 0, mode);
  generateKpiReport(hFile, [dataPath "/dv"], reportPath, "dv", "Differential prediction", 0, mode);
  generateComparison(hFile, [dataPath "/dv"], [dataPath "/dr"], reportPath, "dvdr", "Differential comparison", {"Prediction", "Reward"}, mode);
  generateKpiReport(hFile, [dataPath "/delta"], reportPath, "delta", "Delta", reportParms, mode);

  generateKpiReport(hFile, [dataPath "trainingLayers/critic/values"], reportPath, "critic", "Critic", 0, mode);

  generateMeansReport(hFile, [dataPath "/trainingLayers/move/values/mean"], reportPath, "policy_move_mean", "Move mean prob.", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/move/values/max"], reportPath, "policy_move", "Move max prob.", @(h)generateMaxProbReport(h, reportParms.moveSize, reportParms.moveTemperature), mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/move[1]/values/saturation"], reportPath, "policy_move_saturation", "Move saturation", 0, mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/move/values/gm"], reportPath, "policy_move_gm", "Move prob. geometric mean", @(h)generateGmReport(h, reportParms.moveSize, reportParms.moveTemperature), mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/move/values/maxGMRatio"], reportPath, "policy_move_ratio", "Move max/mean ratio", @(h)generateMaxGmRatioReport(h, reportParms.moveSize, reportParms.moveTemperature), mode);

  generateMeansReport(hFile, [dataPath "/trainingLayers/sensorAction/values/mean"], reportPath, "policy_sensorAction_mean", "Sensor direction mean prob.", mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/sensorAction/values/max"], reportPath, "policy_sensorAction", "Sensor direction max prob.",@(h)generateMaxProbReport(h, reportParms.sensorActionSize, reportParms.sensorActionTemperature), mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/sensorAction[1]/values/saturation"], reportPath, "policy_sensorAction_saturation", "Sensor saturation", 0, mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/sensorAction/values/gm"], reportPath, "policy_sensorAction_gm", "Sensor direction prob. geometric mean.", @(h)generateGmReport(h, reportParms.sensorActionSize, reportParms.sensorActionTemperature), mode);
  generateKpiReport(hFile, [dataPath "/trainingLayers/sensorAction/values/maxGMRatio"], reportPath, "policy_sensorAction_ratio", "Sensor direction  max/mean ratio", @(h)generateMaxGmRatioReport(h, reportParms.sensorActionSize, reportParms.sensorActionTemperature), mode);

  generateKpiReport(hFile, [dataPath "/deltaGrads/critic"], reportPath, "eta_delta_critic", "Critic Error RMS", @(h)generateCriticErrorReport(h, reportParms), mode);
  generateKpiReport(hFile, [dataPath "/deltaGrads/move/sum"], reportPath, "eta_delta_move", "Move error RMS", @(h)generateActionErrorReport(h, reportParms.eta, reportParms.moveAlpha), mode);
  generateKpiReport(hFile, [dataPath "/deltaGrads/sensorAction/sum"], reportPath, "eta_delta_sensorAction", "Sensor error RMS", @(h)generateActionErrorReport(h, reportParms.eta, reportParms.sensorActionAlpha), mode);

  generateKpiReport(hFile, [dataPath "/move/delta"], reportPath, "move_delta", "Move Correction Rate RMS", @(h)generatePPOReport(h, reportParms), mode);
  generateKpiReport(hFile, [dataPath "/sensorAction/delta"], reportPath, "sensorAction_delta", "Sensor Direction Correction Rate RMS", @(h)generatePPOReport(h, reportParms), mode);

  fclose(hFile);

  close(fig);
endfunction


