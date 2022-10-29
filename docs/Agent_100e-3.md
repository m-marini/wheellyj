# 2022-10-28

Interazione di 2 ore pari a 24000 steps

| Parametri agente |        |
|------------------|-------:|
| alpha            |  30e-3 |
| lambda           |    0.5 |

## Rewards

| KPI    |             |
|--------|------------:|
| Mean   |     -0.0936 |
| Min    |          -1 |
| Median |           0 |
| Max    |           1 |
| Trend  |      Linear |
| From   |     -0.0103 |
| To     |      -0.177 |

Trend negativo con valori medi oscillanti tra -0.46 e 0.21.

__L'agente non ha trovato ancora una strategia positiva.__


## Delta

| KPI    |             |
|--------|------------:|
| Mean   |       0.341 |
| Min    |    3.86e-05 |
| Median |       0.209 |
| Max    |        1.22 |
| Trend  |      Linear |
| From   |       0.295 |
| To     |       0.388 |

Trend crescente con valor massimi tra0.8 e 1.2.

__L'agente ha ancora un tasso di errore sulle stime elevato.__


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
| Mean   |       0.879 |
| Min    |         0.5 |
| Median |       0.899 |
| Max    |         0.9 |
| Trend  |      Linear |
| From   |       0.828 |
| To     |       0.931 |

Trend positivo con raggiungimento e stabilizzazione del valore massimo sopra i 0.87 dopo 2300 steps.

__L'agente ha raggiunto una strategia deterministica.__

## Direction max probability

| KPI    |             |
|--------|------------:|
| Mean   |       0.106 |
| Min    |        0.04 |
| Median |      0.0913 |
| Max    |       0.247 |
| Trend  | Exponential |
| From   |       0.137 |
| To     |      0.0775 |

Trend descrescente con un livellamento a 0.09 dopo 8600 steps.
Rapporto max/min di 6.

__L'agente ha un comportamento nettamente casuale.__

## Speed max probability

| KPI    |             |
|--------|------------:|
| Mean   |       0.351 |
| Min    |       0.111 |
| Median |       0.326 |
| Max    |         0.5 |
| Trend  | Exponential |
| From   |       0.398 |
| To     |       0.303 |

Trend negativo con livellamento a 0.32 dopo circa 5500 steps.
Rapporto max/min di 2.9.

__L'agente ha un comportamento molto casuale.__

## Sensor direction max probability

| KPI    |             |
|--------|------------:|
| Mean   |       0.693 |
| Min    |       0.143 |
| Median |       0.895 |
| Max    |         0.9 |
| Trend  |      Linear |
| From   |       0.348 |
| To     |        1.04 |

Trend positivo con raggiungimento del valore massimo 0.9 dopo 10000 steps.

__L'agente ha un comportamento nettamente deterministico.__


## Policy Abs Delta

Variazioni della probabilità massima da addestramento.
Indicano la velocità di cambiamento del comportamento.

## Delta halt probability

| KPI    |             |
|--------|------------:|
| Mean   |    0.000436 |
| Min    |           0 |
| Median |    2.72e-06 |
| Max    |      0.0913 |
| Trend  |      Linear |
| From   |     0.00158 |
| To     |   -0.000709 |

Trend descrescente con raggiungimento di valori minimali (< 0.001) di correzione dopo 5400 steps.

__L'agente non corregge ulteriormente la strategia di halt__

## Delta direction probability

| KPI    |             |
|--------|------------:|
| Mean   |    0.000554 |
| Min    |           0 |
| Median |    3.98e-05 |
| Max    |      0.0494 |
| Trend  |      Linear |
| From   |      0.0018 |
| To     |   -0.000697 |

Trend descrescente con raggiungimento di valori inferiori a 0.03 dopo 8900 steps.

__L'agente non corregge ulteriormente la strategia direzionale__


## Delta speed probability

| KPI    |             |
|--------|------------:|
| Mean   |    0.000625 |
| Min    |           0 |
| Median |    4.56e-05 |
| Max    |      0.0728 |
| Trend  |      Linear |
| From   |     0.00194 |
| To     |   -0.000687 |

Trend descrescente con raggiungimento di valori inferiori a 0.02 dopo 6500 steps.

## Delta sensor direction probability

| KPI    |             |
|--------|------------:|
| Mean   |    0.000899 |
| Min    |           0 |
| Median |    1.21e-05 |
| Max    |       0.198 |
| Trend  |      Linear |
| From   |     0.00232 |
| To     |   -0.000521 |

Trend descrescente periodi significativi di correzione (< 0.1) fino a 5600 steps poi ulteriori 2 periodi tra gli step 9800 e 10400 e tra gli step 13000 e 15400, poi attività praticamente nulla.

## Advantage residual estimation (critic)

| KPI    |             |
|--------|------------:|
| Mean   |      -0.938 |
| Min    |      -0.994 |
| Median |      -0.984 |
| Max    |       0.828 |
| Trend  |      Linear |
| From   |      -0.838 |
| To     |       -1.04 |

Trend decrescente con valori > -0.89 fino allo step 6800, poi il valore tende a -1.

__La stima del critico tende al valore minimo -1, denotando una stima pessimistica.__