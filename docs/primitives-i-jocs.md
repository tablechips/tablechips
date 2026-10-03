# Primitives i abast de jocs

Aquest document recupera l'anàlisi feta durant la planificació i la contrasta
amb el nucli implementat. És el criteri per decidir **què es modela com a mode
de primera classe i què es deixa al mode manual**. Sense ell, cada joc que
algú demane torna a ser una discussió des de zero.

Font original: `pla-tablechips.md` §12. Aquest document el substitueix com a
referència viva; el pla queda com a document històric.

Les verificacions contra el codi (§6) es van fer sobre la fase 7
(`claude/fase-7-modes`), que és la primera que té modes propis.

---

## 1. Dues capes, no una

L'error de vocabulari que arrossegàvem: el pla deia «quatre primitives» i el
codi també parla de primitives, però **no són el mateix nivell**. Separem-ho,
perquè tota la confusió ve d'aquí.

**Capa A — moviments de fitxes (el llibre major).** Què es pot moure i cap a
on. És el que hi ha al nucli avui:

| Operació | Moviment |
|---|---|
| `bet` | pila → pot |
| `award_pot` | pot → pila |
| `transfer` | pila → pila |
| `sit`, `rebuy`, `leave`, `adjust_stack` | dins/fora de la taula |
| `stake` + `settle` | pila → aposta → pila o banca |
| `split_pots` | capes dins del pot |

**Capa B — estructures de joc.** Quan i per què s'invoquen aquells moviments,
i quin estat cal mantenir entre mans. És el que enumerava el pla:

| Estructura | Què demana |
|---|---|
| Banca rotatòria | un rol assignat i una regla de traspàs |
| Pot acumulat | que el pot sobrevisqui al tancament de la mà |
| Vides / rondes perdudes | un comptador d'eliminació per jugador |
| Liquidació a tant el punt | punts per mà i una conversió a fitxes al tancar |

La capa A està bàsicament completa. **El treball pendent és tot a la capa B**,
i és molt menys del que sembla.

---

## 2. Estat de cada estructura

### Banca rotatòria — moviments coberts, falta la regla

`stake` + `settle` ja fan el moviment exacte: tothom aposta contra un jugador
concret i es liquida un a un. El que no és un moviment és **qui és la banca i
quan passa**: després de cada mà, quan la banca perd, quan es planta, o per
torn fix segons el joc.

*Verificat:* el rol **no** està encastat al 7 i mig. És estat de la taula
(`TableState.banker`) amb una ordre pròpia (`set_banker`), i el comparteixen
els dos jocs de banca a través de `GameMode.isBankGame`. Afegir-hi muntet,
quinze o punt i banca és afegir-los a aquesta condició i posar-los vocabulari.

El que falta és exactament el que diu el títol: **la regla de traspàs no
existeix**. La banca només canvia de mans quan l'amfitrió ho diu. Tampoc
sobreviu fora dels jocs de banca: passar a un mode que no és de banca
l'esborra, i el mode manual no la pot fer servir.

### Pot acumulat — el risc és el contrari del que sembla

No cal cap moviment nou: un pot que s'acumula és senzillament un pot que **no**
es reparteix. El risc és que la màquina d'estats netegi el pot en començar una
mà nova, cosa que és el comportament correcte per al pòquer i el 7 i mig i
fatal per al canari.

*Verificat:* el pot és **de la taula** (`TableState.pots`), no de la mà. Cap
regla el buida mai: les fitxes només en surten amb `award_pot`. En mode manual
no hi ha cap concepte de mà, o sigui que un pot de canari s'acumula tot sol,
avui mateix.

L'única regla que toca l'estructura dels pots és `start_hand` del pòquer: fon
tots els pots en un de sol **conservant-ne les fitxes** (el que la mà anterior
va deixar al pot passa a la nova). No perd res, però sí que perd els noms: un
pot creat a mà («poso») desapareix fos dins el principal. No afecta cap joc de
la taula del §4, perquè només passa en mode pòquer.

Una peça que sí que falta és **cobrar a tots alhora** (la penalització del
canari, l'ante de cada mà): avui són N `bet` solts. Vegeu §3.

### Vides / rondes perdudes — probablement no cal codi

Una vida és una fitxa. Un jugador amb tres vides és un jugador amb una pila de
tres, perdre una vida és un `bet` d'una unitat i quedar eliminat és arribar a
zero. El llibre major ja ho fa tot.

**Recomanació: no implementar-ho com a estructura pròpia.** Si algun dia cal,
serà per presentació (mostrar cors en lloc de xifres), no per lògica. Això
elimina una de les quatre primitives del pla.

### Liquidació a tant el punt — l'únic buit real

Cap operació de la capa A ho expressa. Demana dues coses que el nucli no té:

1. Registrar **punts** per mà i per jugador, que no són fitxes i no es mouen
   entre piles.
2. Una conversió al tancament: `fitxes = punts × taxa`, amb la taxa fixada
   quan es crea la taula.

És la peça de més retorn per línia de codi de tot el document: obre chinchón,
skat, doppelkopf, preferans i sheepshead, i el mercat alemany de skat i
doppelkopf és massiu i no té res lliure ni multi-dispositiu.

*Decisió a prendre:* si els punts viuen al mateix registre de transaccions amb
una unitat diferent, o en un registre paral·lel. La primera opció manté l'undo
i l'auditoria gratis i és la que recomano.

*Verificat:* `TableState` no té cap noció de punts. Seguiment a
[#6](https://github.com/tablechips/tablechips/issues/6), que afegeix una
condició que aquí faltava: la taula no pot crear fitxes, així que la
conversió ha de ser un seguit de transferències entre jugadors que **sumi
zero**, no un pagament des del no-res.

---

## 3. Peces que falten a la capa A

Tres candidates, sortides de contrastar la llista de jocs amb les operacions
existents:

**Transacció atòmica de grup.** La banca paga a quatre jugadors alhora; tots
els jugadors posen l'ante alhora. Avui són N operacions independents, i això
trenca l'undo: desfer «la liquidació» hauria de tornar enrere la ronda sencera,
no l'últim pagament. Amb la promesa d'undo il·limitat i registre íntegre
(§3.7 del pla), això no és cosmètic.

**Aquesta és la recomanació més important del document.** Si el nucli encara no
agrupa transaccions, afegir-ho ara és barat; després de blackjack i pòquer, no.

*Verificat:* no les agrupa, i el problema ja existeix sense ordres de grup.
Una ordre pot escriure més d'un esdeveniment (aixecar-se amb una aposta
pendent n'escriu dos), però `undo` en treu un. Prova feta: un jugador amb 30
apostats s'aixeca, l'amfitrió desfà una vegada, i el jugador torna a estar
assegut **sense** l'aposta. Cap fitxa es perd, però un «Desfer» tira enrere
mig moviment. Seguiment a
[#5](https://github.com/tablechips/tablechips/issues/5), amb un matís: el grup
hauria de ser el que l'amfitrió ha tocat, no la ronda sencera.

**Pot → pot.** Necessari si el pot acumulat del canari es reparteix en capes, o
si una mà nova ha d'absorbir el pot de l'anterior. Possiblement ja resolt per
`split_pots`; cal mirar-ho abans d'afegir res.

*Verificat:* `split_pots` **no** ho cobreix. No mou fitxes d'un pot a un
altre: substitueix el pot únic per capes calculades a partir del que cada
jugador ha posat a la mà, només en mode pòquer, i només si el pot encara
coincideix amb el que s'hi ha posat. L'absorció del pot anterior sí que existeix,
però només dins `start_hand` del pòquer. En mode manual no hi ha cap moviment
pot → pot. Com que el pot del canari s'acumula sense moure's (§2), **avui no
bloqueja cap joc de la taula**; no cal afegir-lo fins que n'aparegui un que ho
demani.

**Apostes obligatòries per posició.** Cegues i antes. El moviment és un `bet`
normal; el que falta és el disparador automàtic segons el seient. Si el mode de
pòquer ja ho fa, val la pena extreure-ho, perquè el canari i el poch en fan un
ús gairebé idèntic.

*Verificat:* el pòquer ho fa, però només les cegues (petita i gran), no l'ante,
i està escrit dins les regles del pòquer (`start_hand` només s'accepta en mode
pòquer). El disparador per seient —el botó que roda i qui va darrere seu— és
el que caldria extreure. Les cegues van dins un sol esdeveniment
(`HandStarted`), així que ja es desfan juntes: és el patró a seguir per a
l'ante de tothom.

---

## 4. Jocs i estructures que necessiten

| Joc | Banca rot. | Pot acum. | Ante/oblig. | Punts | Estat |
|---|:---:|:---:|:---:|:---:|---|
| 7 i mig | ✓ | | | | mode propi |
| Blackjack | banca fixa | | | | mode propi |
| Pòquer (Hold'em) | | | ✓ | | mode propi |
| Quinze, 31 | ✓ | | | | manual |
| Muntet (monte) | ✓ | | | | manual |
| Punt i banca | banca fixa | | | | manual |
| Canari (julepe) | | ✓ | ✓ | | manual |
| Loo | | ✓ | ✓ | | manual |
| Poch | | ✓ | ✓ | | manual |
| Toepen | | ✓ | | | manual |
| Brag | | | ✓ | | manual |
| Nap, solo whist | | | | | manual |
| Newmarket / Michigan | | ✓ | ✓ | | manual |
| Pope Joan | | ✓ | ✓ | | manual |
| Mus | | | | | manual |
| Truc | | | | | manual |
| Chinchón | | | | ✓ | **no cobert** |
| Skat | | | | ✓ | **no cobert** |
| Doppelkopf | | | | ✓ | **no cobert** |
| Preferans | | ✓ | | ✓ | **no cobert** |
| Sheepshead | | | | ✓ | **no cobert** |

Els tres jocs de banca que la taula marca com a manuals (quinze, muntet, punt i
banca) es poden jugar avui en manual amb `transfer`, pagant cada jugador
directament a qui té la banca. Estarien més ben servits pel rol de banca que ja
existeix (§2), i fer-ho és afegir-los a `isBankGame`; no cal cap peça nova.

**Jocs que no en treuen res i no s'han de perseguir:** botifarra, brisca, tute,
escombra, subhastat, belote, scopa. Són punts purs sense aposta i ja tenen
comptadors de sobres.

**Prioritat local (zona del projecte):** canari, truc, set i mig, muntet i
subhastat. Són els jocs de cafè documentats i d'on sortiran els primers
provadors.

---

## 5. Conclusions

1. **16 dels 21 jocs de la taula ja es poden jugar avui**, sense una línia de
   codi nova: 3 amb mode propi i 13 amb el mode manual. Aquesta és la
   justificació que faltava per escrit.
2. **Les quatre primitives del pla en són tres.** Vides es modela amb fitxes i
   no necessita estructura pròpia.
3. **L'únic buit funcional és la liquidació a tant el punt**, que bloqueja cinc
   jocs, entre ells els dos de més abast de mercat
   ([#6](https://github.com/tablechips/tablechips/issues/6)).
4. **La transacció atòmica de grup és la mancança més urgent**, perquè afecta
   una promesa central del producte (undo íntegre) i encarir-la és qüestió de
   temps. La verificació ha confirmat que el problema ja existeix avui
   ([#5](https://github.com/tablechips/tablechips/issues/5)).
5. La xifra correcta d'abast és **una trentena de jocs**, no una cinquantena.
   Comptant variants regionals s'hi arriba, però la llista defensable és
   aquesta.

## 6. Verificat contra el codi

Les quatre preguntes que quedaven obertes, contestades llegint el nucli i, on
calia, provant-lo:

| Pregunta | Resposta |
|---|---|
| El pot és de la mà o de la taula? | **De la taula.** Cap regla el buida; en manual un pot s'acumula sol. `start_hand` del pòquer fon els pots en un conservant-ne les fitxes. |
| El rol de banca és reutilitzable? | **Sí**: estat de taula i ordre pròpia, compartits pels dos jocs de banca. **No** hi ha regla de traspàs: només l'amfitrió el mou. |
| `split_pots` cobreix pot → pot? | **No**: calcula capes a partir de les aportacions, només en pòquer. Pot → pot no existeix, i avui no bloqueja cap joc. |
| Hi ha agrupació atòmica? | **No**, i ja té conseqüències: una ordre pot escriure dos esdeveniments i l'undo en desfà un ([#5](https://github.com/tablechips/tablechips/issues/5)). |

La revisió també va trobar un error a la fase 7, ja corregit: el nucli
acceptava donar un pot lateral a qui no hi havia arribat. Les pantalles
amagaven l'opció, però la regla no hi era, i el protocol promet que el servidor
ho valida tot. Ara la rebutja, i un amfitrió que no estigui d'acord amb la
divisió la desfà i reparteix a mà.

Si d'una revisió posterior surt algun joc no cobert que valgui la pena,
**obre-li una issue pròpia** en comptes d'ampliar aquest document.
