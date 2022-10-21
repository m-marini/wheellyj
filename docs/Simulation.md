# Simulazione dell'ambiante

Trasformare l'azione (direzione, velocità), stato (direzione, velocità, velocità angolare)
in forza o impulso e coppia o impulso angolare:

Calcolare la rotazione richiesta
Se il valore assoluto della rotazione è > di 10 DEG
    applicare una velocità angolare massima
Se il valore assoluto della rotazione è < di 3 DEG
    applicare una velocità angolare nulla
Altrimenti
    applicare una velocità angolare proporzionale alla rotazione residua

Se il valore assoluto della rotazione è > di 30 DEG
    applicare una velocità lineare nulla

Altrimenti applicare una velocità lineare proporzionale alla rotazione fino al massimo richiesto

Calcolare la variazione di quantità di moto linerae pari all'impulso linerae
Calcolare la variazione di quantità di moto angolare pari all'impulso angolare

Limitare gli impulsi ai limiti consentiti
