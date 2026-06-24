function Y = stats(X)
  N = X(:, 1);
  NUM_SAMPLES = sum(N);
  MEANS = X(:, 2);
  MEAN = sum(MEANS .* N) / NUM_SAMPLES;
  MIN = min(X(:, 3));
  MAX = max(X(:, 4));
  Y = [NUM_SAMPLES MEAN MIN MAX];
endfunction

