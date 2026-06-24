clear all;
FILE = "../measures.csv";
LEFT_INC_MOTOR = 0;
LEFT_DEC_MOTOR = 1;
RIGHT_INC_MOTOR = 2;
RIGHT_DEC_MOTOR = 3;

function Y = matchByType(X, TYPE, FORWARD)
  if FORWARD
    Y = X(:,1) == TYPE & X(:,2) >= 0;
  else
    Y = X(:,1) == TYPE & X(:,2) <= 0;
  endif
endfunction

function Y = matchValidSpeed(X)
  Y = X(:,4) != 0 & X(:,5) != 0;
endfunction

function [V S] = convertToVS(X)
  V = X(:, 2) .* X(:, 3) / 255;
  S = 1000 * X(:, 5) ./ X(:, 4);
endfunction

function M = computeM(V, S)
  XY = sum(V .* S);
  XX = sum(V .* V);
  M = XY / XX;
endfunction

function Y = extract(X, MATCHED)
  IDX = find(MATCHED);
  Y = X(IDX, :);
endfunction

function [V0, VX] = extractMotorParams(X, TYPE, FORWARD)
  X = extract(X, matchByType(X, TYPE, FORWARD));
  X1 = extract(X, matchValidSpeed(X));
  [V S] = convertToVS(X1);
  M = computeM(V, S);
  VX = round(120 / M);

  if (X(1, 5) != 0)
    IDX = find(X(:,5) == 0)(1) - 1;
  else
    IDX = find(X(:,5) != 0)(1);
  endif
  V0 = round(X(IDX,2) * X(IDX,3) / 255);
endfunction

function [V0, VX] = plotMotor(X, TYPE, FORWARD)
  X = extract(X, matchByType(X, TYPE, FORWARD));
  X1 = extract(X, matchValidSpeed(X));
  [V S] = convertToVS(X1);
  M = computeM(V, S);
  VX = round(120 / M);
  [V S] = convertToVS(X);
  Y = V .* 120 / VX;
  plot(V, [S Y]);
endfunction

TYPE = LEFT_INC_MOTOR;
X = csvread(FILE);
[LIF0, LIFX] = extractMotorParams(X, LEFT_INC_MOTOR, 1);
[LDF0, LDFX] = extractMotorParams(X, LEFT_DEC_MOTOR, 1);
[LIB0, LIBX] = extractMotorParams(X, LEFT_INC_MOTOR, 0);
[LDB0, LDBX] = extractMotorParams(X, LEFT_DEC_MOTOR, 0);
[RIF0, RIFX] = extractMotorParams(X, RIGHT_INC_MOTOR, 1);
[RDF0, RDFX] = extractMotorParams(X, RIGHT_DEC_MOTOR, 1);
[RIB0, RIBX] = extractMotorParams(X, RIGHT_INC_MOTOR, 0);
[RDB0, RDBX] = extractMotorParams(X, RIGHT_DEC_MOTOR, 0);
printf("Left motor  %d,%d,%d,%d,%d,%d,%d,%d\n", LIF0, LIFX, LDF0, LDFX, LIB0, LIBX, LDB0, LDBX);
printf("Right motor %d,%d,%d,%d,%d,%d,%d,%d\n", RIF0, RIFX, RDF0, RDFX, RIB0, RIBX, RDB0, RDBX);
plotMotor(X, LEFT_DEC_MOTOR, 1);




