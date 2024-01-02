# Note RLAgent

L'agente RL gener il comportamento sulla base di una rete neurale.

## processForTrain

La funzione `processForTrain` processa i segnali di feedback cosi da creare
i dati necessari all'apprendimento.

partendo da un segnale di feedback costituito dallo stato all'istante `t0`,
azione intrapresa, premio ottenuto e stato all'istante `t1` (`SARS`)
produce un dizionario di dati con i dati per l'apprendimeno;

- gli stati s0 e s1 vengono trasformati nei segnali di ingresso della rete attraverso i codificatore di stati
- i segnali engono applicati alla rete per produrre i vari segnali di uscita.
- i segnali del critico del modello vengono convertiti in residual advantage average reward (v0, v1)
  attraverso la funzione di denormalizzazione
  dei valori delle azioni (conversione linerare da -1, +1 a -minRewarda maxReward)
- Viene calcolato il valore corretto del resiual advantage average reward considerando il premio ricevuto
  `v0' = (v1 + r - avg) decay + avg (1 - decay)`
- Viene calcolato l'errore di previsione
  `delta = v0' - v0`
- Viene calcolato il punteggio della previsione
- `score = delta' ^ 2`
- Viene calcolato il nuovo valore medio del premio
  `avg' = avg rewardDecay + reward (1 - rewardDecay)`
- Viene calcolata la label del critico attraverso la normalizzazione del nuovo valore
  (conversione linerare da -minRewarda maxReward a -1, +1)
- Vengono calcolate le label di ogni attore
- Vengono raccolti e uniti i nuovi parametri alpha per ogni attore
- Vengono poi fuse insieme tutte le labels calcolate per produrre le effettive
  labels per istruire la rete.

## directLearn

La funzione `directLearn` processa i segnali di feedback e istruisce la rete
con le nuove informazioni.
Applica prima la funzione `processForTrain`, estrae le labels di della rete,
istruisce la rete con le nuove label, riapplica la funzione di `processForTrain`
e memorizza il nuovo valore medio del premio e dei nuovi parametri alpha degli attori.

## computeLabel of Discrete Actor

La funzione `computeLabels` processa i segnali di uscita della rete,
l'azione selezionata (ordinale del valore dell'azione) per produrre le labels per istruire la rete.

- i segnali di uscita della rete per lo specifico attore vengono convertite nei livelli di preferenza
  `h` di ogni possibile valore di uscita con la finzione di denormalizzazione delle uscite
- vengono poi calcolate le probabilità di selezione di un valore `pi` con la funzione softmax delle preference.
- viene calcolata la matrice di feature `y` dell'azione attraverso la funzione di codifica dell'azione
  (matrice binaria per ogni possibile valore con solo l'azione selezionata a valore 1 e i rimanenti a 0)
- viene calcolata la funzione di errore delle probabilità `z = y - pi`
- viene calcolata la funzione di errore delle preferenze `deltaH = z delta alpha`
- vengono calcolati i nuovi valori di preferenze `h* = h + deltaH`
- vengono calcolati i nuovi valori di correzione
  alpha `alpha* = alpha * alphaDecay + epsilonH / deltaHRMS (1 - alphaDecay)`
- vengono calcolate le labels di correzione della reta attraverso la
  funzione di normalizzazione delle preferenze (bilancamenro dei valori e conversione lineare ai livelli -1, 1)



