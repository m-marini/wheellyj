# Polar Map Intersection

Let us give $Q = (x_q,y_q)$ the point rappresenting the center of polar map,

and $\alpha$ the direction angle of polar map from the point $Q$,

and $\Delta \alpha$ the direction range of polar map sector

and $A=(x_l, y_0), B=(x_r, y_0)$ with $(x_l < x_r) \cup (y_0 > y_q)$ the two extreme points of segment $\overline{AB}$
the horizontal edge of the sector square in front of $Q$.

Let us define

$\alpha_l = \alpha - \Delta \alpha$ the left limit direction

$\alpha_r = \alpha + \Delta \alpha$ the right limit direction

## Horizontal line intersection

Let us calculate $x_{0l}, x_{0r}$ the intersection of the line $y = y_q$ and the directed lines from $Q$ to the
directions $\alpha_l, \alpha_r$

```
 y ^
   |
yq +..L...Q...R
   |  .   .   .
   |  .   .   .
  -+--+---+---+----->
      x0l xq  x0r   x
```

$|\frac{\pi}{2} - \alpha| \le d \alpha \implies x_{0l} = x_q, x_{0r} = \infty$

$|-\frac{\pi}{2} - \alpha| \le d \alpha \implies x_{0l} = -\infty, x_{0r} = x_q$

$(|\frac{\pi}{2} - \alpha| > d \alpha) \cup (|-\frac{\pi}{2} - \alpha| > d \alpha) \implies x_{0l} = x_q, x_{0r} = x_q$

## Horizontal front intersection

Let us calculate $x_{0l}, x_{0r}$ the intersection of the line $y = y_0, y_0 > y_q$ and the directed lines from $Q$ to
the directions $\alpha_l, \alpha_r$

```
 y ^
   |
y0 +..L.......R
   |  .\     /.
   |  . \   / .
   |  .  \ /  .
yq +......Q   .
   |  .   .   .
  -+--+---+---+----->
      x0l xq  x0r   x
```

$(-\frac{\pi}{2} < \alpha_l< \frac{\pi}{2}) \implies x_{0l} = (y_0-y_q) \tan \alpha_l + x_q$

else

$x_{0l} = -\infty$

$(-\frac{\pi}{2} < \alpha_r< \frac{\pi}{2}) \implies x_{0r} = (y_0-y_q) \tan \alpha_r + x_q$

else

$x_{0r} = \infty$

## Horizontal rear intersection

Let us calculate $x_{0l}, x_{0r}$ the intersection of the line $y = y_0, y_0 < y_q$ and the directed lines from $Q$ to
the directions $\alpha_r, \alpha_l$

```
 y ^
   |
yq +......Q
   |     /.\
   |    / . \
   |   /  .  \
y0 +..L.......R
   |  .   .   .
  -+--+---+---+----->
      x0l xq  x0r   x
```

$(-\frac{\pi}{2} \le \alpha_l \le \frac{\pi}{2}) \implies x_{0r} = \infty$

else

$x_{0r} = (y_0-y_q) \tan \alpha_l + x_q$

$(-\frac{\pi}{2} \le \alpha_r \le \frac{\pi}{2}) \implies x_{0l} = -\infty$

else

$x_{0l} = (y_0-y_q) \tan \alpha_r + x_q$

## Vertical line intersection

Let us calculate $y_{0r}, y_{0f}$ the intersection of the line $x = x_q$ and the directed lines from $Q$ to the
directions $\alpha_r, \alpha_l$

```
 y ^
    |
y0f |......F
    |      .
    |      .
    |      .
yq  +......Q
    |      .
    |      .
    |      .
y0r +......R
    |      .   
   -+------+-------->
           xq        x
```

$|\alpha| \le d \alpha \implies y_{0r} = y_q, y_{0f} = \infty$

$|-\pi - \alpha| \le d \alpha \implies y_{0r} = -\infty, y_{0f} = y_q$

$(|\alpha| > d \alpha) \cup (|-\pi - \alpha| > d \alpha) \implies y_{0r} = y_q, y_{0f} = y_q$

## Vertical right intersection

Let us calculate $y_{0r}, y_{0f}$ the intersection of the line $x = x_0, x_0 > x_q$ and the directed lines from $Q$ to
the directions $\alpha_r, \alpha_l$

```
 y ^
    |
y0f |......F
    |     /.
    |    / .
    |   /  .
yq  +..Q   .
    |  .\  .
    |  . \ .
    |  .  \.
y0r +......R
    |  .   .   
   -+--+---+-------->
      xq   x0        x
```

$(\alpha_r \le 0) \implies y_{0r} = -\infty
$

else

$y_{0r} = (x_0-x_q)\tan (\frac{\pi}{2}-\alpha_r) + y_q$

$(\alpha_l \le 0) \implies \infty$

else

$y_{0f} = (x_0-x_q) \tan(\frac{\pi}{2} - \alpha_l) + y_q$

## Vertical left intersection

Let us calculate $y_{0r}, y_{0f}$ the intersection of the line $x = x_0, x_0 x_q$ and the directed lines from $Q$ to the
directions $\alpha_l, \alpha_r$

```
 y ^
    |
y0f |..F
    |  .\
    |  . \
    |  .  \
yq  +......Q
    |  .  /.
    |  . / .
    |  ./  .
y0r +..R   .
    |  .   .   
   -+--+---+-------->
      x0   xq        x
```

$(-\pi < \alpha_l < 0) \implies y_{0r} = (x_0-x_q)\tan (\frac{\pi}{2}-\alpha_l) + y_q$

else

$y_{0r} = -\infty$

$(-\pi < \alpha_r < 0) \implies y_{0r} = (x_0-x_q)\tan (\frac{\pi}{2}-\alpha_r) + y_q$

else

$y_{0f} = \infty$

## Nearest horizontal point

```
 y ^
   |  
y0 |--A--o-------o---B 
   |  .  .\     /.   .
   |  .  . \   / .   .
   |  .  .  \ /  .   .
yq |.........Q   .   .
   |  .  .   |   .   .
  -+--+--+---+---+---+------->
      xl x0l xq  x0r xr      x
```

Let us calculate if $S = (x_s,y_0) \in \overline{AB}$ the point of the segment closest to point $Q$ whose direction is
in the range $\alpha \pm \Delta \alpha$ exists.

$x_{1l} = \max(x_l, x_{0l})$
$x_{1r} = \min(x_r, x_{0r})$

then we have

$x_{1l} > x_{1r} \implies \not \exist S$

$x_q \le x_{1l} \implies S=(x_{1l}, y_0)$

$x_q \le x_{1r} \implies S=(x_q, y_0)$

$x_q \ge x_{1r} \implies S=(x_{1r}, y_0)$

## Nearest vertical point

```
  y ^
    |  
yf  |......B
    |     /|
y0f |..../.o
    |   /  |
yq  |..Q   |
    |  .\  |
y0r |....\.o
    |  .  \|
yr  |......A
    |  .   .
   -+--+---+------->
       xq  x0      x
```

Let us calculate if $S = (x_0,y_s) \in \overline{AB}$ the point of the segment closest to point $Q$ whose direction is
in the range $\alpha \pm \Delta \alpha$ exists.

$y_{1r} = \max(y_r, y_{0r})$
$y_{1f} = \min(y_f, y_{0f})$

then we have

$y_{1r} > y_{1f} \implies \not \exist S$

$y_q \le y_{1r} \implies S=(x_0, y_{1r})$

$y_q \le y_{1f} \implies S=(x_0, y_q)$

$y_q \ge y_{1f} \implies S=(x_0, y_{1f})$

### Nearest square

Let us give $A B C D$ a square centered in $P = (x_p, y_p)$ of size $l$.

```
 A-------B
 |       |
 |   P   |
 |       |
 D-------C
 ```

We have

$x_l = x_p - l$ left abscissa of square

$x_r = x_p + l$ right abscissa of square

$y_r = y_p - l$ front ordinate of square

$y_f = y_p + l$ rear ordinate of square

$A = (x_l, y_f)$ the front left square corner

$B = (x_r, y_f)$ the front right square corner

$C = (x_r, y_r)$ the rear right square corner

$D = (x_l, y_r)$ the rear left square corner

Let us calculate if $S = (x_s,y_s) \in ABCD$ the point of the square closest to point $Q$ whose direction is in the
range $\alpha \pm \Delta \alpha$ exists.

### Case 1 (Container Square)

The square contains the point $Q$, $(x_q \ge x_l \cap x_q \le x_r \cap y_q \ge y_r \cap y_q \le y_f)$.

The point $Q$ itself is the nearest point.

### Others

Let us compute the $S_v = (x_v, y_v)$ vertical edges nearest point if exists

$x_q < x_l \implies S_v = nearestVertical(Q, y_r, y_f, x_l, \alpha, \Delta \Alpha)$

$x_q > x_l \implies S_v = nearestVertical(Q, y_r, y_f, x_r, \alpha, \Delta \Alpha)$

$x_l \le x_q \le x_l \implies \not \exist S_v$

and the $S_h = (x_h, y_h)$ horizontal edges nearest point if exists

$y_q < y_r \implies S_h = nearestHorizontal(Q, x_l, x_r, y_r, \alpha, \Delta \Alpha)$

$y_q > y_f \implies S_h = nearestHorizontal(Q, x_l, x_r, y_f, \alpha, \Delta \Alpha)$

$y_r \le y_q \le x_r \implies \not \exist S_h$

Then

$ \not \exist S_v \cap \not \exist S_h \implies \not \exist S$

$ \exist S_v \cap \not \exist S_h \implies S = S_v$

$ \not \exist S_v \cap \exist S_h \implies S = S_h$

$ \exist S_v \cap \exist S_h \implies S = nearest(S_v, S_h)$

### Farthest horizontal point

```
 y ^
   |  
y0 |--A--o-------o---B 
   |  .  .\     /.   .
   |  .  . \   / .   .
   |  .  .  \ /  .   .
yq |.........Q   .   .
   |  .  .   |   .   .
  -+--+--+---+---+---+------->
      xl x0l xq  x0r xr      x
```

Let us calculate if $S = (x_s,y_0) \in \overline{AB}$ the point of the segment farthest to point $Q$ whose direction is
in the range $\alpha \pm \Delta \alpha$ exists.

$x_{1l} = \max(x_l, x_{0l})$
$x_{1r} = \min(x_r, x_{0r})$
$x_m = \frac{x_{1r} + x_{1l}}{2}$

then we have

$x_{1l} > x_{1r} \implies \not \exist S$

$x_q \le x_m \implies S=(x_{1r}, y_0)$

$x_q > x_{1r} \implies S=(x_{1l}, y_0)$

## Farthest vertical point

```
  y ^
    |  
yf  |......B
    |     /|
y0f |..../.o
    |   /  |
yq  |..Q   |
    |  .\  |
y0r |....\.o
    |  .  \|
yr  |......A
    |  .   .
   -+--+---+------->
       xq  x0      x
```

Let us calculate if $S = (x_0,y_s) \in \overline{AB}$ the point of the segment farthest to point $Q$ whose direction is
in the range $\alpha \pm \Delta \alpha$ exists.

$y_{1r} = \max(y_r, y_{0r})$
$y_{1f} = \min(y_f, y_{0f})$
$y_m = \frac{y_{1r} + y_{1f}}{2}$

then we have

$y_{1r} > y_{1f} \implies \not \exist S$

$y_q \le y_m \implies S=(x_0, y_{1f})$

$y_q > y_m \implies S=(x_0, y_{1r})$

### Farthest square

Let us give $A B C D$ a square centered in $P = (x_p, y_p)$ of size $l$.

```
 A-------B
 |       |
 |   P   |
 |       |
 D-------C
 ```

We have

$x_l = x_p - l$ left abscissa of square

$x_r = x_p + l$ right abscissa of square

$y_r = y_p - l$ front ordinate of square

$y_f = y_p + l$ rear ordinate of square

$A = (x_l, y_f)$ the front left square corner

$B = (x_r, y_f)$ the front right square corner

$C = (x_r, y_r)$ the rear right square corner

$D = (x_l, y_r)$ the rear left square corner

Let us calculate if $S = (x_s,y_s) \in ABCD$ the point of the square farthest to point $Q$ whose direction is in the
range $\alpha \pm \Delta \alpha$ exists.

Let us compute the $S_v = (x_v, y_v)$ vertical edges nearest point if exists

$x_q < x_l \implies S_v = fartherVertical(Q, y_r, y_f, x_l, \alpha, \Delta \Alpha)$

$x_q > x_l \implies S_v = fartherVertical(Q, y_r, y_f, x_r, \alpha, \Delta \Alpha)$

$x_l \le x_q \le x_l \implies \not \exist S_v$

and the $S_h = (x_h, y_h)$ horizontal edges nearest point if exists

$y_q < y_r \implies S_h = fartherHorizontal(Q, x_l, x_r, y_r, \alpha, \Delta \Alpha)$

$y_q > y_f \implies S_h = fartherHorizontal(Q, x_l, x_r, y_f, \alpha, \Delta \Alpha)$

$y_r \le y_q \le x_r \implies \not \exist S_h$

Then

$ \not \exist S_v \cap \not \exist S_h \implies \not \exist S$

$ \exist S_v \cap \not \exist S_h \implies S = S_v$

$ \not \exist S_v \cap \exist S_h \implies S = S_h$

$ \exist S_v \cap \exist S_h \implies S = nearest(S_v, S_h)$
