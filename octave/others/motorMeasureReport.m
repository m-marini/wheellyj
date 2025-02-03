#
# Computes the correction factors for motors
#
clear all;
FILENAME = "MotorsMeasures-2.txt";
PREC= 255;

function DATA = loadData(FILENAME)
   DATA= dlmread(FILENAME, " ", 0,1);
endfunction

function AVG = avgByValue(DATA)
  X = sort(unique(DATA(:, 1)));
  Y = zeros(size(X));
  for I = 1 : size(X, 1)
    Y(I) = mean(DATA(find(DATA(:, 1) == X(I)), 2));
  endfor
  AVG = [X Y];
endfunction

function Y = aprox(X, THETA)
  Y = zeros(size(X));
  BX1 = THETA(1, 1);
  BY1 = THETA(1, 2);
  BX2 = THETA(2, 1);
  BY2 = THETA(2, 2);
  for I = 1 : size(X, 1)
    XX = X(I);
    if XX < BX1
      Y(I) = (BY1 + 1) * (XX + 1) / (BX1 + 1) - 1;
    elseif XX < 0
      Y(I) = BY1 * XX / BX1;
    elseif XX < BX2
      Y(I) = BY2 * XX / BX2;
    else
      Y(I) = (1 - BY2) * (XX - BX2) / (1 - BX2) + BY2;
    endif
  endfor
  Y = min(max(Y, -1), 1);
endfunction

function ERR = erraprox(X, Y, THETA)
  YA = aprox(X, THETA);
  ERR = sum((Y - YA) .^ 2);
endfunction

function THETA = optim(DATA, PREC=255)
  THETA = [
    -0.5, -0.5;
    0.5, 0.5];
  THETA1 = THETA;
  BE = 1000;
  for I = [1 : PREC - 1]
    for J = [0 : PREC]
      THETA1(1, 1) = -I / PREC;
      THETA1(1, 2) = -J / PREC;
      ERR = erraprox(DATA(:, 1), DATA(:, 2), THETA1);
      if ERR < BE
        BE = ERR;
        THETA = THETA1;
      endif
    endfor
  endfor
  THETA1 = THETA;
  BE = 1000;
  for I = [1 : PREC - 1]
    for J = [0 : PREC]
      THETA1(2, 1) = I / PREC;
      THETA1(2, 2) = J / PREC;
      ERR = erraprox(DATA(:, 1), DATA(:, 2), THETA1);
      if ERR < BE
        BE = ERR;
        THETA = THETA1;
      endif
    endfor
  endfor
endfunction

function DATA = normAry(DATA)
  DATA = DATA ./ max(abs(DATA));
endfunction

function plotMotor(DATA, THETA)
  YA = aprox(DATA(:, 1), THETA);
  plot([DATA(:, 2) YA], DATA(:, 1));
  grid on;
endfunction

function printTheta(THETA)
  printf("%d, %d, %d, %d", THETA(1, 2), THETA(1, 1), THETA(2, 2), THETA(2, 1));
endfunction


DATA = loadData(FILENAME);

LEFT_VALUES = avgByValue(DATA(:, [1 3]));
RIGHT_VALUES = avgByValue(DATA(:, [2 4]));

# Normalize
Y_RATIO = max(abs(LEFT_VALUES(:, 1))) / max(abs(LEFT_VALUES(:, 2))) / 255;
LEFT = [LEFT_VALUES(:, 1) / 255, LEFT_VALUES(:, 2) * Y_RATIO];
LEFT = [-1 -1; LEFT; 1 1];
Y_RATIO = max(abs(RIGHT_VALUES(:, 1))) / max(abs(RIGHT_VALUES(:, 2))) / 255;
RIGHT  = [RIGHT_VALUES(:, 1) / 255, RIGHT_VALUES(:, 2) * Y_RATIO];
RIGHT = [-1 -1; RIGHT; 1 1];

printf("Optimizing left ...\n");
LEFT_THETA = optim(LEFT, PREC);

printf("Left: ");
printTheta(LEFT_THETA * PREC);
printf("\n");

printf("Optimizing right ...\n");
RIGHT_THETA = optim(RIGHT, PREC);

printf("Right: ");
printTheta(RIGHT_THETA * PREC);
printf("\n");

subplot(1, 2, 1);
plotMotor(LEFT, LEFT_THETA);
title("Left motor");

subplot(1, 2, 2);
plotMotor(RIGHT, RIGHT_THETA);
title("Right motor");

