function analyzeAgent(
  J0,J1,                  # the J0 and J1
  REWARDS,                # rewards
  DELTA,                  # delta
  EPSH = 0.24,            # the optimal range of h to be considered
  K0 = 0.7,               # the K threshold for C1 class
  EPS = 100e-6,           # the minimu J value to be considered
  PRC = [50 : 10 : 90]',  # the percentiles in the chart
  BINS = 20,              # the number of bins in histogram
  NPOINTS = 300,          # the number of point for historical charts
  LENGTH = 300            # the number of averaging samples for historical chrts
  )

  # Indices constants
  IV0 = 1;
  IVStar = 2;
  IV1 = 3;
  IRPi = 4;
  IAlphaDir = 7;
  IHDir = [8 : 15];
  IHDirStar = [16 : 23];
  IAlphaH = 32;
  IHH = [33 : 35];
  IHHStar = [36 : 38];
  IAlphaZ = 42;
  IHZ = [43 : 47];
  IHZStar = [48 : 52];
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

  NR = 2;             # number of rows
  NC = 2 + NA;

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

  for actor = 0 : NA - 1
    col = actor + 3;
    subplot(NR, NC, col);
    #autoplot(PRC, [hChart{actor + 1, 2}, hChart{actor + 1, 3}]);
    grid on;
    grid minor on;
    title(sprintf("alpha %d", actor));
    xlabel("% corrected samples");
    ylabel(sprintf("alpha %d", actor));

    subplot(NR, NC, col + NC);
    #hist(hChart{actor + 1, 1}, BINS);
    grid on;
    title(sprintf("J %d distribution", actor));
    xlabel(sprintf("J %d distribution", actor));
    ylabel("# samples");
  endfor

  printf("ANN\n");
  printf("%s rewards trend from %.1f to %.1f\n", RMODE, RTREND(1), RTREND(end));
  printf("%.0f%% red class\n", RED * 100);
  printf("%.0f%% yellow class\n", YELLOW * 100);
  printf("%.0f%% green class\n", GREEN * 100);

endfunction
