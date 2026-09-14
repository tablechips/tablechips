# TableChips

Fitxes per a una taula de cartes, sense fitxes. Un mòbil fa d'amfitrió i la
resta s'hi connecten per la seua zona wifi, **sense internet, sense comptes i
sense que els convidats hagen d'instal·lar res**.

TableChips porta el compte d'una partida de veritat, amb una baralla de veritat:
set i mig, blackjack, pòquer i un mode manual per a qualsevol altre joc.
**No reparteix cartes**: això és un altre producte.

## Què ens separa de la competència

- **Programari lliure, a F-Droid.** Sense anuncis, sense compres integrades,
  sense comptes, sense telemetria.
- **Els convidats no instal·len res.** L'amfitrió serveix un client web: un
  iPhone i un Android a la mateixa taula només han d'obrir el navegador.
- **No només pòquer.** Set i mig i blackjack de primera classe, i un mode
  genèric per a qualsevol joc no previst (canari, muntet, truc, subhastat…).
- **Fins a deu jugadors**, cadascú amb el seu dispositiu.
- **Zero internet, sense lletra petita.** Es pot jugar en un refugi de muntanya.
- **Sempre es pot escriure l'import.**
- **Undo il·limitat** i registre íntegre.
- **Aguanta una partida llarga:** reconnexió, seient recuperable, estat que
  sobreviu a la mort del procés.
- **Català, castellà i anglès des de la primera pantalla.**

## Com es construeix

Cal un JDK 17 o superior i, per al mòdul `:app`, un SDK d'Android.

```sh
./gradlew build
./gradlew :core:test
```

La resta de la documentació (arquitectura, mòduls, fases i decisions preses) és
al [README en anglès](README.md), al [protocol](docs/protocol.md) i a
[docs/deeplinks.md](docs/deeplinks.md).

## Llicència

GPLv3. Vegeu [LICENSE](LICENSE).
