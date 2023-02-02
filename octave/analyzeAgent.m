function analyzeAgent(
  J0,J1,                  # the J0 and J1
  REWARDS,                # rewards
  DELTA,                  # delta
  DIR_ALPHA,              # direction alpha
  DIR_DH,                 # direction dh
  SPEED_ALPHA,            # Apha
  SPEED_DH,               # speed dh
  SENS_ALPHA,             # sensor alpha
  SENS_DH,                # sensor dh
  EPSH = 0.24,            # the optimal range of h to be considered
  K0 = 0.7,               # the K threshold for C1 class
  EPS = 100e-6,           # the minimu J value to be considered
  PRC = [50 : 10 : 90]',  # the percentiles in the chart
  BINS = 20,              # the number of bins in histogram
  NPOINTS = 300,          # the number of point for historical charts
  LENGTH = 300            # the number of averaging samples for historical chrts
  )

  # Indices constants
  NA = 4;

  # Number of steps, number of values
  [n m] = size(J0);

    # Filter the J values greater than threshold EPS
  VI = find(abs(J0) >= EPS);
  # Compute the K values
  K = J1(VI) ./ J0(VI);


  # Number of invalid steps
  C0 = sum(J1> J0);
  # Number of optimizied steps
  C2 = sum(J1 < J0 * K0);
  # Number of optimizing steps
  C1 = n - C0 - C2;

  # Class percentages
  RED = C0 / (C0 + C1 + C2);
  YELLOW = C1 / (C0 + C1 + C2);
  GREEN = C2 / (C0 + C1 + C2);

  [TDX TD TDTREND TDMODE] = meanchart(DELTA .^ 2, NPOINTS, LENGTH);
  [RPIX RPI RTREND RMODE] = meanchart(REWARDS, NPOINTS, LENGTH);

  NR = 3;             # number of rows
  NC = 2 + NA;

  DIR_DH2 = DIR_DH .^ 2;
  DIR_DH2M = mean(DIR_DH2, 2);
  ALPHA = DIR_ALPHA(1);
  # Compute alpha for percetile
  PCH = prctile(sqrt(DIR_DH2M), PRC);
  DIR_DIST = EPSH ./ PCH * ALPHA;
  # Compute mean
  DIR_DIST_TREND = ones(size(PRC, 1), 1) * EPSH ./ sqrt(mean(DIR_DH2M)) * ALPHA;

  SPEED_DH2 = SPEED_DH .^ 2;
  SPEED_DH2M = mean(SPEED_DH2, 2);
  ALPHA = SPEED_ALPHA(1);
  # Compute alpha for percetile
  PCH = prctile(sqrt(SPEED_DH2M), PRC);
  SPEED_DIST = EPSH ./ PCH * ALPHA;
  # Compute mean
  SPEED_DIST_TREND = ones(size(PRC, 1), 1) * EPSH ./ sqrt(mean(SPEED_DH2M)) * ALPHA;

  SENS_DH2 = SENS_DH .^ 2;
  SENS_DH2M = mean(SENS_DH2, 2);
  ALPHA = SENS_ALPHA(1);
  # Compute alpha for percetile
  PCH = prctile(sqrt(SENS_DH2M), PRC);
  SENS_DIST = EPSH ./ PCH * ALPHA;
  # Compute mean
  SENS_DIST_TREND = ones(size(PRC, 1), 1) * EPSH ./ sqrt(mean(SENS_DH2M)) * ALPHA;

  clf;

  subplot(NR, NC, 1);
  autoplot(RPIX, [RPI RTREND]);
  grid on;
  title(sprintf("Average Reward\n%s Trend", RMODE));
  ylabel("Reward");
  xlabel("Step");

  subplot(NR, NC, 2);
  autoplot(TDX, [TD TDTREND]);
  grid on;
  title(sprintf("Squared TD Error\n%s Trend", TDMODE));
  ylabel("delta^2");
  xlabel("Step");

  subplot(NR, NC, 1 + NC);
  pie([C0, C1, C2]);
  colormap([1 0 0; 1 1 0; 0 1 0]);
  title("Step classes");

  subplot(NR, NC, 2 + NC);
  hist(K, BINS);
  grid on;
  title(sprintf("K distribution"));
  xlabel("K");
  ylabel("# samples");

  subplot(NR, NC, 4);
  autoplot(PRC, [DIR_DIST, DIR_DIST_TREND]);
  grid on;
  grid minor on;
  title(sprintf("Direction alpha"));
  xlabel("% corrected samples");
  ylabel(sprintf("Direction alpha"));

  subplot(NR, NC, 4 + NC);
  hist(DIR_DH2M, BINS);
  grid on;
  title(sprintf("Direction J distribution"));
  xlabel(sprintf("Direction J distribution"));
  ylabel("# samples");

  subplot(NR, NC, 4 + 2 * NC);
  autoplot(DIR_ALPHA);
  grid on;
  title(sprintf("Direction alpha"));
  xlabel(sprintf("Alpha"));
  ylabel("# samples");

  subplot(NR, NC, 5);
  autoplot(PRC, [SPEED_DIST, SPEED_DIST_TREND]);
  grid on;
  grid minor on;
  title(sprintf("Speed alpha"));
  xlabel("% corrected samples");
  ylabel(sprintf("Speed alpha"));

  subplot(NR, NC, 5 + NC);
  hist(SPEED_DH2M, BINS);
  grid on;
  title(sprintf("Speed J distribution"));
  xlabel(sprintf("Speed J distribution"));
  ylabel("# samples");

  subplot(NR, NC, 5 + 2 * NC);
  autoplot(SPEED_ALPHA);
  grid on;
  title(sprintf("Speed alpha"));
  xlabel(sprintf("Alpha"));
  ylabel("# samples");

  subplot(NR, NC, 6);
  autoplot(PRC, [SENS_DIST, SENS_DIST_TREND]);
  grid on;
  grid minor on;
  title(sprintf("Sensor alpha"));
  xlabel("% corrected samples");
  ylabel(sprintf("Sensor alpha"));

  subplot(NR, NC, 6 + NC);
  hist(SENS_DH2M, BINS);
  grid on;
  title(sprintf("Sensor J distribution"));
  xlabel(sprintf("Sensor J distribution"));
  ylabel("# samples");

  subplot(NR, NC, 6 + 2 * NC);
  autoplot(SENS_ALPHA);
  grid on;
  title(sprintf("Sensor alpha"));
  xlabel(sprintf("Alpha"));
  ylabel("# samples");


  printf("ANN\n");
  printf("%s rewards trend from %.1f to %.1f\n", RMODE, RTREND(1), RTREND(end));
  printf("%.0f%% red class\n", RED * 100);
  printf("%.0f%% yellow class\n", YELLOW * 100);
  printf("%.0f%% green class\n", GREEN * 100);

  printf("\n");

  printf("Optimal directin alpha: %.1f\n", DIR_DIST_TREND(1));
  printf("Optimal speed alpha:    %.1f\n", SPEED_DIST_TREND(1));
  printf("Optimal sensor alpha:   %.1f\n", SENS_DIST_TREND(1));

endfunction
