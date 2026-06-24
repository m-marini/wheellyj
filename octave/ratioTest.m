N = 300;
M = 10;
K = 10000;
P = [
  rand(N,M);
#  ones(N,1) rand(N,M-1)*K;
#  ones(N,2) rand(N,M-2)*K;
#  ones(N,3) rand(N,M-3)*K;
#  ones(N,M-3) rand(N,3)*K;
#  ones(N,M-2) rand(N,2)*K;
  ones(N,M-1) [1:N]';
];
P = P ./ sum(P,2);
logP = log10(P);
maxLogP = max(logP, [], 2);
meanLogP = mean(logP, 2);
ratioLogP = maxLogP - meanLogP;
scatter(maxLogP, ratioLogP, 5);

