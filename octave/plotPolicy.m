function plotPolicy(HALT_H, DIR_H, SPEED_H, SENS_H)
  NR = 2;
  NC = 2;
  
  HALT_LEGEND = {"Move", "Halt"};
  DIR_LEGEND = {};
  SPEED_LEGEND = {};
  SENS_LEGEND = {};
  
  N_DIR = size(DIR_H, 2);
  for I = 1 : N_DIR
    DIR_LEGEND(1, I) = sprintf("%d", (I - 1) * 360 / N_DIR - 180);
  endfor
  
  N_SPEED = size(SPEED_H, 2);
  for I = 1 : N_SPEED
    SPEED_LEGEND(1, I) = sprintf("%.1f", (I - 1) * 2.0 / (N_SPEED - 1) - 1);
  endfor

  N_SENS = size(SENS_H, 2);
  for I = 1 : N_SENS
    SENS_LEGEND(1, I) = sprintf("%d", (I - 1) * 180 / N_SENS - 80);
  endfor

  clf;
  
  subplot(NR, NC, 1);
  plot(softmax(HALT_H));
  grid on;
  title(sprintf("Halt prob"));
  ylabel("Prob");
  xlabel("Steps");
  legend(HALT_LEGEND);

  subplot(NR, NC, 2);
  plot(softmax(DIR_H));
  grid on;
  title(sprintf("Dir prob"));
  ylabel("Prob");
  xlabel("Steps");
  legend(DIR_LEGEND);

  subplot(NR, NC, 3);
  plot(softmax(SPEED_H));
  grid on;
  title(sprintf("Speed prob"));
  ylabel("Prob");
  xlabel("Steps");
  legend(SPEED_LEGEND);

  subplot(NR, NC, 4);
  plot(softmax(SENS_H));
  grid on;
  title(sprintf("Sensor prob"));
  ylabel("Prob");
  xlabel("Steps");
  legend(SENS_LEGEND);

  endfunction
