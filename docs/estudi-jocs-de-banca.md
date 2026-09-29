# Estudi de jugabilitat: blackjack i set i mig (#8)

S'han simulat 20 mans de blackjack i 20 de set i mig, amb 5 jugadors i la interfície de `main` després de la fase 7. Es tracta de veure on costa jugar i on s'equivoca l'app, igual que es va fer amb el pòquer.

## Com s'ha fet

- La simulació es fa amb el client web i 5 navegadors (Chromium, pantalla de 412 × 915): Anna, Bru, Carla, Dani i Eva. Anna és l'amfitriona.
- Un script fa els clics com els faria una persona i els apunta tots. De cada clic guarda qui el fa, en quina pantalla i si ha calgut desplaçar-la.
- Les cartes es juguen a la taula: l'app només sap com ha acabat cada mà. Els resultats els decideix un sorteig amb llavor fixa, així la simulació és repetible:
  - 40 % guanya;
  - 46 % perd;
  - 8 % empat;
  - 6 % natural.
- Al set i mig, un 15 % de les mans la banca es passa.
- Cada jugador té unes quantes apostes preferides, i un 10 % de les mans no juga.
- Casos coberts:
  - **Al blackjack:** doblar (7), partir (2) i una banca que no pot pagar.
  - **Al set i mig:** qui fa set i mig es queda la banca (3), i la banca que passa a un altre jugador perquè la demana.
  - **Als dos jocs:** un jugador sense fitxes que torna a comprar, i algú que s'aixeca amb l'aposta posada i torna a seure.
- En acabar, les fitxes quadren als dos jocs: el que hi ha a la taula és el que s'ha comprat (6.335 i 7.240).

Al blackjack, la banca la porta l'amfitriona, que és qui reparteix. Al set i mig, la banca comença amb en Bru i va passant.

## Error trobat i corregit

**Al web, el panell de l'amfitrió no mostrava les mans per resoldre.** La funció que dibuixa l'ordre de les jugades del pòquer es deia `renderHands`, igual que la que dibuixa les mans de la banca. En un fitxer de script, la segona definició substitueix la primera sense avisar. Per això, a `main` no es podia liquidar cap mà ni passar la banca des del web.

Corregit a `24b42ae`. Hi ha un test nou que falla si el client web defineix dues vegades la mateixa funció. És la segona vegada que passa: la primera va ser `renderSeats`.

## El que costa ara

Els recomptes no inclouen l'entrada (nom, entrar i seure: 3 tocs per persona).

| | Blackjack | Set i mig |
|---|---|---|
| Tocs de l'amfitrió en 20 mans | 130 | 141 |
| … dels quals, només per anar al panell i tornar | 63 | 63 |
| Tocs de l'amfitrió per mà (mínim / mitjana / màxim) | 5 / 6,5 / 9 | 5 / 7,0 / 12 |
| Apostes | 76 | 71 |
| Tocs per aposta | 4,1 | 4,2 |
| Tecles de xifres teclejades | 155 | 148 |
| Apostes iguals a l'anterior del mateix jugador | 43 (57 %) | 29 (41 %) |

**Una mà típica de blackjack (mà 1):**
- Bru aposta 25, Carla 100, Dani 10 i Eva 500. Cada aposta és «Apostar», les xifres i «Confirmar»: 4 tocs cadascuna.
- Bru dobla, que són 4 tocs més.
- Anna liquida la mà: «⋯» → «Panell d'amfitrió» → Bru perd → Carla natural → Dani guanya → Eva natural → «←». Són 7 tocs.

**Una banca que no pot pagar (blackjack, mà 2):** Anna toca «Carla guanya» i surt «No tens prou fitxes». Llavors ha de fer 7 tocs:
1. «←»;
2. «Comprar més»;
3. «Confirmar»;
4. «⋯»;
5. «Panell d'amfitrió»;
6. «Carla guanya»;
7. «Dani perd».

**Qui fa set i mig es queda la banca (set i mig, mà 3):** després de liquidar, Anna toca «Canviar» i «Dona-li-la a Anna». Són 2 tocs més dins del panell, o 5 si ja n'havia sortit.

## Què no va bé

Per ordre d'impacte:

1. **Només l'amfitrió liquida, i només des del panell.**
   - La meitat dels tocs de l'amfitrió són per anar al panell i tornar: 63 de 130.
   - Si la banca la porta un altre jugador, el que ha passat a la taula ho ha de traslladar l'amfitrió.
   - Si l'amfitrió juga contra la banca, liquida la seva pròpia mà.
   - A la banca que no és amfitriona, l'app li diu «Resol cada mà des del panell», però no té panell.
2. **Les apostes dels altres no es veuen a la taula.** Les files mostren la pila de cada jugador. La banca només pot saber qui ha apostat quant des del panell, i un jugador que no és amfitrió no ho pot saber de cap manera. En canvi, a la taula de debò les fitxes són davant de cadascú.
3. **Tornar a apostar el mateix costa 4 tocs.** La pantalla d'import proposa sempre la compra dividida per 10 (90), que gairebé mai és el que es vol. El 57 % de les apostes de blackjack i el 41 % de les de set i mig repeteixen l'aposta anterior del mateix jugador.
4. **Doblar costa 4 tocs, i una mà partida no es pot liquidar bé.** Partir és apostar una altra vegada, però l'aposta queda en una sola pila i es liquida amb un únic resultat. Quan les dues mans acaben diferent (una guanya i l'altra empata, o una perd i l'altra empata), no hi ha manera d'expressar-ho, i en 20 mans es va pagar malament 2 vegades.
   - El protocol ja accepta liquidar només una part de l'aposta (`settle` amb `amount`), però cap pantalla ho ofereix.
   - Tampoc es pot fer l'assegurança ni la rendició (perdre la meitat).
5. **Les mans liquidades desapareixen de la llista.** En liquidar una mà, la targeta de sota puja al seu lloc, i el pròxim toc pot caure al jugador equivocat.
6. **No hi ha dreceres per a tota la taula.** Quan la banca es passa (el 15 % de les mans de set i mig) o guanya a tothom, l'amfitrió ha de tocar el mateix resultat jugador per jugador.
7. **Passar la banca és a mà.** La variant casolana en què qui fa set i mig es queda la banca costa 2 a 5 tocs cada vegada, i l'amfitrió se n'ha de recordar.
8. **Quan la banca no pot pagar, el missatge enganya.** Diu «No tens prou fitxes», i qui ho llegeix és l'amfitrió, no la banca. No diu qui no pot pagar ni quant falta. Al set i mig casolà hi ha una norma per a aquest cas, la «banca rebentada»: la banca paga el que pot i la banca passa a un altre.
9. **Al blackjack, la banca hauria de ser la casa.** La casa no aposta i juga amb els fons de la taula, amb normes fixes: demana carta amb 16 o menys i es planta amb 17 o més. A l'app, en canvi, la banca és un jugador assegut, que juga amb la seva pila i ocupa un lloc.

## Propostes

| # | Proposta | Estalvi a la simulació |
|---|---|---|
| 1 | La banca (i l'amfitrió) liquiden des de la taula, tocant la fila de cada jugador que ha apostat | Blackjack: de 6,5 a unes 3,4 tocs per mà per a l'amfitrió; ja no cal passar tota la mà per ell |
| 2 | L'aposta de cada jugador es veu a la seva fila, com al pòquer | Deixa de caldre el panell per saber qui juga |
| 3 | «Apostar 25 un altre cop» en un toc, i «Altre import» per a la resta | De 4,1 a 1 toc en el 41–57 % de les apostes |
| 4 | Blackjack: «Doblar» i «Partir» en un toc, amb cada mà partida liquidada per separat; «Rendir-se» (perdre la meitat) | Doblar passa de 4 tocs a 1; les mans partides es paguen bé |
| 5 | Les mans liquidades es queden a la llista amb el resultat fins a la mà següent | Menys tocs al jugador equivocat |
| 6 | «La banca es passa: paga a tots» i «La banca guanya a tots», amb excepcions | Al set i mig, fins a 4 tocs menys en el 15 % de les mans |
| 7 | Opció de taula (set i mig): «El set i mig es queda la banca», aplicada en liquidar | De 2 a 5 tocs menys cada vegada |
| 8 | Si la banca no pot pagar, dir qui no pot i quant falta, i oferir-li «Comprar més» en un toc; opcionalment, la norma de la banca rebentada | La banca que no pot pagar passa de 7 tocs a 2 |
| 9 | Blackjack: una «casa» amb fons propi que no ocupa cap lloc (decisió pendent) | — |

## Decisions preses

- **La banca del blackjack:** és una casa amb fons propi que no ocupa cap lloc. Es compra com la d'un jugador («Posar 905 a la casa»), i qui reparteix la porta sense que la seva pila es mogui.
- **Les mans partides:** l'app coneix les mans de cada jugador. «Partir» crea una segona mà amb la mateixa aposta, cadascuna es liquida per separat, i cada mà es pot doblar pel seu compte.
- **Quan la banca no pot pagar:** la banca no queda rebentada. Abans d'enviar res, l'app diu quant falta, i la banca hi pot afegir fitxes amb un toc.
- **Propostes implementades:** 1, 2, 3, 5, 6, 7, 8 i 9. De la 4 s'han fet doblar, partir i rendir-se; l'assegurança queda pendent.

## Després dels canvis

La mateixa simulació, amb la mateixa llavor, les mateixes apostes i els mateixos resultats, amb les pantalles noves del client web:
- Liquida la mà qui té la banca, des de la taula.
- Tornar a apostar el mateix és un toc.
- Doblar i partir són un toc.
- Quan tots els que queden acaben igual, es resolen d'un cop.

| | Blackjack abans | Blackjack ara | Set i mig abans | Set i mig ara |
|---|---|---|---|---|
| Tocs per portar la banca en 20 mans (amfitrió i banca) | 132 | 51 | 141 | 49 |
| Tocs per mà per portar la banca (mínim / mitjana / màxim) | 5 / 6,6 / 11 | 1 / 2,5 / 5 | 5 / 7,0 / 12 | 1 / 2,5 / 7 |
| Tocs de tots els jugadors | 312 | 183 | 299 | 212 |
| Tocs per aposta | 4,1 | 2,7 | 4,2 | 3,0 |
| Mans partides pagades malament | 2 | 0 | — | — |
| Tocs amb la pantalla desplaçada | 1 | 0 | 1 | 0 |
| Errors | 1 («No tens prou fitxes») | 0 | 0 | 0 |

Què ha passat en aquesta segona simulació:
- «Paga a tots» i «Guanya a tots» s'han fet servir 13 vegades al blackjack i 14 al set i mig.
- Al set i mig, qui ha fet set i mig s'ha quedat la banca sol 3 vegades, sense cap toc.
- Al blackjack, la banca que no podia pagar ha costat 2 tocs: afegir fitxes i tornar a tocar el resultat.

**Les fitxes quadren als dos jocs.**
- Al set i mig, cada jugador acaba exactament amb les mateixes fitxes que a la primera simulació. Les dreceres no canvien cap càlcul.
- Al blackjack, el resultat canvia, i és el que s'esperava: ara paga la casa i les mans partides es paguen bé.

La preparació de la taula costa alguns tocs més:
- al blackjack, 1 toc per posar fitxes a la casa;
- al set i mig, 4 tocs per activar la norma del set i mig des del panell. També es pot triar en crear la taula.

**Pendent:**
- La simulació s'ha fet només al client web. L'app té les mateixes pantalles i tests de pantalla, però no s'ha provat en un mòbil de veritat.
- Tampoc s'ha jugat amb gent.
