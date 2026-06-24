clear all;
dataPath = "D:/wheelly-data/csv/";
dateStr = strftime("%Y%m%d%H%M", localtime(time ()));
reportPath = ["../reports/" dateStr "/"]
filename = [reportPath, "/report-" dateStr ".md"];

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

# Rewards report

rewards = csvread([dataPath "rewards/data.csv"]);
plotFile = [reportPath "rewards.png"];
printReport(hFile, rewards, "templates/rewardReport.md", "Deltas", "rewards.png");
linplot(plotFile, rewards, "Rewards");

# Deltas report

deltas = csvread([dataPath "deltas/data.csv"]);
plotFile = [reportPath "deltas.png"];
printReport(hFile, deltas, "templates/deltaReport.md", "Deltas", "deltas.png");
linplot(plotFile, deltas, "Deltas");

# Critic report

critic = csvread([dataPath "critic/data.csv"]);
plotFile = [reportPath "critic.png"];
printReport(hFile, critic, "templates/criticReport.md", "Critic", "critic.png");
linplot(plotFile, critic, "Critic");

# Move report

movePolicy = csvread([dataPath "move/data.csv"]);
plotFile = [reportPath "move.png"];
printReport(hFile, movePolicy, "templates/moveReport.md", "Move action", "move.png", true);
linplot(plotFile, movePolicy, "Move log10")

plotFile = [reportPath "moveRatio.png"];
linplot(plotFile, movePolicy(:, [1, 6 : 9]), "Move ratio log10");
printReport(hFile, movePolicy(:, [1, 6 : 9]), "templates/moveRatioReport.md", "Move log10 ratio", "moveRatio.png", true);

# Head report
headPolicy = csvread([dataPath "head/data.csv"]);
plotFile = [reportPath "head.png"];
printReport(hFile, headPolicy, "templates/headReport.md", "Head action", "head.png", true);
linplot(plotFile, headPolicy, "Head log10")

plotFile = [reportPath "headRatio.png"];
linplot(plotFile, headPolicy(:, [1, 6 : 9]), "Head ratio log10");
printReport(hFile, headPolicy(:, [1, 6 : 9]), "templates/headRatioReport.md", "Head log10 ratio", "headRatio.png", true);

fclose(hFile);
