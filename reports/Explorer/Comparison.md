# Comparing Analog vs Tiles Polar Radar

Comparison of different agents on explorer objective.

## Rewards

| Phase |                       |   Tiles |  Analog |
|:-----:|-----------------------|--------:|--------:|
|   1   | Mean                  |  0.0466 |  0.0607 |
|   1   | Sigma                 |   0.215 |   0.184 |
|   1   | From                  | -0.0479 | -0.0392 |
|   1   | To                    |   0.141 |   0.161 |
|   1   | Steps to discount 0.2 |   82000 |   82000 |
|       |                       |         |         |
|   2   | Mean                  |   0.218 |   0.218 |
|   2   | Sigma                 |   0.393 |   0.326 |
|   2   | From                  |   0.171 |   0.155 |
|   2   | To                    |   0.264 |   0.280 |
|   2   | Steps to discount 0.3 |   36000 |   36000 |

Trend of analog agent is better than tiles agent.

## Comparing different lamba parameters on analog agent

| Phase |                       |   TD(0) | TD(0.5) | TD(0.8) | TD(0.9) |
|:-----:|-----------------------|--------:|--------:|--------:|--------:|
|   1   | Mean                  |  0.0374 |  0.0607 |  0.0601 |  0.0837 |
|   1   | Sigma                 |    0.17 |   0.184 |    0.19 |   0.218 |
|   1   | From                  | -0.0212 | -0.0392 | -0.0155 | -0.0289 |
|   1   | To                    |  0.0961 |   0.161 |   0.136 |   0.196 |
|   1   | Steps to discount 0.1 |   82000 |   52000 |   52000 |   48000 |

TD(0.9) is performing better and faster then other agent.

## Comparing long term performance

> _To do ..._
