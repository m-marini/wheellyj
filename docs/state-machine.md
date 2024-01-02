```mermaid
graph LR
    Standby([Stand by])
    Idle([Idle])
    Forward([Moving forward])
    Rotating([Rotating])
    Scanning([Scanning])
    Backward([Moving backward])

    Standby -->|POWER| Idle

    Idle -->|Scanning timeout| Scanning

    Idle -->|Valid forward command| Forward
    Forward -->|forward obstacle| Scanning
    Scanning -->|Scanning completed| Idle

    Idle -->|Rotate command| Rotating
    Rotating -->|Rotate timeout| Scanning

    Idle -->|Backword command| Backward
    Backward -->|Backword timeout| Scanning

    Forward -->|Stop Command| Scanning
    Backward -->|Stop Command| Scanning
    Rotating -->|Stop Command| Scanning

    Forward -->|Rotate command| Rotating
    Backward -->|Rotate command| Rotating

    Backward -->|Valid forward command| Forward
    Rotating -->|Valid forward command| Forward

    Forward -->|Backword command| Backward
    Rotating -->|Backword command| Backward

    Idle -->|POWER| Standby
    Forward -->|POWER| Standby
    Backward -->|POWER| Standby
    Scanning -->|POWER| Standby
    Rotating -->|POWER| Standby
```

## Elenco parametri del motore inferenziale a stati

| Id                                                              | Default                                   | Description                                                                                                                                                                                     |
|-----------------------------------------------------------------|-------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| DISTANCE_KEY = "AvoidObstacleStatus.distance"                   | DEFAULT_DISTANCE = STOP_DISTANCE          | Distanza minima per evitare l'ostacolo                                                                                                                                                          |
| MIN_AVOID_TIME_KEY = "AvoidObstacleStatus.minAvoidTime"         | DEFAULT_MIN_AVOID_TIME = 500              | Tempo minimo di movimento per evitare un ostacolo                                                                                                                                               |
| TARGET_KEY = "FindPathStatus.target"`                           |                                           | Punto di destinazione                                                                                                                                                                           |
| PATH_KEY = "FindPathStatus.path"`                               |                                           | Lista dei punti per raggiungere la destinazione                                                                                                                                                 |
| SAFE_DISTANCE_KEY = "FindPathStatus.safeDistance"               | DEFAULT_SAFE_DISTANCE = 3 * STOP_DISTANCE | Distanza minima di sicurezza dagli ostacoli                                                                                                                                                     |
| LIKELIHOOD_THRESHOLD_KEY = "FindPathStatus.lilelihoodThreshold" | EFAULT_LIKELIHOOD_THRESHOLD = 0           | Soglia minima di rilevazione ostacolo                                                                                                                                                           |
|                                                                 | DEFAULT_EXTENSION_DISTANCE = 1            | Distanza di estensione della ricerca del percorso (vengono considerati i punti distanti dalla partenza e dall'arrivo non più della distanza tra partenza e arrivo più la distanza di estensine) |
| TARGET_KEY = "GotoStatus.target"                                |                                           | Punto di arrivo con traiettoria lineare                                                                                                                                                         |
| DISTANCE_KEY = "GotoStatus.distance"                            | DEFAULT_OBSTACLE_DISTANCE = STOP_DISTANCE | Distanza minima dall'ostacolo per raggiungerlo                                                                                                                                                  |
| SCAN_INTERVAL_KEY = "GotoStatus.scanInterval"                   | DEFAULT_SCAN_INTERVAL = 1000              | Intervallo di scansionamento per controllo ostacoli                                                                                                                                             |
|                                                                 | SCANNING_TIME = 100                       | Tempo di scansionamento laterale degli ostacoli                                                                                                                                                 |
|                                                                 | SCAN_ANGLE = 45                           | Angolo laterale di scansionamento                                                                                                                                                               |
|                                                                 | APPROACH_DISTANCE = 0.5                   | Distanza minima dal punto di arrivo per ridurre la velocità a quella di avvicinamento                                                                                                           |
|                                                                 | APPROACH_SPEED = 1                        | Velocità di avvicinamento                                                                                                                                                                       |
|                                                                 | FINAL_DISTANCE = 0.2                      | Distanza minima dal punto di arrivo per ridurre la velocitò a quella di avvicinamento finale                                                                                                    |
|                                                                 | FINAL_SPEED = 0.5                         | Velocità di avvicinamento finale                                                                                                                                                                |
| TARGET_KEY = "NextSequenceStatus.target"                        |                                           | Punto di arrivo selezionato                                                                                                                                                                     |
| INDEX_KEY = "NextSequenceStatus.index"                          |                                           | Indice del punto di arrivo selezionato                                                                                                                                                          |
| LIST_KEY = "NextSequenceStatus.list"                            |                                           | Lista dei punti di selezione                                                                                                                                                                    |
| INDEX_KEY = "ScanStatus.index"                                  |                                           | Indice dell'angolo di scansionamento                                                                                                                                                            |
| INTERVAL_KEY = "ScanStatus.interval"                            | DEFAULT_INTERVAL = 200                    | Intervallo di scansionamento                                                                                                                                                                    |
| TIMER_KEY = "ScanStatus.timer"                                  |                                           | Istante di cambio angolo di scansionamento                                                                                                                                                      |


