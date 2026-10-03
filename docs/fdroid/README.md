# F-Droid

Què hi ha preparat perquè TableChips entre a F-Droid, i què falta fer a mà.

## Què llig F-Droid del repositori

| Fitxer | Per a què |
|---|---|
| `fastlane/metadata/android/{en-US,ca,es-ES}/` | La fitxa: títol, descripció curta (80 caràcters com a màxim) i llarga, icona, imatge destacada i captures. F-Droid la llig del codi de cada versió, no de `fdroiddata`. |
| `fastlane/metadata/android/*/changelogs/<versionCode>.txt` | Les novetats de cada versió. La versió anglesa la genera el workflow de release; la catalana i la castellana, si es volen, s'escriuen a mà a la release PR. |
| `version-code.txt` | El `versionCode` com a número literal. Gradle el calcula a partir de `version.txt`, però el comprovador d'actualitzacions de F-Droid només sap llegir un número escrit. `scripts/sync_version.py` el genera, el workflow de release el posa a la release PR, i Gradle s'atura si no quadra amb `version.txt`. |
| `docs/fdroid/io.github.tablechips.yml` | La recepta per a `fdroiddata`. No la llig ningú d'ací: és la còpia de treball del que s'envia a F-Droid. |

## Les captures

Les dibuixen les pantalles de veritat, a la JVM amb Robolectric, en els tres
idiomes:

```sh
TABLECHIPS_SCREENSHOTS=fastlane/metadata/android \
  ./gradlew :app:testDebugUnitTest --tests '*StoreScreenshots*'
```

Sense la variable, el test se salta. Quan canvie una pantalla, es tornen a
generar i es pugen amb el canvi.

La icona (`en-US/images/icon.png`) i les imatges destacades
(`*/images/featureGraphic.png`) s'han fet a partir de la marca i de les fitxes de
Refugi. Si canvia la identitat, s'han de refer.

## Compilació reproduïble

L'APK de release és idèntic byte a byte entre dues compilacions del mateix codi,
des de directoris diferents i amb git o sense. La sola diferència que hi havia
era el fitxer on AGP apunta el commit (`version-control-info.textproto`), i està
desactivat (`vcsInfo`, a `app/build.gradle.kts`).

Gràcies a això, la recepta porta `Binaries` i `AllowedAPKSigningKeys`. F-Droid
compila, compara amb l'APK de la release de GitHub i, si coincideixen, publica
l'APK signat amb la nostra clau. Qui l'haja instal·lat des de GitHub pot
actualitzar des de F-Droid sense desinstal·lar, i a l'inrevés.

Si algun dia F-Droid diu que no coincideixen, la solució ràpida és traure
aquelles dues línies de la recepta: F-Droid signarà amb la seua clau i ja està,
però aleshores els dos canals deixen de ser intercanviables.

## Enviar-la

1. **Fer una release que porte tot això.** La v0.1.0 és anterior a aquests
   fitxers. La recepta apunta a la v0.1.1: release-please obrirà la seua release
   PR quan aquesta branca arribe a `main`.
2. **Comprovar la recepta en local**, si es pot, amb `fdroidserver`:
   ```sh
   git clone https://gitlab.com/fdroid/fdroiddata.git && cd fdroiddata
   cp ../tablechips/docs/fdroid/io.github.tablechips.yml metadata/
   fdroid readmeta && fdroid lint io.github.tablechips
   fdroid build -v -l io.github.tablechips   # necessita l'SDK d'Android
   ```
3. **Obrir el *merge request*** a
   [gitlab.com/fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata), amb la
   recepta a `metadata/io.github.tablechips.yml` i la plantilla «App inclusion»
   del formulari. Cal un compte de GitLab.
4. **Mentrestant, IzzyOnDroid**: llig directament les releases de GitHub i sol
   ser més ràpid. La petició es fa obrint una issue a
   [gitlab.com/IzzyOnDroid/repo](https://gitlab.com/IzzyOnDroid/repo).

Un cop acceptada, cada release nova arriba a F-Droid tota sola: el comprovador
troba l'etiqueta `vX.Y.Z`, llig `version-code.txt` i `version.txt`, i compila.
