# 2022-10-28

Interazione di 2 ore pari a 24000 steps

| Parametri agente |        |
|------------------|-------:|
| alpha            | 300e-3 |
| lambda           |    0.5 |

## Rewards

| KPI    |             |
|--------|------------:|
| Mean   |      0.0331 |
| Min    |          -1 |
| Median |           0 |
| Max    |           1 |
| Trend  |      Linear |
| From   |     -0.0165 |
| To     |      0.0827 |

Trend leggermente crescente con oscillazione del valore medio tra -0.22 e 0.32.
Massimo +1 raggiunto dopo circa 20800 steps.

__L'agente sembra migliorare il comportamento.__

## Delta

| KPI    |             |
|--------|------------:|
| Mean   |       0.234 |
| Min    |     1.3e-05 |
| Median |       0.118 |
| Max    |        1.23 |
| Trend  |      Linear |
| From   |       0.216 |
| To     |       0.251 |

Trend crescente con valore massimo oscillante tra 0.85 e 1.25.

__L'agente sta ancora effettuando correzioni sulle stime__

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
| Mean   |       0.898 |
| Min    |         0.5 |
| Median |         0.9 |
| Max    |         0.9 |
| Trend  |      Linear |
| From   |       0.893 |
| To     |       0.903 |

Raggiunto il massimo dopo 500 steps e poi stabile al valore massimo.

## Direction max probability

| KPI    |             |
|--------|------------:|
| Mean   |      0.0693 |
| Min    |        0.04 |
| Median |      0.0665 |
| Max    |       0.163 |
| Trend  | Exponential |
| From   |      0.0754 |
| To     |      0.0628 |

Raggiunto massimo 0.163 dopo 127 steps ma sceso stabilmente a 0.066 dopo 3300 steps.
Rapporto max/min di 4

__L'agente ha un comportamento nettamente casuale.__

## Speed max probability

| KPI    |             |
|--------|------------:|
| Mean   |       0.154 |
| Min    |       0.111 |
| Median |       0.143 |
| Max    |       0.351 |
| Trend  | Exponential |
| From   |        0.18 |
| To     |        0.13 |

Raggiunto massimo dopo 120 steps ma sceso stabilmente a 0.14 dopo 8000 steps.
Rapporto max/min di 3.16

__L'agente ha un comportamento nettamente casuale.__

## Sensor direction max probability

| KPI    |             |
|--------|------------:|
| Mean   |       0.484 |
| Min    |       0.143 |
| Median |       0.478 |
| Max    |       0.893 |
| Trend  |      Linear |
| From   |       0.499 |
| To     |       0.468 |

Valore massimo raggiunto dopo circa 800 steps, poi sceso stabilmente a 0.46 dopo 2100 steps.
Rapporto max/min 6.2

__L'agente ha un comportamento abbastanza casuale.__

## Policy Abs Delta

Variazioni della probabilità massima da addestramento.
Indicano la velocità di cambiamento del comportamento.

## Delta halt probability

| KPI    |             |
|--------|------------:|
| Mean   |       6e-05 |
| Min    |           0 |
| Median |     1.2e-07 |
| Max    |       0.149 |
| Trend  |      Linear |
| From   |    0.000238 |
| To     |   -0.000118 |

Valori scesi a 0 dopo 645 steps.

__L'agente non modifica più la strategia__

## Delta direction probability

| KPI    |             |
|--------|------------:|
| Mean   |    0.000241 |
| Min    |           0 |
| Median |    1.81e-06 |
| Max    |      0.0821 |
| Trend  |      Linear |
| From   |    0.000893 |
| To     |   -0.000412 |

Valori scesi a 0 dopo 4400 steps con alcuni picchi nell'intervallo 10400 e 12000.

__L'agente non modifica più la strategia__

## Delta speed probability

| KPI    |             |
|--------|------------:|
| Mean   |    0.000284 |
| Min    |           0 |
| Median |    2.54e-06 |
| Max    |       0.126 |
| Trend  |      Linear |
| From   |     0.00105 |
| To     |   -0.000482 |

Valori scesi a 0 dopo 3600 steps con un piccho nell'intervallo 7500 e 8000.

__L'agente non modifica più la strategia__

## Delta sensor direction probability

| KPI    |             |
|--------|------------:|
| Mean   |    0.000304 |
| Min    |           0 |
| Median |       6e-08 |
| Max    |       0.293 |
| Trend  |      Linear |
| From   |     0.00117 |
| To     |   -0.000564 |

Valori scesi a 0 dopo 2600 steps.

__L'agente non modifica più la strategia__

## Advantage residual estimation (critic)

| KPI    |             |
|--------|------------:|
| Mean   |      -0.892 |
| Min    |      -0.997 |
| Median |       -0.99 |
| Max    |       0.983 |
| Trend  |      Linear |
| From   |      -0.641 |
| To     |       -1.14 |

IL valore dopo un perido oscillante si stabilizza intorno al valore  -1 dopo circa 11000 steps.

__L'agente non modifica più la stima__

## Diagnosi

L'agente dopo una breve miglioramento verso una strategia deterministica peggiora sempre di più fino a raggiungere un una situazione stabile su un comportamento piuttosto casuale, senza ulteriori modifiche alla rete neurale.

__Probabilmente i parametri alpha sono troppo elevati e portano a livelli di inattività dei layer RELU con conseguente incapacità di migliorare.__

Studiare le variazioni di pesi nei layer dell'attore.

## Max abs delta weights bias layer0

| KPI    |             |
|--------|------------:|
| Mean   |    0.000102 |
| Min    |    1.44e-09 |
| Median |    2.85e-05 |
| Max    |      0.0169 |
| Trend  |      Linear |
| From   |    0.000198 |
| To     |    6.37e-06 |

L'attività massima di aggiornamento del bias si riduce dal picco di 0.0169 intorno allo step 2300 a valori oscillanti tra 270e-6 a 1.7e-3 dopo lo step 14000.
