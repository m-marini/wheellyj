#
# Computes the correction factors for motors
#
clear all;
FILENAME = "MotorsMeasures-2.txt";
PREC= 255;

function DATA = loadData(FILENAME)
   DATA= dlmread(FILENAME, " ", 0,1);
endfunction

function [startIndex endIndex] = findIncPos(x, fromIndex)
  n = size(x, 1);
  startIndex = 0;
  endIndex = 0;
  # Find start
  while fromIndex <= n - 1
    if x(fromIndex) == 0 && x(fromIndex + 1) > x(fromIndex)
      startIndex = fromIndex;
      break;
    endif
    fromIndex = fromIndex + 1;
  endwhile

  if startIndex > 0
    # find end
    while fromIndex <= n - 1
      if x(fromIndex + 1) < x(fromIndex)
        endIndex = fromIndex;
        break;
      endif
      fromIndex = fromIndex + 1;
    endwhile
  endif

endfunction


function [startIndex endIndex] = findDecNeg(x, fromIndex)
  n = size(x, 1);
  startIndex = 0;
  endIndex = 0;
  # Find start
  while fromIndex <= n - 1
    if x(fromIndex) == 0 && x(fromIndex + 1) < x(fromIndex)
      startIndex = fromIndex;
      break;
    endif
    fromIndex = fromIndex + 1;
  endwhile

  if startIndex > 0
    # find end
    while fromIndex <= n - 1
      if x(fromIndex + 1) > x(fromIndex)
        endIndex = fromIndex;
        break;
      endif
      fromIndex = fromIndex + 1;
    endwhile
  endif

endfunction

function [startIndex endIndex] = findDecPos(x, fromIndex)
  n = size(x, 1);
  startIndex = 0;
  endIndex = 0;
  # Find start
  while fromIndex <= n - 1
    if x(fromIndex) > 0 && x(fromIndex + 1) < x(fromIndex)
      startIndex = fromIndex;
      break;
    endif
    fromIndex = fromIndex + 1;
  endwhile

  if startIndex > 0
    # find end
    while fromIndex <= n
      if x(fromIndex) == 0
        endIndex = fromIndex;
        break;
      endif
      fromIndex = fromIndex + 1;
    endwhile
  endif

endfunction


function [startIndex endIndex] = findIncNeg(x, fromIndex)
  n = size(x, 1);
  startIndex = 0;
  endIndex = 0;
  # Find start
  while fromIndex <= n - 1
    if x(fromIndex) < 0 && x(fromIndex + 1) > x(fromIndex)
      startIndex = fromIndex;
      break;
    endif
    fromIndex = fromIndex + 1;
  endwhile

  if startIndex > 0
    # find end
    while fromIndex <= n
      if x(fromIndex) == 0
        endIndex = fromIndex;
        break;
      endif
      fromIndex = fromIndex + 1;
    endwhile
  endif

endfunction

function [p0f p1f muf p0b p1b mub] = computeConfig(x)
  p1f = 0;
  p0f = 255;
  muf = 0;
  # scans for all forward ramp up
  startIdx = 1;
  while 1
    [startIdx endIdx] = findIncPos(x(:, 1), startIdx);
    if startIdx <= 0
      break;
    endif
    ramp = x([startIdx : endIdx], :);
    idx = find(ramp(:, 2) > 0);
    p1f = max(min(ramp(idx, 1)), p1f);
    startIdx = endIdx;
  endwhile

  # scans for all forward ramp down
  startIdx = 1;
  while 1
    [startIdx endIdx] = findDecPos(x(:, 1), startIdx);
    if startIdx <= 0
      break;
    endif
    ramp = x([startIdx : endIdx], :);
    idx = find(ramp(:, 2) > 0);

    pw = min(ramp(idx, 1));
    if pw < p0f
      p0f = pw;
      w2 = max(ramp(:, 2));
      p2 = max(ramp(:, 1));
      muf = round(256 * (p2 - p0f) / w2);
    endif
    startIdx = endIdx;
  endwhile

  p0b = -255;
  p1b = 0;
  mub = 0;

  # scans for all backward ramp down
  startIdx = 1;
  while 1
    [startIdx endIdx] = findDecNeg(x(:, 1), startIdx);
    if startIdx <= 0
      break;
    endif
    ramp = x([startIdx : endIdx], :);
    idx = find(ramp(:, 2) < 0);
    p1b = min(max(ramp(idx, 1)), p1b);
    startIdx = endIdx;
  endwhile

  # scans for all backward ramp up
  startIdx = 1;
  while 1
    [startIdx endIdx] = findIncNeg(x(:, 1), startIdx);
    if startIdx <= 0
      break;
    endif
    ramp = x([startIdx : endIdx], :);
    idx = find(ramp(:, 2) < 0);
    pw = max(ramp(idx, 1));
    if pw > p0b
      p0b = pw;
      w2 = min(ramp(:, 2));
      p2 = min(ramp(:, 1));
      mub = round(256 * (p2 - p0b) / w2);
    endif
    startIdx = endIdx;
  endwhile
endfunction

function printConfig(x)
  [p0f p1f muf p0b p1b mub] = computeConfig(x);
  printf("P0_FORWARD: %d\n", p0f);
  printf("P1_FORWARD: %d\n", p1f);
  printf("MU_FORWARD: %d\n", muf);
  printf("\n");
  printf("P0_BACKWARD: %d\n", p0b);
  printf("P1_BACKWARD: %d\n", p1b);
  printf("MU_BACKWARD: %d\n", mub);
endfunction

data = loadData(FILENAME);


printf("# Left motor\n");
printf("\n");
printConfig(data(:, [1 3]));
printf("\n");


printf("# Right motor\n");
printf("\n");
printConfig(data(:, [2 4]));
printf("\n");

subplot(1, 2, 1);
title("Left motor");
plot(data(:, 3), data(:, 1));
xlabel("Left speed (pps)");
ylabel("Left power (unit)");
grid on;
grid minor on;

subplot(1, 2, 2);
title("Right motor");
plot(data(:, 4), data(:, 2));
ylabel("Right power (unit)");
xlabel("Right speed (pps)");
grid on;
grid minor on;


