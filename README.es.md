# TableChips

Fichas para una mesa de cartas, sin fichas. Un móvil hace de anfitrión y el
resto se conectan por su zona wifi, **sin internet, sin cuentas y sin que los
invitados tengan que instalar nada**.

TableChips lleva la cuenta de una partida de verdad, con una baraja de verdad:
siete y media, blackjack, póquer y un modo manual para cualquier otro juego.
**No reparte cartas**: eso es otro producto.

## Qué nos separa de la competencia

- **Software libre, en F-Droid.** Sin anuncios, sin compras integradas, sin
  cuentas, sin telemetría.
- **Los invitados no instalan nada.** El anfitrión sirve un cliente web: un
  iPhone y un Android en la misma mesa solo tienen que abrir el navegador.
- **No solo póquer.** Siete y media y blackjack como modos de primera clase, y
  un modo genérico para cualquier juego no previsto (julepe, monte, truc…).
- **Hasta diez jugadores**, cada uno con su dispositivo.
- **Cero internet, sin letra pequeña.** Se puede jugar en un refugio de montaña.
- **Siempre se puede escribir el importe.**
- **Deshacer sin límite** y registro íntegro.
- **Aguanta una partida larga:** reconexión, puesto recuperable, estado que
  sobrevive a la muerte del proceso.
- **Catalán, castellano e inglés desde la primera pantalla.**

## Cómo se construye

Hacen falta un JDK 17 o superior y, para el módulo `:app`, un SDK de Android.

```sh
./gradlew build
./gradlew :core:test
```

El resto de la documentación (arquitectura, módulos, fases y decisiones
tomadas) está en el [README en inglés](README.md), en el
[protocolo](docs/protocol.md) y en [docs/deeplinks.md](docs/deeplinks.md).

## Licencia

GPLv3. Véase [LICENSE](LICENSE).
