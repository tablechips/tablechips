# Pla de difusió

Com fem que TableChips arribe a les taules on fa falta, sense trair el que el
fa diferent: sense anuncis, sense comptes, sense telemetria. Aquest document és
el pla viu; els esborranys de text són aquí perquè es puguen revisar en el
mateix lloc on es decideix on es publiquen.

---

## 0. Abans de dir-ho a ningú

Una primera impressió a Reddit només es té una vegada. Un fil que diu «no
funciona amb el meu Xiaomi» als deu minuts enterra el projecte en aquella
comunitat per a mesos. Per tant, el llançament públic **espera** a tres coses:

1. **Que es puga instal·lar sense compilar.** La fase F8 del README (metadades
   d'F-Droid, compilacions reproduïbles) és un requisit, no un detall. Mentre
   F-Droid no l'haja acceptada, cal com a mínim:
   - un APK signat a **GitHub Releases** (amb el canvi de versió i
     l'empremta del certificat al text de la release). Ja està automatitzat
     amb release-please: vegeu «Releasing» al README;
   - la petició a **IzzyOnDroid**, que llegeix directament les releases de
     GitHub i sol ser més ràpid que el repositori principal;
   - el *merge request* a `fdroiddata` amb el fitxer de metadades complet fet
     per nosaltres, no una entrada a la cua de sol·licituds: la documentació
     d'F-Droid diu que és el camí ràpid, i un cop fusionat tarda un o dos dies
     a aparéixer.
2. **Que s'haja jugat de veritat.** Els criteris F3–F7 del README que només
   poden validar persones: una nit de cada joc, amb un iPhone a la taula i
   mòbils de dos fabricants diferents. Cada cosa que es trenque ací és un
   comentari negatiu que no es llegirà a Reddit.
3. **Que hi haja material per ensenyar-ho** (§1).

**Data objectiu:** Nadal és la temporada del set i mig i de les cartes en
família. Si l'app és instal·lable a mitjan novembre, el llançament arriba a
temps per a les sobretaules. Amb F-Droid tardant setmanes en el pitjor cas,
el *merge request* hauria d'estar obert abans de final d'octubre.

---

## 1. El material

Tot es fa una vegada i es reaprofita a tot arreu.

| Peça | Per a què | Notes |
|---|---|---|
| **Vídeo de 20–30 s** | Reddit, Mastodon, landing | El moment màgic: l'amfitrió ensenya el QR, un iPhone l'escaneja amb la càmera, s'obre el navegador i ja està assegut. Sense veu, amb subtítols, en vertical. |
| **GIF de 6–8 s** | Capçalera del README, fils on el vídeo no es reprodueix | El mateix moment, retallat. |
| **5–6 captures** | F-Droid, landing, Reddit | Les sis pantalles de Refugi: inici, codi QR, taula, import, panell de l'amfitrió, banca. En els tres idiomes. |
| **Foto real** | Comunitats de jugadors | Una taula de debò, cartes de debò, mòbils al costat. Ningú creu un mockup. |
| **Metadades fastlane** | F-Droid i IzzyOnDroid | `fastlane/metadata/android/{ca,es-ES,en-US}/`: títol, descripció curta, llarga, captures. Les reaprofita tot el món. |
| **Frase d'una línia** | Tots els títols | Vegeu §2. |

---

## 2. El missatge

### Què diem primer

El que ningú més fa: **els convidats no instal·len res**. Totes les apps del
nínxol (Felt, HG Poker, PokerPot, PartyPot…) demanen que cada jugador la
descarregue, i moltes són només per a iOS. Nosaltres: un mòbil Android fa
d'amfitrió, la resta obri el navegador.

Frases, de més curta a més llarga:

- **ca:** «Fitxes per a jugar a cartes, sense fitxes. Els altres només obrin el
  navegador.»
- **es:** «Fichas para jugar a las cartas, sin fichas. Los demás solo abren el
  navegador.»
- **en:** «Poker chips without the chips. One phone hosts, everyone else just
  opens a browser.»

### Què diem després, segons qui escolta

| Públic | L'argument que li importa |
|---|---|
| Jugadors de pòquer casolà | Ningú instal·la res, fins a deu jugadors, cegues i *side pots*, desfer il·limitat. |
| Famílies, set i mig, Nadal | El set i mig és un mode de primera, no un afegit. En català i castellà. |
| Gent de muntanya, càmping, furgo | Zero internet. Funciona en un refugi. |
| Comunitat FOSS / F-Droid | GPLv3, sense Play Services, sense telemetria, sense comptes. |
| Desenvolupadors | Un servidor Ktor dins d'un *foreground service*, un client web d'un sol fitxer, i per què no WebRTC. |
| Comunitat catalanoparlant | Una app pensada en català des de la primera pantalla, amb vocabulari de cada joc (el «poso» del canari). |

### Què **no** diem mai

- Res que sone a apostes amb diners. TableChips porta el compte de fitxes;
  el que valguen les fitxes és cosa de la taula. Ho repetim a les FAQ, perquè
  és el primer que retrauran els moderadors i el primer motiu de *ban*.
- «Repartix cartes». No ho fa i és una decisió (README, «No card dealing»).
- Comparacions amb noms de la competència als títols. Al cos, si cal, amb
  fets: «les altres demanen que tothom instal·le l'app».

---

## 3. La landing page

Esborrany a [`site/index.html`](../site/index.html). És un sol fitxer amb
l'estètica Refugi, les fonts empaquetades al costat i **cap petició externa**:
la mateixa regla que el client web, perquè una landing amb trackers
contradiria la primera línia del README.

### On viu

**De moment, a GitHub Pages:** `tablechips.github.io/tablechips`. El
desplegament és `.github/workflows/pages.yml`, que publica la carpeta `site/`
cada vegada que canvia a `main`. Cal fer una sola cosa a mà: *Settings →
Pages → Source: GitHub Actions*.

**El domini propi, més endavant.** Quan es compre, n'hi ha prou amb un fitxer
`site/CNAME` i el registre DNS; l'adreça de GitHub continua redirigint. Opcions,
per ordre:

- **`tablechips.app`**: neutre, internacional, diu el que és.
- **`tablechips.cat`**: per a la primera onada, la catalana; demana contingut
  en català i la landing ja en té. Pot redirigir a l'altre.
- **`.bet` no.** Diu «apostes» abans que ningú llija la FAQ que diu que no ho
  és, i els filtres DNS, de control parental i d'escoles i empreses solen
  bloquejar la categoria sencera. Reddit i les xarxes tracten els enllaços
  d'apostes amb més sospita.

### Què hi ha

1. **Capçalera:** la frase, i la pantalla de taula amb les fitxes de fusta
   escampades al voltant. **Les fitxes són el detall clau**: és el que
   l'app substituïx, i s'han de veure abans de llegir res.
2. **La capsa:** les cinc fitxes de Refugi (1, 5, 25, 50, 100) i la compra
   inicial, cinc de cada, 905. La pila del mòbil es dibuixa amb el mateix
   `chipsIn` que l'app i el client web.
3. **Com funciona, en tres passos:** l'amfitrió obri la taula → els altres
   escanegen el codi → jugueu.
4. **Per què aquesta:** els diferenciadors del README, en targetes curtes.
5. **Jocs:** set i mig, blackjack, pòquer, mode manual (canari, muntet, truc…).
6. **FAQ:** cal internet? cal iPhone? és per apostar diners? quants jugadors?
   per què no és a Google Play?
7. **Peu:** GPLv3, codi font, idiomes.

Trilingüe amb el mateix sistema que el client web: el text anglés és al codi
(és el que indexen els cercadors) i el navegador tria català o castellà si
l'usuari els prefereix. Si algun dia la cerca orgànica importa, tres pàgines
estàtiques (`/`, `/ca/`, `/es/`) amb `hreflang` són el pas següent.

### Com mesurem sense espiar

Sense analítica a la landing. Els números que tenim són suficients:

- descàrregues per release a GitHub (l'API les compta);
- *Traffic → Referring sites* de GitHub, que diu d'on ve la gent al repositori;
- vots i comentaris als fils;
- issues obertes per gent que no coneixem: el millor indicador que algú l'ha
  feta servir de debò.

---

## 4. Reddit

### Regles que valen per a tots els fils

- **Llegir la barra lateral abans de publicar.** Moltes comunitats limiten
  l'autopromoció (dies concrets, *flair* obligatori, karma mínim, proporció
  entre contingut propi i aliè). Si no està clar, escriure als moderadors
  abans, no després.
- **Dir que som qui l'ha fet**, a la primera línia.
- **Un fil per comunitat, mai el mateix text.** Res de *crosspost* en sèrie el
  mateix dia: Reddit ho marca com a spam.
- **Text, no enllaç sol.** Els fils de text amb el vídeo pujat a Reddit
  funcionen millor que un enllaç a la landing.
- **Contestar tots els comentaris les primeres dues hores.** És quan es decidix
  si el fil puja o mor.
- **Demanar opinió, no vots.** «Quina pantalla canviaríeu?» genera conversa;
  «doneu-li una estrella» genera *downvotes*.
- **Publicar en hora de la comunitat:** de matí als EUA (15–17 h aquí) per als
  subreddits en anglés; vesprada-nit per als locals.

### Comunitats, en ordre

L'ordre importa: primer els públics xicotets i amables, on un error es
perdona i el *feedback* arriba en el nostre idioma; després els grans.

| Ona | Comunitat | Angle | Idioma |
|---|---|---|---|
| 1 | r/valencia, r/Catalonia, r/catalunya | Feta aquí, en català, per al set i mig de Nadal | ca |
| 1 | r/spain, r/askspain (si ho permeten) | Siete y media, sin instalar nada | es |
| 2 | r/fdroid | Nova app lliure, sense Play Services | en |
| 2 | r/FOSS, r/opensource, r/degoogle | GPLv3, sense comptes ni telemetria | en |
| 3 | r/homegames, r/poker (només si les regles ho permeten) | Ningú de la taula instal·la res | en |
| 3 | r/cardgames, r/boardgames | Per a qualsevol joc de cartes amb fitxes | en |
| 4 | r/androiddev, r/Kotlin | La història tècnica | en |
| 4 | r/androidapps, r/SideProject, r/coolgithubprojects | Llançament general | en |
| 5 | r/camping, r/vandwellers, r/hiking | Funciona sense cobertura | en |

Entre ona i ona, almenys dos o tres dies: el temps de corregir el que s'haja
trencat i d'afegir a la FAQ el que haja preguntat la gent.

### Esborranys

#### Ona 1 — r/valencia / r/catalunya (català)

> **Títol:** He fet una app lliure per a jugar al set i mig sense fitxes: un
> mòbil fa de banca i la resta només obri el navegador
>
> Bones! Soc qui l'ha feta, ho dic d'entrada.
>
> Cada Nadal el mateix: algú porta cigrons, algú altre fitxes de pòquer que no
> arriben, i a la tercera mà ningú sap qui deu què. Així que he fet
> **TableChips**.
>
> - Un mòbil Android obri la taula i fa de zona wifi.
> - La resta escaneja un codi QR i **juga des del navegador**, siga iPhone o
>   Android. No cal instal·lar res ni fer-se cap compte.
> - **No cal internet.** Funciona en un mas o en un refugi.
> - Set i mig i blackjack amb banca, pòquer, i un mode manual per a canari,
>   muntet, truc o el que jugueu a casa.
> - En català, castellà i anglés. Lliure (GPLv3), sense anuncis.
>
> No repartix cartes: es juga amb baralla de veritat, l'app només porta el
> compte.
>
> [vídeo de 20 s]
>
> M'agradaria saber, sobretot, quins jocs jugueu vosaltres que no hi
> encaixen. Enllaç: …

#### Ona 1 — r/spain (castellano)

> **Título:** He hecho una app libre para jugar a la siete y media sin fichas:
> un móvil hace de banca y los demás solo abren el navegador
>
> Lo he hecho yo, lo digo de entrada.
>
> La idea: un Android abre la mesa y su zona wifi; los demás escanean un QR y
> juegan desde el navegador, iPhone o Android, sin instalar nada y **sin
> internet**. Siete y media y blackjack con banca, póquer con ciegas y botes
> laterales, y un modo manual para julepe, monte o lo que juguéis.
>
> No reparte cartas, se juega con baraja de verdad. Gratis, sin anuncios, sin
> cuentas, código abierto.
>
> [vídeo]
>
> ¿Qué juego de cartas con fichas jugáis en casa que no esté? Enlace: …

#### Ona 2 — r/fdroid

> **Title:** TableChips — chips for a card table; guests join from a browser,
> no internet needed (GPLv3, no Play Services)
>
> I'm the developer. TableChips replaces physical chips at a real card game.
> One Android phone hosts over its hotspot and serves a web client, so
> everyone else joins by scanning a QR — iPhone or Android, nothing to
> install.
>
> - No internet at any point, no accounts, no telemetry, no ads.
> - No Google Play Services: the QR scanner is built in, so it's F-Droid-clean.
> - 7½ and blackjack with a bank, poker with blinds and side pots, and a manual
>   mode for anything else.
> - Up to ten players, unlimited undo, survives the host app being killed.
> - Catalan, Spanish and English.
>
> [F-Droid link] · [source]
>
> Feedback on devices where the hotspot or the QR scan misbehaves is the most
> useful thing anyone could send me.

#### Ona 3 — r/homegames / r/poker

> **Title:** I built a free chip tracker for home games where nobody at the
> table has to install anything
>
> Every home-game app I tried makes each player download it, which in
> practice means the one friend with an iPhone and no storage holds up the
> table for ten minutes.
>
> TableChips works the other way around: the host's Android phone runs the
> table over its own hotspot, and everyone else joins from their browser by
> scanning a QR. Blinds, a moving button, call amounts, side pots, rebuys,
> unlimited undo. No internet, no accounts, no ads — it's open source.
>
> It tracks chips only: what a chip is worth is up to your table.
>
> [video]
>
> Hosts: what's the one thing you always end up fixing by hand at the end of
> the night?

#### Ona 4 — r/androiddev

> **Title:** Serving a web client from a Ktor server inside a foreground
> service, so guests can join a local game from any browser over the hotspot
>
> Writeup on the architecture of an open-source app I've been building: why
> plain `http://` + `ws://` on the hotspot instead of WebRTC (no secure
> context, no signalling server, no internet), why the host talks to its own
> server over localhost like any other client, how the ledger survives the
> process being killed, and how the whole thing is tested on the JVM with
> Robolectric and no emulator.
>
> [enllaç a un article al blog o al README] · [source]

Aquest fil necessita un article de veritat darrere (§5, blog), no només el
README.

---

## 5. Altres canals

### Botigues i catàlegs

| Canal | Esforç | Per què |
|---|---|---|
| **F-Droid** | mitjà, una vegada | El canal natural. Apareix a «This Week in F-Droid» quan entra una app nova, sense demanar-ho. |
| **IzzyOnDroid** | baix | Arriba abans que F-Droid; útil per al llançament si F-Droid tarda. |
| **GitHub Releases** | baix | Per a qui no fa servir cap botiga; també alimenta Obtainium. |
| **AlternativeTo** | baix | Fitxa com a alternativa a les apps de fitxes de pòquer. La gent la troba buscant l'alternativa lliure. |
| **Catàleg de Softcatalà** | baix | Programari en català. Comunitat fidel i amb newsletter. |
| **Google Play** | alt | Més abast, però demana compte de desenvolupador amb verificació d'identitat, un període de proves tancades amb testers abans de publicar, i les polítiques de jocs d'atzar fan nosa a una app de fitxes. **Després**, si de cas, i només si F-Droid funciona. |
| **App Store (iOS)** | — | No cal: els convidats amb iPhone ja juguen des del navegador. Fer d'amfitrió des d'un iPhone és un altre producte. Ho diem a la FAQ. |

### Comunitats fora de Reddit

- **Mastodon** (fosstodon.org, mastodont.cat): el públic FOSS i el
  catalanoparlant hi viuen. Un compte del projecte, el vídeo fixat, i
  etiquetes #FDroid #FOSS #Android #català.
- **Hacker News — Show HN:** amb la història tècnica, no amb la de producte.
  Un sol intent; dimarts-dijous de matí als EUA.
- **Lobsters:** si algú amb invitació ho vol compartir; no s'hi pot fer
  autopromoció directa.
- **Fòrum d'F-Droid** (forum.f-droid.org): fil a «Apps».

### Premsa i blogs

Un correu curt i personal, amb el vídeo i tres frases. Millor pocs i ben
triats que una llista:

- **En català:** Softcatalà (notícies), VilaWeb (secció de tecnologia),
  À Punt i Catalunya Ràdio (programes de tecnologia; a Nadal, l'angle del set
  i mig hi encaixa).
- **En castellà:** Genbeta, Xataka Android, El Androide Libre.
- **En anglés, FOSS:** It's FOSS, Linux Uprising, el blog d'F-Droid.

### Món real

Aquest és el canal que més s'assembla a l'ús de veritat:

- **Casals fallers, penyes i associacions** on es juga a cartes. Un cartell
  amb el codi QR a la landing.
- **Associacions de truc i campionats locals**: el mode manual cobreix el
  compte de pedres si juguen amb fitxes.
- **Refugis de muntanya i càmpings**: un adhesiu amb QR al taulell de jocs.
  Barat, i és exactament l'escenari per al qual està fet.
- **Bars de jocs de taula.**

### Contingut propi

- **Un article tècnic** («Un servidor web dins del mòbil, per a jugar sense
  internet») al blog o a dev.to. Serveix per a r/androiddev, Show HN i
  Lobsters.
- **Una guia del set i mig** amb les regles i com es juga amb TableChips. És
  el tipus de pàgina que la gent busca a Google al desembre.
- **Vídeos curts** (YouTube Shorts, TikTok, Instagram Reels): el mateix vídeo
  del QR. Opcional, i només si algú del projecte ja hi és.

---

## 6. Calendari

| Quan | Què |
|---|---|
| **Octubre, setmanes 1–2** | Tancar F8: metadades fastlane, release signada a GitHub, *merge request* a `fdroiddata`, petició a IzzyOnDroid. |
| **Octubre, setmanes 3–4** | Nits de joc reals (F3–F7). Captures, vídeo, foto. Landing publicada a GitHub Pages. |
| **Novembre, setmana 1** | Llançament discret: amics, Mastodon, fòrum d'F-Droid. Corregir el que surta. |
| **Novembre, setmana 2** | Ona 1 (Reddit local) + Softcatalà. |
| **Novembre, setmana 3** | Ona 2 (FOSS). AlternativeTo. |
| **Novembre, setmana 4** | Ona 3 (jugadors) + premsa en castellà i català amb l'angle de Nadal. |
| **Desembre, setmana 1** | Ona 4 (tècnica): article, r/androiddev, Show HN. |
| **Desembre** | Nadal: tornar a les comunitats locals només si hi ha novetat real (una versió nova, un joc nou). |
| **Gener** | Revisar números (§3) i decidir si val la pena Google Play. |

---

## 7. Què considerem un èxit

No vanitat, sinó senyals d'ús:

- **Partides de veritat:** issues o comentaris de gent desconeguda que diu
  «ho vam fer servir dissabte».
- **Contribucions:** una traducció nova, un mode de joc proposat, un PR.
- **Descàrregues:** 500 a GitHub + F-Droid abans de Reis seria un bon
  començament per a un nínxol com aquest.
- **Retenció de la comunitat:** que algú la recomane en un fil que no hem
  obert nosaltres.
