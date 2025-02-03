function generateMoveProbReport(hFile, dataPath, reportPath)
  filename = [dataPath "/data.csv"];
  if exist(filename, "file")
    disp(["Creating report for " filename "..."]);
    stats = csvread(filename);

    # Creates speed prob
    numSpeed = 5;
    numDir = 8;

    speedProb = zeros(1, numSpeed);
    for i = 1 : numSpeed
      idx = i : numSpeed : (numSpeed * numDir);
      prob = stats(idx);
      speedProb(i) = sum(prob);
    endfor

    # Creates dir prob
    dirProb = zeros(1, numDir);
    for i = 1 : numDir
      idx = (i * numSpeed - numSpeed + 1) : (i * numSpeed);
      prob = stats(idx);
      dirProb(i) = sum(prob);
    endfor
    dirAngle = -180 : 360 / numDir : 179;
    speedValue = -60 : 30: 60;

    kpiTitle = "Move mean prob.";
    fprintf(hFile, "\n");
    fprintf(hFile, "## %s\n", kpiTitle);
    fprintf(hFile, "\n");
    importFile(hFile, ["templates/policy_moveAction_mean.md"]);

    fprintf(hFile, "\n");

    fprintf(hFile, "| Action | Dir (DEG)| Speed (PPS) |  Probability |\n");
    fprintf(hFile, "|-------:|---------:|------------:|------------:|\n");
    for i = 1 : size(stats, 2)
      fprintf(hFile, "| %6d | %6d | %6d | %11s |\n",
      i - 1,
      dirAngle(floor((i - 1) / numSpeed) + 1),
      speedValue(mod((i - 1), numSpeed) + 1),
      strFloat(stats(i)));
    endfor

    fprintf(hFile, "\n");
    fprintf(hFile, "### %s histogram\n", kpiTitle);
    fprintf(hFile, "\n");

    # Generate charts
    histFile = "policy_move_mean_hist.png";
    fprintf(hFile, "\n");
    fprintf(hFile, "![%s](%s)\n", kpiTitle, histFile);

    clf();
    bar(0 : size(stats, 2) - 1, stats);
    grid on;
    grid minor on;
    title(kpiTitle);
    file = [reportPath "/" histFile];
    print(file, "-dpng", "-S1200,800");

    disp(["Creating report for " filename "..."]);
    stats = csvread(filename);

    kpiTitle = "Direction mean prob.";
    fprintf(hFile, "\n");
    fprintf(hFile, "## %s\n", kpiTitle);
    fprintf(hFile, "\n");
    #    importFile(hFile, ["templates/" id ".md"]);

    fprintf(hFile, "\n");

    fprintf(hFile, "| Dir (DEG) | Probability |\n");
    fprintf(hFile, "|----------:|------------:|\n");
    for i = 1 : numDir
      fprintf(hFile, "| %6d | %11s |\n", dirAngle(i), strFloat(dirProb(i)));
    endfor

    fprintf(hFile, "\n");
    fprintf(hFile, "### %s histogram\n", kpiTitle);
    fprintf(hFile, "\n");

    # Generate direction charts
    histFile = [ "move_dir_hist.png"];
    fprintf(hFile, "\n");
    fprintf(hFile, "![Direction mean prob.](%s)\n", histFile);

    clf();

    bar(dirAngle, dirProb);
    grid on;
    grid minor on;
    title(kpiTitle);
    file = [reportPath "/" histFile];
    print(file, "-dpng", "-S1200,800");

    # Generate speed report
    kpiTitle = "Speed mean prob.";
    fprintf(hFile, "\n");
    fprintf(hFile, "## %s\n", kpiTitle);
    fprintf(hFile, "\n");
    #    importFile(hFile, ["templates/" id ".md"]);

    fprintf(hFile, "\n");

    fprintf(hFile, "| Speed (PPS) | Probability |\n");
    fprintf(hFile, "|-------:|------------:|\n");
    for i = 1 : numSpeed
      fprintf(hFile, "| %6d | %11s |\n", speedValue(i), strFloat(speedProb(i)));
    endfor

    fprintf(hFile, "\n");
    fprintf(hFile, "### %s histogram\n", kpiTitle);
    fprintf(hFile, "\n");

    # Generate direction charts
    histFile = [ "move_speed_hist.png"];
    fprintf(hFile, "\n");
    fprintf(hFile, "![Direction mean prob.](%s)\n", histFile);

    clf();

    bar(speedValue, speedProb);
    grid on;
    grid minor on;
    title(kpiTitle);
    file = [reportPath "/" histFile];
    print(file, "-dpng", "-S1200,800");
  else
    disp(["File " filename " not found"]);
  endif
endfunction

