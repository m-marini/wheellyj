# Motion process

# Assetto del robot

L'assetto del robot è rappresentato dalla posizione nello spazio data dalle coordinate del centro del robot rispetto
l'origine
\[ \vec P(t) \]
e dall'angolo direzionale del robot rispetto la posizione iniziale
\[ \vec n(t) = (\cos\alpha(t), \sin(\alpha(t) )\]

# Movimento del robot

Il robot è controllato attraverso due segnali che determinano la velocità desiderata del motori sinistro e destro \[ l(t), r(t)\]

Assumiamo che la posizione desiderata e quella effettiva siano uguali in posizione di riposo cioè quando $l(t) = r(t) = 0$.

Prendiamo in esame ora gli istanti $ t \ge t_0 $ sucessivi a $ t_0 $ quando viene dato un comando di movimento del robot
\[
    l(t_0) \ne 0 \cup r(t_0) \ne 0
\]
Avremo che la posizione desiderata del robot sarà
\[
    \vec Q(t_0) = \vec P(t_0) \\
    \beta(t_0) = \alpha(t_0) \\
    \vec m(t_0) = (cos \beta(t_0)), sin \beta(t_0))
\]

Dopo un intervallo di tempo $ \Delta t $ avremo quindi che il robot dovrebbe avanzare in direzione $ \beta(t) $ di una valore
\[
\Delta q(t) = \frac{V}{2} (l(t) + r(t)) \Delta t
\]
con $ V $ velocità lineare massima e ruoterà la direzione di un angolo
\[
    \Delta \beta(t) =  \frac{V}{C} (l(t) - r(t)) \Delta t
\]
quindi il nuovo assetto desiderato sarà
\[
    \vec Q(t + \Delta t) = \vec Q(t) + \Delta q(t) \cdot \vec n(t)
    \\
    \beta(t+\Delta t) = \beta(t) + \Delta \alpha(t)
\]

In realtà il robot si muoverà in base alla potenza applicata ai motori
\[L(t), R(t)\]
e alle forze di attrito non conosciute e variabili
per cui l'assetto reale sarà
\[
    \vec P(t+\Delta t)\\
    \alpha(t + \Delta t)
\]

# Retroazione

Per rendere il movimento del robot più preciso applicheremo la potenza ai motori cercando di correggere le differenze tra l'assetto desiderato e quello reale.

Consideriamo ora un istante $ t $ e calcoliamo la velocità dei motori per raggiungere la posizione desiderata.

La variazione di assetto migliore è quella che ottiene il massimo avvicinamento al punto desiderato nella direzione attuale e una direzione finale pari a quella desiderata
\[
    \Delta q'(t) = (\vec Q(t + \Delta t) - \vec P(t)) \cdot \vec n(t) \\
    \Delta \beta'(t) = \beta(t+\Delta t) - \alpha(t)
\]

Ciò si ottiene con velocità dei motori
\[
    \Delta q'(t) = \frac{V}{2} (l'(t) + r'(t)) \Delta t \\
    \Delta \beta'(t) = \frac{V}{C}(l'(t) - r'(t)) \Delta t
\]
da cui
\[
    l'(t) + r'(t) = \frac{2}{V} \frac{\Delta q'(t)}{ \Delta t} \\
    l'(t) - r'(t) = \frac{C}{V} \frac{\Delta \beta'(t)}{\Delta t}
\]

La velocità dei motori dovrà essere limitata al range 1, -1 quindi è necessario normalizzare le velocità:
\[
    \lambda(t) = \min \left( 1, \frac{1}{\max(|l'(t)|, r'((t)))}\right) \\
    l"(t) = \lambda l'(t) \\
    r"(t) = \lambda r'(t)
\]

# Funzione di correzione motore

La velocità del motore dibende dalla potenza applicata ai motori. A causa degli attriti la velocità non è funzione lineare della potenza e quindi è necessario applicare una funzione di correzione:
\[
    L(t) = f(l"(t))\\
    R(t) = f(r"(t))
\]

Si può stimare la funzione $ f(x) $ misurando le velocità effettive dei motori con diversi segnali.
In generale sarà necessario fornire una potenza proporzianalmente maggiore per velocità piccole per contrastare gli attriti statici quindi una prima approssimazione può essere la funzione tanh con parametro k:
\[
    L(t) = \tanh(kx) = \frac{e^{kx} - e^{-kx}}{e^{kx} + e^{-kx}}
\]
