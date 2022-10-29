# 2022-10-28

Interazione di 2 ore pari a 24000 steps

| Parametri agente |        |
|------------------|-------:|
| alpha            |  10e-3 |
| lambda           |    0.5 |


## Rewards

| KPI    |             |
|--------|------------:|
| Mean   |      -0.142 |
| Min    |          -1 |
| Median |           0 |
| Max    |           1 |
| Trend  |      Linear |
| From   |      -0.105 |
| To     |      -0.179 |

Trend in discesa con valori medi oscillanti tra -0.5 e 0.34.
Valori massimi oscillanti tra 0.19 e 1.
Da segnalare un picco negativo a -1 dei valori massimi intorno allo step 22000.

__L'agente non ha trovato una soluzione ottimale.__

## Delta

| KPI    |             |
|--------|------------:|
| Mean   |       0.433 |
| Min    |    6.85e-05 |
| Median |       0.321 |
| Max    |        1.87 |
| Trend  |      Linear |
| From   |       0.282 |
| To     |       0.584 |

Trend in crescita.

__L'errore dell'agente sembra crescere indicando alti errori di stima.__

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
| Mean   |       0.574 |
| Min    |         0.5 |
| Median |       0.562 |
| Max    |       0.707 |
| Trend  |      Linear |
| From   |       0.555 |
| To     |       0.593 |

Trend crescente con alte oscillazioni.

__L'agente sembra non aver trovato una strategia nettamente deterministica dell'azione halt.__

## Direction max probability

| KPI    |             |
|--------|------------:|
| Mean   |       0.169 |
| Min    |        0.04 |
| Median |       0.187 |
| Max    |       0.268 |
| Trend  |      Linear |
| From   |      0.0862 |
| To     |       0.251 |

Trend crescente con rapporto max/min di 6.7.

__L'agente ha ancora comportamento nettamente casuale (P < 0.5), con un trend però di miglioramento.__

## Speed max probability

| KPI    |             |
|--------|------------:|
| Mean   |       0.401 |
| Min    |       0.111 |
| Median |        0.41 |
| Max    |       0.578 |
| Trend  |      Linear |
| From   |       0.193 |
| To     |        0.61 |

Trend crescente con valori attestati attorno al 0.5 e rapporto max/min di 5.2

__L'agente mantiene un comportamento ancora casuale.__
Il trend fa comunque suppore miglioramenti verso comportamenti più deterministici.

## Sensor direction max probability

| KPI    |             |
|--------|------------:|
| Mean   |       0.401 |
| Min    |       0.143 |
| Median |        0.42 |
| Max    |       0.571 |
| Trend  |      Linear |
| From   |       0.267 |
| To     |       0.535 |

Trend crescente con valori attestati attorno al 0.5 e rapporto max/min di 4.

__L'agente mantiene un comportamento ancora casuale.__

Il trend fa comunque suppore miglioramenti verso comportamenti più deterministici.

## Policy Abs Delta

Variazioni della probabilità massima da addestramento.
Indicano la velocità di cambiamento del comportamento.

## Delta halt probability

| KPI    |             |
|--------|------------:|
| Mean   |     0.00117 |
| Min    |           0 |
| Median |    0.000717 |
| Max    |     0.00911 |
| Trend  |      Linear |
| From   |     0.00079 |
| To     |     0.00155 |

__La massima variazione di probabilità molto bassa indica una correzione di comportamento molto limitata.__

## Delta direction probability

| KPI    |             |
|--------|------------:|
| Mean   |     0.00104 |
| Min    |    1.12e-07 |
| Median |    0.000761 |
| Max    |     0.00658 |
| Trend  |      Linear |
| From   |     0.00107 |
| To     |     0.00102 |

__La massima variazione di probabilità molto bassa indica una correzione di comportamento molto limitata.__

## Delta speed probability

| KPI    |             |
|--------|------------:|
| Mean   |     0.00126 |
| Min    |     5.4e-08 |
| Median |    0.000853 |
| Max    |      0.0108 |
| Trend  |      Linear |
| From   |     0.00142 |
| To     |     0.00111 |

__La massima variazione di probabilità molto bassa indica una correzione di comportamento molto limitata.__

## Delta sensor direction probability

| KPI    |             |
|--------|------------:|
| Mean   |    0.000878 |
| Min    |       6e-08 |
| Median |    0.000548 |
| Max    |     0.00931 |
| Trend  |      Linear |
| From   |     0.00128 |
| To     |    0.000474 |

__La massima variazione di probabilità molto bassa indica una correzione di comportamento molto limitata.__

## Advantage residual estimation (critic)

| KPI    |             |
|--------|------------:|
| Mean   |      -0.564 |
| Min    |      -0.939 |
| Median |      -0.643 |
| Max    |       0.371 |
| Trend  |      Linear |
| From   |       -0.28 |
| To     |      -0.849 |

  __La stima del critico sta peggiorando indicando che non ha ancora trovato una strategia migliorativa__

## Diagnosi

__L'agente ha valori dei ratei di apprendimento $\alpha$ troppo bassi e il processo di apprendimento è molto lento.__

