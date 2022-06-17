Y=csvread("../dataset-simple.csv");
DIR_SIG = Y(:,1 : 24);
SENS_SIG = Y(:, 25 : 33);
DISTANCE_SIG = Y(:, 34 : 63);
SPEEDS_SIG = Y(:, 64 : 88);
CONTACTS_SIG = Y(:, 89 : 103);
BLOCK_SIG = Y(:, 104 : 108);
IMU_SIG = Y(:, 109);
