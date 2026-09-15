# Disseny: identitat «Refugi»

> Handoff de disseny, tal com va arribar. És el contracte visual del projecte:
> els valors de color, tipografia, mides i espaiats es poden prendre
> literalment. Al final hi ha les decisions preses en implementar-lo, que són
> les úniques desviacions.

## Handoff original

## Overview

TableChips és una app lliure que substitueix les fitxes físiques en partides
presencials de cartes (7 i mig, blackjack, pòquer). Un mòbil fa d'amfitrió i
servidor sobre la seua zona wifi; la resta de jugadors s'hi connecten amb l'app
nativa o simplement amb el navegador. **No hi ha internet en cap moment**:
l'escenari és un refugi de muntanya o un càmping.

Aquest paquet documenta la direcció visual triada (**Refugi** — fusta fosca,
llautó mat) i les sis pantalles principals.

Context d'ús que condiciona tot el disseny:

- Es mira de reüll, amb poca llum, en una taula sorollosa → contrast alt, xifres enormes.
- Ús amb una mà → accions primàries ancorades a la franja inferior.
- Cap acció només disponible mitjançant un gest: tot té botó visible.
- Dispositius vells i bateria limitada → cap animació contínua, cap efecte car.
- Trilingüe (ca/es/en): les etiquetes catalanes i castellanes són molt més
  llargues que les angleses.

## About the Design Files

Els fitxers d'aquest paquet són **referències de disseny fetes en HTML** —
prototips que mostren l'aspecte i el comportament previstos, **no codi de
producció per copiar**. La feina és **recrear aquests dissenys en l'entorn
real del projecte** amb els seus patrons establerts.

En aquest cas hi ha **dues implementacions previstes**:

1. **Android natiu amb Jetpack Compose** (l'app).
2. **HTML + CSS pelat en un sol fitxer** (el client que serveix l'amfitrió al
   navegador dels altres jugadors).

Restricció dura per a totes dues: **cap dependència de llibreries de
components**. Res de Material Components personalitzat fins a fer-lo
irreconeixible, res de Tailwind, Bootstrap ni cap framework CSS. A Compose,
`Box`/`Row`/`Column`/`Text`/`BasicTextField` i formes pròpies; a web, CSS pla
amb variables personalitzades. El client web ha de ser **un sol fitxer**, sense
peticions externes (ni Google Fonts: veure «Tipografia»).

Els fitxers `.dc.html` s'obren directament al navegador. `support.js` és el
runtime de l'eina de disseny — **ignoreu-lo del tot**, no forma part del
producte.

## Fidelity

**Alta fidelitat.** Colors, tipografia, mides i espaiats són definitius i es
poden prendre literalment. El que **no** és definitiu:

- Les icones estan dibuixades amb `div`s i vores CSS per no dependre de cap
  llibreria. Recreeu-les com a vectors o com a formes natives; la geometria
  descrita és la bona.
- El QR de la pantalla 03 és un patró de farciment, no un codi real.
- Els marcs de 390 × 844 són el llenç de disseny (iPhone 14/15 lògic). El
  disseny ha de ser fluid en amplada: res no depèn de 390 px exactes.

## Design Tokens

### Colors — mode fosc (base)

El mode fosc és la base del producte; el clar és secundari i encara no està
dissenyat. No implementeu mode clar endevinant-lo: demaneu-lo.

| Rol | Hex | Contrast sobre fons | Ús |
|---|---|---|---|
| `bg` | `#14110E` | — | Fons de pantalla, negre càlid de fum |
| `surface` | `#1F1A16` | — | Targetes, files de llista, botons secundaris |
| `surfaceHigh` | `#2A231D` | — | Estat premut, blocs destacats, fitxa |
| `line` | `#3A312A` | — | Separadors, vores de targeta |
| `lineStrong` | `#4A3E33` | — | Vora de botó secundari |
| `lineAccent` | `#6B5A3E` | — | Vora de blocs d'avís |
| `lineDanger` | `#5A3B33` | — | Vora de botó destructiu |
| `textPrimary` | `#F2EAE0` | 15.4:1 | Xifres, títols, text de botó |
| `textSecondary` | `#A99C8E` | 7.4:1 | Etiquetes, metadades, text auxiliar |
| `accent` (llautó) | `#C69A4E` | 8.1:1 | Acció primària, torn actiu, import a igualar |
| `accentHover` | `#D8AC5E` | — | Estat premut/hover de l'acció primària |
| `gain` | `#86A96B` | 7.6:1 | Guanys, jugador connectat, entrades positives |
| `loss` | `#CC5B42` | 5.3:1 | Pèrdues, retirar-se, expulsar, pila a zero |
| `warn` | `#EBC15E` | 11.6:1 | Avisos, temporitzador, finestra de desfer |
| `onAccent` | `#14110E` | — | Text sobre farciment de llautó |

Regles de color que **no** es poden trencar:

- El llautó (`accent`) és escàs. Només hi ha **una** acció de llautó ple per
  pantalla; tota la resta és superfície amb vora.
- `warn` i `accent` són tots dos càlids i es confondrien. Per això **`warn` mai
  no s'usa com a farciment de botó**: només com a text, vora o punt.
- `loss` mai no és farciment tampoc: text o vora. Així un daltònic distingeix
  primària de destructiva per la forma, no pel to.
- Cap gradient, cap ombra de color, cap resplendor. Les úniques ombres
  admissibles són `inset` per dibuixar els anells de les fitxes.

### Tipografia

Dues famílies, totes dues de llicència lliure:

- **Instrument Sans** — interfície: etiquetes, botons, noms, text corrent.
  Pesos 400 / 500 / 600 / 700.
- **IBM Plex Mono** — **tot el que és un número**, sempre amb
  `font-variant-numeric: tabular-nums` (a Compose: `FontFeature "tnum"` o la
  variant tabular de la família). Els imports canvien constantment i no poden
  ballar d'amplada. Pesos 500 / 600.

Plex Mono també s'usa per a les etiquetes petites en versaletes
(`letter-spacing: .10em`, `text-transform: uppercase`) que encapçalen cada bloc.

**Empaqueteu les fonts**: `res/font/` a Android, `@font-face` amb `base64` woff2
dins del fitxer HTML únic. Cap `<link>` a Google Fonts — no hi ha internet.
Reserva raonable si no es poden empaquetar: `ui-sans-serif` i `ui-monospace`.

Escala tipogràfica utilitzada:

| Ús | Mida / alçada / pes | Família |
|---|---|---|
| Pila pròpia (xifra mare) | 72 px / 1.0 / 600, `letter-spacing: -.03em` | Plex Mono |
| Import en edició | 64 px / 1.0 / 600, `-.03em` | Plex Mono |
| Pot, per igualar | 34 px / 1.0 / 600 | Plex Mono |
| Títol de marca (Inici) | 34 px / 1.1 / 700, `-.02em` | Instrument Sans |
| Teclat numèric | 26 px / 1.0 / 500 | Plex Mono |
| Text d'acció primària | 17–18 px / 1.15 / 600 | Instrument Sans |
| Text d'acció secundària | 15–16 px / 1.15 / 600 | Instrument Sans |
| Nom de jugador | 15 px / 1.2 / 500–600 | Instrument Sans |
| Import de jugador en llista | 15–17 px / 1.0 / 600 | Plex Mono |
| Text corrent, subtítols | 12.5–14 px / 1.3–1.5 / 400 | Instrument Sans |
| Etiqueta en versaletes | 10–11 px / 1.0 / 500, `.10em`, majúscules | Plex Mono |

Cap text per sota de 12 px. Les xifres grans sempre amb `tabular-nums`.

### Espaiat, radis, objectius tàctils

- Escala d'espaiat: 2 / 4 / 7 / 9 / 12 / 14 / 18 / 22 / 26 px. El marge lateral
  de pantalla és **18 px**.
- Radis: 10–11 px controls petits · 12–13 px botons · 14–16 px targetes ·
  22 px el marc del dispositiu · 999 px píndoles.
- Alçades d'objectiu: **72 px** botons d'inici · **64 px** acció primària ·
  **60–62 px** accions secundàries i tecles · **56 px** accions de capçalera ·
  **48 px** accions de fila (recompra, expulsar). Mai per sota de 48 px.
- Separació mínima entre objectius adjacents: 7 px.

### Text llarg — la restricció trilingüe

Cap botó no pot estar ajustat al text anglès. Regles concretes:

- Els botons tenen alçada fixa però **amplada flexible**; el text es pot
  embolicar a **dues línies** (`line-height: 1.15`, alineat al centre).
- A la fila de tres accions de la pantalla 01 («Pujar / Passar / Retirar-se»),
  els tres botons són `flex: 1` amb `padding: 0 12px`. «Retirar-se» i
  «Abandonar la mano» hi caben en una línia a 390 px; en anglès sobra espai.
- El botó primari duu **l'etiqueta i la xifra com a dos fills flex** amb
  `gap: 10px`, mai concatenats en una cadena. Això permet traduir l'etiqueta
  sense tocar el format del número.
- Prohibit `white-space: nowrap` i `text-overflow: ellipsis` en botons. Els
  noms de jugador **sí** que es trunquen amb el·lipsi (són dades, no interfície).
- Proveu sempre amb la cadena més llarga de les tres llengües. Mostres de
  referència: «Igualar l'aposta» / «Igualar la apuesta» / «Call» —
  «Començar la partida» / «Empezar la partida» / «Start game» —
  «Unir-se a una taula» / «Unirse a una mesa» / «Join a table».

### Tractament de les fitxes

Deliberadament **no** és el disc de casino. És una **tauleta quadrada de fusta
estampada**:

- Quadrat de 66 × 66 px, `border-radius: 10px`.
- Farciment `surfaceHigh` `#2A231D`, vora exterior d'1 px `#4A3E33`.
- Dos anells `inset`: `inset 0 0 0 3px #14110E, inset 0 0 0 4px <colorDenominació>`.
  És a dir, un solc fosc i després una línia fina de color.
- Una marca de 10 × 3 px del color de la denominació centrada a 6 px del cantell
  superior (la mossa de la tauleta).
- La xifra va centrada, Plex Mono 600, **sempre `textPrimary`** — el color mai
  no és el farciment, així el contrast de la xifra no depèn de la denominació.
- Denominacions de mostra: 5 → llautó · 25 → `gain` · 100 → `loss`.

**Pila vista de cantell** (a la pantalla 01 i on calga insinuar quantitat):
barres horitzontals de 10–11 px d'alçada, `border-radius: 3px`, fons
`surfaceHigh`, vora 1 px `#4A3E33` i **vora esquerra de 4 px** del color de la
denominació. Apilades amb `gap: 3–4px`. És decoració informativa, no interactiva:
no cal que siga exacta respecte del recompte real.

### Icona de l'app

Anell de llautó amb un rombe encaixat al centre:

- Llenç de 48 px. Cercle de 26 px amb vora de 4 px en `accent`.
- Al centre, un quadrat de 9 px girat 45°, ple, del mateix color.
- Fons de la icona: `surface` `#1F1A16`; en monocrom, negre pur amb formes blanques.
- Dues formes, cap detall per sota d'1,5 px: aguanta bé a 24 px.
- La mateixa marca, escalada a 18 px amb vora de 3 px i rombe de 6 px, s'usa
  com a logotip a les capçaleres; a 72 px amb vora de 8 px i rombe de 24 px, a Inici.

## Screens / Views

Totes les pantalles comparteixen la mateixa arquitectura vertical:

```
[ capçalera fixa · 1px línia inferior #2A231D ]
[ contingut flexible, sense scroll horitzontal ]
[ franja d'accions fixa a baix · 1px línia superior #2A231D ]
```

Farciment de la franja inferior: `14px 18px 20px` (els 20 px de baix absorbeixen
l'indicador de gest). La franja mai no es desplaça amb el contingut.

---

### 01 · Vista de jugador (pantalla mare)

**Propòsit.** Saber en un cop d'ull de què disposes, què hi ha en joc, si et toca
i què pots fer. És la pantalla on el jugador passa el 90 % del temps.

**Capçalera.** Logotip de 18 px + «Taula del refugi» (600, 13 px). A la dreta,
l'idioma actiu en Plex Mono 11 px i un botó de 32 px amb tres punts de 3 px
(menú). Farciment `14px 18px 12px`.

**Contingut** (`gap: 14px`, farciment `16px 18px 0`):

1. **Targeta de pila.** `surface`, vora `line`, radi 14, farciment
   `18px 20px 20px`. A dalt, «LA TEUA PILA» en versaletes a l'esquerra i la
   variació de la mà a la dreta (`+180` en `gain`, 600 / 13 px). Sota, la xifra
   de 72 px. A baix, quatre barres de pila `flex: 1` amb `gap: 4px`.
2. **Parell de mètriques.** Dues targetes `flex: 1` amb `gap: 12px`, radi 14,
   farciment `14px 16px 16px`: «POT» amb la xifra en `textPrimary`, i
   «PER IGUALAR» amb la xifra en `accent`. Totes dues a 34 px.
3. **Bloc de torn.** Fons `surfaceHigh`, **vora 1 px `accent`**, radi 14,
   farciment `14px 16px`. Punt de 10 px en `accent`, títol «És el teu torn»
   (600 / 16 px), subtítol amb l'aposta actual, i el compte enrere a la dreta
   en Plex Mono 20 px `accent`. Quan **no** és el teu torn, aquest bloc canvia a
   `surface` + vora `line`, el punt passa a `line`, i el text diu de qui és el
   torn; el temporitzador desapareix.
4. **Llista de jugadors.** Encapçalada per «A TAULA · 4». Files de
   `padding: 11px 14px`, `surface`, vora `line`, radi 11, `gap: 7px` entre elles.
   Cada fila: punt d'estat de 8 px (llautó si té el torn, `line` si no) · nom
   (`flex: 1`, truncat) · etiqueta d'estat opcional en 12 px (`ha igualat` en
   secundari, `s'ha retirat` en `loss`) · import en Plex Mono 16 px.
   Les files que no tenen el torn van a `opacity: .72`.

**Franja d'accions.**

- Primària de 64 px, farciment `accent`, text `onAccent`: «Igualar» + la xifra
  com a fill separat (Plex Mono 19 px). Hover/premut `accentHover`.
- Fila de tres botons de 60 px, `gap: 9px`, `flex: 1` cadascun, fons `surface`:
  «Pujar» i «Passar» amb vora `lineStrong` i text `textPrimary`; «Retirar-se»
  amb vora `lineDanger` i text `loss`. Premut: fons `surfaceHigh`.

**Estats a cobrir** (no dibuixats, deriveu-los d'aquestes regles):
esperant el torn d'un altre · sense fitxes (primària esdevé «Demanar recompra»,
en vora d'avís) · desconnectat del servidor (banner d'avís sota la capçalera,
accions desactivades al 40 % d'opacitat) · mà acabada.

---

### 02 · Introduir un import

**Propòsit.** Fixar una quantitat. Sempre escrivible: els valors ràpids són una
drecera, mai l'únic camí.

**Capçalera.** Botó de retrocés de 40 × 40 px (fletxa dibuixada amb dues vores
de 2 px girades 45°), títol «Pujar l'aposta» i subtítol amb el mínim legal i la
pila disponible.

**Camp d'import.** Targeta `surface` amb **vora 1 px `accent`**, radi 14,
farciment `18px 20px`. Etiqueta «IMPORT» en versaletes, la xifra a 64 px
seguida d'un cursor de 3 × 52 px en `accent`. Sota una línia divisòria de 12 px
de marge, la fila «Et quedarien» amb el saldo resultant en Plex Mono 17 px.
Si l'import supera la pila, aquest saldo passa a `loss` i el botó primari es
desactiva.

**Valors ràpids.** Graella de 3 × 2, tecles de 52 px, `gap: 8px`:
`+25` `+100` `+500` (Plex Mono 16 px) i `Mig pot` `Pot` `Tot` (Instrument Sans
14 px, amb `padding: 0 6px` perquè «Mig pot» i «Medio bote» hi càpiguen).
«Tot» va en `warn` — és la que té conseqüències.

**Teclat numèric.** Graella de 3 columnes, tecles de 62 px, `gap: 8px`, fons
`surface`, vora `line`, radi 12. Ordre: 1-9, després `00`, `0` i esborrar
(rectangle de 22 × 15 px amb vora de 2 px i una creu de dues barres de 9 × 2 px).
No hi ha separador decimal: les fitxes són enteres.

Entre els valors ràpids i el teclat hi ha un espaiador `flex: 1` que empeny el
teclat cap avall en pantalles altes.

**Franja d'accions.** «Cancel·lar» d'amplada fixa 112 px (cap a la vora del
polze esquerre) i la primària `flex: 1` de 64 px amb «Pujar a» + la xifra.
Totes dues de 64 px d'alçada.

---

### 03 · Amfitrió · taula creada

**Propòsit.** Que els altres s'hi connecten sense fricció. El QR i la URL són
l'element dominant.

**Capçalera.** Logotip + nom de la taula; a la dreta, «AMFITRIÓ» en versaletes
`gain`.

**Bloc de connexió.** Targeta de **fons `#F2EAE0`** (l'única superfície clara de
tot el producte: el QR necessita un fons blanc per escanejar-se de nit), radi 16,
farciment `16px 16px 14px`. A dins: el QR de 180 × 180 px amb 7 px de marge
blanc, la URL en Plex Mono 600 / 16 px sobre `#14110E`, i una línia d'ajuda de
12 px en `#5A5048`. Feu el QR amb correcció d'errors mitjana i mòduls quadrats
sense arrodonir.

**Accions de compartició.** Dos botons `flex: 1` de 56 px: «Copiar l'adreça» i
«Compartir».

**Avís de wifi.** Bloc `surfaceHigh` amb vora `lineAccent`, radi 12. Icona
circular de 18 px amb «!» i text de 12.5 px, tot en `warn`, amb el nom de la
zona wifi en 600. És informatiu permanent, no un toast.

**Llista de connectats.** Encapçalada per «CONNECTATS» amb el recompte «3 / 8»
en `gain` a la dreta. Files idèntiques a les de la pantalla 01, amb punt `gain`.

**Franja d'accions.** Una única primària de 64 px: «Començar la partida».
Desactivada mentre hi haja menys de dos jugadors.

---

### 04 · Inici

**Propòsit.** Dues portes. Res més.

**Capçalera.** Només el selector d'idioma, alineat a la dreta: tres botons de
38 px dins d'una píndola `surface` amb vora `line` i 3 px de farciment.
L'actiu duu farciment `accent` i text `onAccent`; els inactius són
transparents amb text secundari.

**Cos.** Centrat verticalment (`justify-content: center`, `gap: 18px`):
la marca a 72 px (anell de vora 8 px, rombe de 24 px), el nom «TableChips» a
34 px / 700, i una línia de suport de 14 px en secundari: «Fitxes per a partides
presencials. Sense internet, sense comptes.»

**Franja d'accions.** Tres nivells clarament jerarquitzats:

1. «Crear una taula» — 72 px, farciment `accent`, text alineat a l'esquerra,
   amb subtítol de 13 px en `rgba(20,17,14,.72)`: «Aquest mòbil fa de servidor».
2. «Unir-se a una taula» — 72 px, `surface` amb vora `lineStrong`, subtítol en
   secundari: «Escaneja el QR o escriu l'adreça».
3. «Reprendre la darrera taula · Refugi» — 56 px, sense fons ni vora, text
   secundari de 14 px. Només apareix si hi ha una sessió desada.

---

### 05 · Panell d'amfitrió

**Propòsit.** Les accions que només l'amfitrió pot fer: adjudicar el pot,
concedir recompres i expulsar algú.

**Capçalera.** Retrocés + «Panell d'amfitrió» amb subtítol «Mà 14 · pot 840».

**Bloc d'assignació del pot.** Targeta `surface`, radi 14, farciment
`16px 18px 18px`. Fila superior: «ASSIGNAR EL POT» i la xifra del pot a 28 px
en `accent`. A sota:

- Botó suggerit de 58 px, fons `surfaceHigh` amb vora `lineAccent`: el nom del
  guanyador probable a l'esquerra i `+840` en `gain` a la dreta
  (`justify-content: space-between`).
- Dos botons de 52 px `flex: 1`: «Repartir» (obre un selector de divisió) i
  «Empat» (retorna les apostes).

**Llista de jugadors.** Files de `padding: 11px 12px`, radi 11. Cada fila:
bloc de nom + pila (`flex: 1`, la pila en Plex Mono 13 px), botó «Recompra» de
48 px i botó d'expulsar de 48 × 48 px amb «×» en `loss` i vora `lineDanger`.
Quan un jugador està a zero, la seua pila es mostra «0 · sense fitxes» en `loss`
i el seu botó «Recompra» puja a vora `lineAccent` amb text `warn`: és la fila
que reclama atenció.

Expulsar **sempre** demana confirmació i **sempre** és desfeible des del
registre.

**Franja d'accions.** Dos botons `flex: 1` de 60 px: «Registre» i
«Tancar la taula».

---

### 06 · Registre d'activitat amb desfer

**Propòsit.** Resoldre discussions i corregir errors. En una partida real les
equivocacions són constants, i desfer ha de ser trivial.

**Capçalera.** Retrocés + «Registre d'activitat» amb subtítol de context.

**Targeta de desfer.** Sempre a dalt, fixa mentre la finestra siga oberta.
Fons `surfaceHigh`, vora `lineAccent`, radi 13, farciment `14px 15px`.
A l'esquerra el resum de l'acció (600 / 15 px) i «Fa 8 s · encara es pot desfer»
en `warn` de 12.5 px. A la dreta, un botó de 52 px amb vora `warn`, fons
transparent i text `warn`: «Desfer». Quan la finestra expira (suggeriment:
30 s, o fins a la mà següent), la targeta desapareix i l'entrada baixa a la
llista com una més.

**Entrades.** Files de `padding: 13px 4px` separades per una línia d'1 px
`#2A231D`, sense fons propi:

- Hora en Plex Mono 12 px, amplada fixa de 42 px, `textSecondary`.
- Descripció en 14.5 px, `flex: 1`, `text-wrap: pretty`, pot ocupar dues línies.
- Delta en Plex Mono 15 px: `+` en `gain`, `−` en `loss` (guionet menys
  U+2212, no un guió normal), o un guió llarg en secundari si l'acció no mou
  fitxes.

L'opacitat baixa amb l'antiguitat (1.0 → .62 → .45) per donar profunditat
sense afegir cap color. És un efecte estàtic, no una transició.

**Franja d'accions.** Un botó secundari de 60 px a tota amplada:
«Tornar a la taula».

## Interactions & Behavior

- **Navegació.** Inici → Crear → 03 Amfitrió → 01 Jugador (l'amfitrió també és
  jugador). Inici → Unir-se → 01. Des de 01: primària d'apostar → 02; menú →
  05 i 06. Totes les pantalles secundàries tenen retrocés explícit a la
  capçalera; el gest de retrocés del sistema fa el mateix, però mai no és
  l'únic camí.
- **Retroacció tàctil.** Cada acció confirmada dispara una vibració curta
  (Compose: `HapticFeedbackType.LongPress`; web: `navigator.vibrate(12)` si hi és).
  És el senyal principal en una taula sorollosa.
- **Estats de premut.** Canvi de fons immediat (`surface` → `surfaceHigh`,
  `accent` → `accentHover`) sense transició, o amb un màxim de 80 ms lineals.
  Cap efecte d'ona, cap escala, cap ombra animada.
- **Res d'animació contínua.** Cap pols, cap parpelleig, cap degradat animat,
  cap cronòmetre que redibuixe cada fotograma. El compte enrere s'actualitza
  **una vegada per segon**, només el text. Aquesta restricció és dura: la pila
  d'algú ha d'aguantar tota la partida.
- **Actualitzacions d'estat.** Quan arriba un canvi del servidor, la xifra es
  reemplaça directament. Cap animació de recompte.
- **Desfer.** Finestra temporal per a l'última acció econòmica (adjudicar pot,
  recompra, expulsió). Mentre és oberta, l'entrada apareix destacada a 06 i
  també com a barra compacta a 01. Només l'amfitrió pot desfer.
- **Desconnexió.** Si un client perd el servidor, mostra un banner d'avís
  persistent sota la capçalera i desactiva les accions; no oculta les dades
  (la darrera pila coneguda continua visible, atenuada).
- **Confirmacions destructives.** Expulsar i tancar la taula demanen
  confirmació. La resta no.

## State Management

Estat autoritatiu **al dispositiu amfitrió**; els clients només el reflecteixen.

Model de taula:

```
Table {
  id, name, wifiSsid, serverUrl
  startingStack: Int
  players: List<Player>
  hand: Hand
  log: List<LogEntry>
  undoWindow: { entryId, expiresAt } | null
}

Player { id, name, stack: Int, isHost: Bool, connected: Bool,
         status: ACTIVE | FOLDED | OUT }

Hand { number: Int, pot: Int, currentBet: Int, turnPlayerId, turnDeadline }

LogEntry { id, timestamp, type, actorId, amount: Int?, undoable: Bool }
```

Accions del client: `bet(amount)` · `call()` · `check()` · `fold()` ·
`requestRebuy()`. Accions d'amfitrió: `awardPot(playerId | split)` ·
`grantRebuy(playerId, amount)` · `kick(playerId)` · `undo(entryId)` ·
`startHand()` · `closeTable()`.

Cada acció és una entrada de registre; l'estat de la taula és la reducció del
registre. Això fa que desfer siga trivial i que la reconciliació després d'una
reconnexió siga només reenviar entrades. Persistiu el registre per poder
reprendre la partida si l'amfitrió es queda sense bateria («Reprendre la
darrera taula» a Inici).

## Localization

Tres llengües: **català** (per defecte), **castellà**, **anglès**. Detecteu la
del sistema i deixeu canviar-la des d'Inici i des del menú; el canvi és
immediat i no reinicia la sessió.

Cadenes de la interfície, amb les seues tres variants, per ordre d'aparició:

| Clau | ca | es | en |
|---|---|---|---|
| `app.name` | TableChips | TableChips | TableChips |
| `home.tagline` | Fitxes per a partides presencials. Sense internet, sense comptes. | Fichas para partidas presenciales. Sin internet, sin cuentas. | Chips for in-person games. No internet, no accounts. |
| `home.create` | Crear una taula | Crear una mesa | Create a table |
| `home.create.sub` | Aquest mòbil fa de servidor | Este móvil hace de servidor | This phone acts as the server |
| `home.join` | Unir-se a una taula | Unirse a una mesa | Join a table |
| `home.join.sub` | Escaneja el QR o escriu l'adreça | Escanea el QR o escribe la dirección | Scan the QR or type the address |
| `home.resume` | Reprendre la darrera taula | Reanudar la última mesa | Resume last table |
| `host.role` | Amfitrió | Anfitrión | Host |
| `host.scanHint` | Escanegeu el codi o escriviu l'adreça al navegador | Escanead el código o escribid la dirección en el navegador | Scan the code or type the address in your browser |
| `host.copy` | Copiar l'adreça | Copiar la dirección | Copy address |
| `host.share` | Compartir | Compartir | Share |
| `host.wifi` | Connecteu-vos a la zona wifi %s. No cal internet. | Conectaos a la zona wifi %s. No hace falta internet. | Connect to the %s hotspot. No internet needed. |
| `host.connected` | Connectats | Conectados | Connected |
| `host.start` | Començar la partida | Empezar la partida | Start game |
| `player.stack` | La teua pila | Tu pila | Your stack |
| `player.pot` | Pot | Bote | Pot |
| `player.toCall` | Per igualar | Para igualar | To call |
| `player.yourTurn` | És el teu torn | Es tu turno | Your turn |
| `player.turnOf` | Torn de %s | Turno de %s | %s's turn |
| `player.currentBet` | Aposta actual %d · pots pujar | Apuesta actual %d · puedes subir | Current bet %d · you can raise |
| `player.atTable` | A taula | En la mesa | At the table |
| `player.called` | ha igualat | ha igualado | called |
| `player.folded` | s'ha retirat | se ha retirado | folded |
| `action.call` | Igualar | Igualar | Call |
| `action.raise` | Pujar | Subir | Raise |
| `action.check` | Passar | Pasar | Check |
| `action.fold` | Retirar-se | Abandonar la mano | Fold |
| `amount.title` | Pujar l'aposta | Subir la apuesta | Raise |
| `amount.min` | Mínim %d · pila %d | Mínimo %d · pila %d | Min %d · stack %d |
| `amount.label` | Import | Importe | Amount |
| `amount.remaining` | Et quedarien | Te quedarían | You'd have left |
| `amount.quick` | Valors ràpids | Valores rápidos | Quick values |
| `amount.halfPot` | Mig pot | Medio bote | Half pot |
| `amount.pot` | Pot | Bote | Pot |
| `amount.all` | Tot | Todo | All in |
| `amount.confirm` | Pujar a | Subir a | Raise to |
| `common.cancel` | Cancel·lar | Cancelar | Cancel |
| `hostPanel.title` | Panell d'amfitrió | Panel de anfitrión | Host panel |
| `hostPanel.awardPot` | Assignar el pot | Asignar el bote | Award pot |
| `hostPanel.wins` | %s guanya el pot | %s gana el bote | %s wins the pot |
| `hostPanel.split` | Repartir | Repartir | Split |
| `hostPanel.tie` | Empat | Empate | Tie |
| `hostPanel.players` | Jugadors | Jugadores | Players |
| `hostPanel.rebuy` | Recompra | Recompra | Rebuy |
| `hostPanel.noChips` | %d · sense fitxes | %d · sin fichas | %d · no chips |
| `hostPanel.close` | Tancar la taula | Cerrar la mesa | Close table |
| `log.title` | Registre d'activitat | Registro de actividad | Activity log |
| `log.short` | Registre | Registro | Log |
| `log.hand` | Mà %d · %d jugadors | Mano %d · %d jugadores | Hand %d · %d players |
| `log.undoable` | Fa %s · encara es pot desfer | Hace %s · aún se puede deshacer | %s ago · can still be undone |
| `log.undo` | Desfer | Deshacer | Undo |
| `log.raisedTo` | %s ha pujat a %d | %s ha subido a %d | %s raised to %d |
| `log.rebuyOf` | Recompra de %s | Recompra de %s | Rebuy for %s |
| `log.tableCreated` | Taula creada · %d per jugador | Mesa creada · %d por jugador | Table created · %d per player |
| `log.back` | Tornar a la taula | Volver a la mesa | Back to the table |

**Format de nombres.** Separador de milers: **espai fi insecable** (U+202F) en
les tres llengües. Així «1 250» té la mateixa amplada arreu i no es trenca de
línia. Cap decimal: les fitxes són enteres.

## Assets

Cap imatge de mapa de bits. Tot són formes i text.

- **Icona / logotip** — anell + rombe, descrits a «Design Tokens». Feu-ne un
  vector (SVG / `VectorDrawable`) i deriveu-ne totes les mides.
- **Fletxa de retrocés, creu d'esborrar, «×» d'expulsar, punts de menú** — al
  prototip són `div`s amb vores girades; recreeu-los com a vectors.
- **QR** — generat en temps d'execució amb una llibreria mínima (ZXing a
  Android; per al fitxer HTML únic, un generador petit incrustat). Mòduls
  quadrats, sense logotip al centre, correcció d'errors mitjana, fons
  `#F2EAE0`, mòduls `#14110E`.
- **Fonts** — Instrument Sans i IBM Plex Mono, totes dues amb llicència SIL
  Open Font. Empaqueteu els subconjunts llatins.

## Files

| Fitxer | Què conté |
|---|---|
| `TableChips Pantalles.dc.html` | Les sis pantalles a 390 × 844, disposades en graella. La referència principal. |
| `TableChips Identitat.dc.html` | Les tres direccions d'identitat explorades. La triada és **1a Refugi**; 1b i 1c es conserven només com a context de la decisió — **no les implementeu**. |
| `support.js` | Runtime de l'eina de disseny. No forma part del producte; ignoreu-lo. |

Obriu els `.dc.html` directament al navegador. Les mesures es poden verificar
amb les eines de desenvolupament, però preferiu sempre els valors d'aquest
document: el prototip fa servir píxels CSS, i a Compose cal traduir-los a `dp`
i `sp` (relació 1:1 amb la densitat de referència).

## Què queda per dissenyar

No inventeu aquestes pantalles sense consultar-ho:

- Mode clar.
- Pantalla de creació de taula (nom, pila inicial, nombre de places).
- Pantalla d'unió (escàner de QR i entrada manual d'adreça).
- Selector de repartiment del pot entre diversos guanyadors.
- Resum de final de partida (qui deu què a qui).
- Tauleta i horitzontal.


---

# Decisions preses en implementar

El handoff es va aplicar sencer al client web (fase 3) i a la pantalla
d'amfitrió de l'app. Les desviacions, totes deliberades:

**Les pantalles de pòquer no s'han fet.** Les pantalles 01 i 02 descriuen torns,
temporitzador, «per igualar» i les accions igualar/pujar/passar/retirar-se.
Res d'això existeix al motor: el mode manual no té torns, i el pòquer és la
fase 7 del pla. La pantalla 01 s'ha adaptat al mode manual conservant-ne
l'arquitectura: targeta de pila, mètrica del pot, llista de jugadors i franja
inferior. El bloc de torn, que hauria quedat buit, allotja les dues coses que
sí que passen ara: l'avís de desconnexió i la barra de desfer.

**Desfer: finestra i profunditat són coses diferents.** El handoff proposa una
finestra de 30 s; el pla del projecte promet undo il·limitat, que és un dels
punts que ens separen de la competència. S'han conservat totes dues: la barra
compacta de la pantalla de taula viu 30 s (és una comoditat), i el registre
ofereix desfer sempre, tirant enrere tant com calga (és la promesa).

**Expulsar no hi és.** És fase 6. La fila de jugador del panell d'amfitrió, en
lloc del botó «×», porta «Donar» i «Llevar», que són les correccions que el
motor sí que sap fer avui. Quan arribe l'expulsió, ocuparà el seu lloc amb la
vora `lineDanger` que el handoff li assigna.

**El QR és fase 5.** El bloc de connexió clar de la pantalla 03 ja hi és, amb
l'adreça en Plex Mono sobre `#F2EAE0`; el codi hi entrarà al seu lloc.

**Mode clar: no s'ha inventat**, tal com demana el handoff. L'app declara
`color-scheme: dark` i no té `values-night`.

**El selector d'idioma de l'app encara no hi és.** Al client web sí (al menú).
A l'app cal passar per les locales per aplicació d'Android i es farà amb la
pantalla d'unió; de moment segueix l'idioma del sistema, que ja resol el cas
normal.

**Els números van en Plex Mono també dins de les frases.** El handoff ho diu
com a regla («tot el que és un número») i la pràctica ho confirma: l'espai fi
insecable del separador de milers és gairebé invisible dins d'Instrument Sans a
12–13 px, i «1 000» es llegia «1000». Les cadenes amb xifra porten la xifra en
un fill propi, mai concatenada, que és el que el handoff ja exigeix per al botó
primari.

**Les fonts van empaquetades**: `third_party/fonts/` conserva els originals i
les llicències OFL. Al client web s'incrusten en base64 dins del fitxer únic
(79 kB) i a l'app van a `res/font/`. Cap `<link>` ni cap petició externa.
