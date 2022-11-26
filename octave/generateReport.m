function generateReport(dataPath, reportPath, gamma)
  filename = [reportPath "report.md"];
  hFile = fopen(filename, "w");
  fprintf(hFile, "# Report\n");
  fprintf(hFile, "\n");
  fprintf(hFile, "%s\n", strftime ("%e %B %Y, %R", localtime(time ())));
  fprintf(hFile, "Data folder `%s`\n", dataPath);
  fprintf(hFile, "Report folder `%s`\n", reportPath);

  fprintf(hFile, "\n");
  fprintf(hFile, "[TOC]\n");

  rewards = csvread([dataPath "reward_data.csv"]);
  avgRewards = csvread([dataPath "avgReward_data.csv"]);
  delta = csvread([dataPath "delta_data.csv"]);
  halt = csvread([dataPath "policy.halt_data.csv"]);
  dir = csvread([dataPath "policy.direction_data.csv"]);
  speed = csvread([dataPath "policy.speed_data.csv"]);
  sens = csvread([dataPath "policy.sensorAction_data.csv"]);
  halt1 = csvread([dataPath "trainedPolicy.halt_data.csv"]);
  dir1 = csvread([dataPath "trainedPolicy.direction_data.csv"]);
  sens1 = csvread([dataPath "trainedPolicy.sensorAction_data.csv"]);
  speed1 = csvread([dataPath "trainedPolicy.speed_data.csv"]);
  v0 = csvread([dataPath "v0_data.csv"]);
  v01 = csvread([dataPath "trainedCritic.output_data.csv"]);
  haltGrad = maxabs(csvread([dataPath "gradPolicy.halt_data.csv"]));
  dirGrad = maxabs(csvread([dataPath "gradPolicy.direction_data.csv"]));
  sensGrad = maxabs(csvread([dataPath "gradPolicy.sensorAction_data.csv"]));
  speedGrad = maxabs(csvread([dataPath "gradPolicy.speed_data.csv"]));

  maxHalt = max(halt, [], 2);
  maxDir = max(dir, [], 2);
  maxSpeed = max(speed, [], 2);
  maxSens = max(sens, [], 2);
  dHalt = maxabs(halt1 - halt);
  dDir = maxabs(dir1 - dir);
  dSpeed = maxabs(speed1 - speed);
  dSens = maxabs(sens1 - sens);
  discount = discount(rewards, gamma);
  dv0 = v01 - v0;

  n = size(rewards, 1);

  mode = {"mean", "rms", "min", "max", "lin", "exp"};

  len = max(round(n / 100), 1);
  stride = max((len / 2), 1);

  fig = figure();

  fprintf(hFile, "\n");
  generateKpiReport(hFile, reportPath, "reward", "Rewards", mode, rewards, len, stride);
  generateKpiReport(hFile, reportPath, "delta", "Delta", mode, delta, len, stride);
  generateKpiReport(hFile, reportPath, "discount", ...
                    sprintf("Discount %g rewards", gamma), ...
                    mode, discount, len, stride);
  generateKpiReport(hFile, reportPath, "maxhalt", "Halt max prob.", mode, maxHalt, len, stride);
  generateKpiReport(hFile, reportPath, "maxdir", "Direction max prob.", mode, maxDir, len, stride);
  generateKpiReport(hFile, reportPath, "maxspeed", "Speed max prob.", mode, maxSpeed, len, stride);
  generateKpiReport(hFile, reportPath, "maxsens", "Sensor direction max prob.", mode, maxSens, len, stride);
  generateKpiReport(hFile, reportPath, "dhalt", "Delta halt prob.", mode, dHalt, len, stride);
  generateKpiReport(hFile, reportPath, "ddir", "Delta direction prob.", mode, dDir, len, stride);
  generateKpiReport(hFile, reportPath, "dspeed", "Delta speed prob.", mode, dSpeed, len, stride);
  generateKpiReport(hFile, reportPath, "dsens", "Delta sensor direction prob.", mode, dSens, len, stride);
  generateKpiReport(hFile, reportPath, "critic", "Advantage residual estimation (critic)", mode, v0, len, stride);
  generateKpiReport(hFile, reportPath, "dcritic", "Delta Advantage residual estimation (critic)", mode, dv0, len, stride);
  generateKpiReport(hFile, reportPath, "avgreward", "Avg Rewards", mode, avgRewards, len, stride);
  generateKpiReport(hFile, reportPath, "haltgrad", "Halt gradient", mode, haltGrad, len, stride);
  generateKpiReport(hFile, reportPath, "dirgrad", "Direction gradient", mode, dirGrad, len, stride);
  generateKpiReport(hFile, reportPath, "speedgrad", "Speed gradient", mode, speedGrad, len, stride);
  generateKpiReport(hFile, reportPath, "sensgrad", "Sensor direction gradient", mode, sensGrad, len, stride);

  fclose(hFile);

  close(fig);
endfunction


