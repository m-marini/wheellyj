# 2022-10-28

Interazione di 2 ore pari a 24000 steps

| Parametri agente |        |
|------------------|-------:|
| alpha            |  30e-3 |
| lambda           |    0.5 |

## Rewards

| KPI    |             |
|--------|------------:|
| Mean   |      -0.167 |
| Min    |          -1 |
| Median |           0 |
| Max    |       0.667 |
| Trend  |      Linear |
| From   |     -0.0955 |
| To     |       -0.24 |

Trend negativo con valore medio oscillante tra -0.5 e 0.111, l'ampiezza delle oscillazioni tende ad aumentare.
IL valore massimo indica che l'agente non hai mai raggiunto una situazione ottimale.

__L'agente non sta migliorando il comportamento.__

## Delta

| KPI    |             |
|--------|------------:|
| Mean   |       0.447 |
| Min    |    1.94e-05 |
| Median |       0.406 |
| Max    |        1.09 |
| Trend  |      Linear |
| From   |       0.299 |
| To     |       0.595 |

Trend in crescita.

__L'agente ha ancora alti errori nella stima del crtico, quindi non ha ancora trovato una strategia di miglioramento.__

## Maximum probability

| Action           | Minimum value |
|------------------|--------------:|
| Halt             |           0.5 |
| Direction        |          0.04 |
| Speed            |         0.111 |
| Sensor direction |         0.143 |

Tali valori corrispondono con il comportamento completamente casuale.
Le temperature dei layer softmax dell'attore sono tarati per una probabilità massima di 0.9.

## Halt max probability

| KPI    |             |
|--------|------------:|
| Mean   |       0.803 |
| Min    |         0.5 |
| Median |       0.858 |
| Max    |       0.872 |
| Trend  |      Linear |
| From   |       0.669 |
| To     |       0.937 |

Trend crescente con raggiungimento del valore 0.85 prossimo al valore massimo dopo 7300 steps e mantenimento costante del valore.

__L'agente ha trovato un comportamento deterministico per l'azione halt.__

## Direction max probability

| KPI    |             |
|--------|------------:|
| Mean   |       0.179 |
| Min    |        0.04 |
| Median |       0.186 |
| Max    |       0.239 |
| Trend  |      Linear |
| From   |        0.18 |
| To     |       0.178 |

Dopo una crescita fino a valori superiori a 0.2 dopo 3000 steps si assiste ad una discesa dei valori fino a 0.15.
Rapporto max/min di 6.

__L'agente ha ancora comportamenti piuttosto casuali e senza segnali di stabilizzazione.__

## Speed max probability

| KPI    |             |
|--------|------------:|
| Mean   |       0.349 |
| Min    |       0.111 |
| Median |       0.343 |
| Max    |       0.474 |
| Trend  |      Linear |
| From   |        0.33 |
| To     |       0.367 |

Trend in leggera cresciata con valori oscillanti tra 0.25 e 0.44.
Rapporto max/min di 4.2.

__L'agente mantiene ancora un comportamento casuale.__

## Sensor direction max probability

| KPI    |             |
|--------|------------:|
| Mean   |       0.543 |
| Min    |       0.143 |
| Median |       0.515 |
| Max    |       0.858 |
| Trend  |      Linear |
| From   |       0.365 |
| To     |       0.721 |

Trend decisamente in salita con picchi a valori prossimi a 0.8 sempre più frequenti
Rapporto max/min di 6.

__L'agente ha ancora alcuni comportamento casuale, ma tende decisamente a un comportamento deterministico.__


## Policy Abs Delta

Variazioni della probabilità massima da addestramento.
Indicano la velocità di cambiamento del comportamento.

## Delta halt probability

| KPI    |             |
|--------|------------:|
| Mean   |    0.000647 |
| Min    |           0 |
| Median |    0.000142 |
| Max    |       0.018 |
| Trend  |      Linear |
| From   |     0.00178 |
| To     |   -0.000489 |

Trend in discesa. raggiunge un valore massimo quasi constante di 1.2e-3 dopo circa 7500 steps.

__L'agente ha raggiunto un cmportamento deterministico stabile e l'attore ha ridotto notevolmente le correzioni alla strategia.__

## Delta direction probability

| KPI    |             |
|--------|------------:|
| Mean   |     0.00143 |
| Min    |       6e-08 |
| Median |    0.000893 |
| Max    |      0.0194 |
| Trend  |      Linear |
| From   |     0.00239 |
| To     |    0.000482 |

Trend in discesa costante.

__L'agente ha una velocità di correzione ancora molto bassa.__


## Delta speed probability

| KPI    |             |
|--------|------------:|
| Mean   |     0.00191 |
| Min    |     1.2e-07 |
| Median |     0.00124 |
| Max    |      0.0248 |
| Trend  |      Linear |
| From   |     0.00292 |
| To     |      0.0009 |

Trend in discesa costante.

__L'agente ha una velocità di correzione ancora molto bassa.__

## Delta sensor direction probability

| KPI    |             |
|--------|------------:|
| Mean   |     0.00174 |
| Min    |       2e-08 |
| Median |     0.00096 |
| Max    |      0.0228 |
| Trend  |      Linear |
| From   |      0.0026 |
| To     |     0.00088 |

Trend in discesa costante.

__L'agente ha una velocità di correzione ancora molto bassa.__

## Advantage residual estimation (critic)

| KPI    |             |
|--------|------------:|
| Mean   |      -0.899 |
| Min    |      -0.974 |
| Median |      -0.932 |
| Max    |    2.09e-07 |
| Trend  |      Linear |
| From   |      -0.795 |
| To     |          -1 |

Trend descrescente verso valori prossimi a -0.97.

__La stima del critico sta peggiorando indicando che non ha ancora trovato una strategia migliorativa__

## Disagnosi

__L'agente ha valori dei ratei di apprendimento $\alpha$ troppo bassi e il processo di apprendimento è molto lento.__

