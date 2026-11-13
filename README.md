# Super Star Trek voor Android

Een Android-versie van **Super Star Trek** (Bob Leedom / David Ahl, 1978), het klassieke terminalspel uit *BASIC Computer Games*. Het spel is regel voor regel uit de originele Microsoft BASIC-listing overgezet. Alle teksten, berichten en zelfs de beroemde typfouten ("CONGRULATION", "NAVAGATION") zijn behouden. De bediening is aangepast voor een telefoon in portretstand.

- Pakket: `nl.rarevogel.startrek`
- Versie: 1.0
- Werkt vanaf Android 5.0 (API 21), target API 34
- Geen internet, geen permissies, geen advertenties

## Het spel

Je bent kapitein van de USS Enterprise. Klingon-oorlogsschepen zijn het melkwegstelsel binnengevallen, en jij moet ze allemaal vernietigen voordat de stardate verstreken is. Het stelsel bestaat uit 8×8 kwadranten, en elk kwadrant weer uit 8×8 sectoren. Onderweg kun je bij starbases (`>!<`) aanmeren om energie en torpedo's aan te vullen en schade te laten repareren.

| Symbool | Betekenis |
|---|---|
| `<*>` | Enterprise |
| `+K+` | Klingon-kruiser |
| `>!<` | Starbase |
| ` * ` | Ster |

### Commando's

| Commando | Functie |
|---|---|
| `NAV` | Koers zetten (koers 1–9, warp 0–8) |
| `SRS` | Korte-afstandsscan van het huidige kwadrant |
| `LRS` | Lange-afstandsscan van de omliggende kwadranten |
| `PHA` | Phasers afvuren (energie wordt over de vijanden verdeeld) |
| `TOR` | Fotontorpedo afvuren |
| `SHE` | Schilden op- of afschalen |
| `DAM` | Schaderapport (en reparatie bij een starbase) |
| `COM` | Library-computer (functies 0–5) |
| `XXX` | Commando neerleggen |

### Koersen

```
  4  3  2
   \ | /
 5 -<*>- 1
   / | \
  6  7  8
```

Tussenwaarden zijn toegestaan: 1.5 ligt precies tussen 1 en 2. Eén warpfactor is één kwadrant ver; warp 0.125 is één sector.

### Library-computer

| Functie | Inhoud |
|---|---|
| 0 | Cumulatief galactisch overzicht |
| 1 | Statusrapport |
| 2 | Koers en afstand naar de Klingons in dit kwadrant |
| 3 | Koers en afstand naar de starbase in dit kwadrant |
| 4 | Richting/afstand-calculator |
| 5 | Kaart met de regionamen van het stelsel |

In de galaxykaart betekent een getal als `207`: 2 Klingons, 0 starbases, 7 sterren.

## Bediening op de telefoon

Het scherm bestaat van boven naar beneden uit:

1. **Bovenbalk:** `?` voor de instructies, `A-` en `A+` voor de lettergrootte, `NIEUW` voor een nieuw spel.
2. **Brugweergave:** altijd zichtbaar. Met twee tabbladen wissel je tussen
   - **SHORT RANGE SCAN:** de sectoren met de status ernaast, en
   - **GALAXY RECORD:** de kaart van het hele stelsel, met Klingons in rood, starbases in cyaan en je eigen kwadrant omkaderd.
3. **Terminal:** de originele tekstuitvoer in het VT323-lettertype. Knijp om in of uit te zoomen.
4. **Invoerregel**, met een `ABC`-knop voor het gewone toetsenbord.
5. **Bedieningspaneel:** dat past zich aan aan de vraag die het spel stelt:
   - bij `COMMAND?` een 3×3-raster met de commandoknoppen,
   - bij een koers een sleepbaar kompas met een numeriek toetsenblok,
   - bij aantallen en warpfactoren een toetsenblok met snelkeuzes,
   - bij de computer zes functieknoppen,
   - bij ja/nee-vragen grote Y- en N-knoppen.

### Richten door te tikken

Zolang de library-computer werkt, kun je een koers ook aanwijzen:

- **Tik een sector** in de korte scan: de koers wordt ingevuld en een stippellijn toont de baan. Bij `NAV` wordt ook de juiste warpfactor alvast klaargezet.
- **Tik een kwadrant** op de galaxykaart om er in één sprong naartoe te gaan.
- **Tik hetzelfde doel nog een keer** om te bevestigen (bij een torpedo: vuren).

Is de computer beschadigd, dan moet je de koers zelf invoeren, net als in het origineel.

### Opslaan

Het spel wordt automatisch bewaard bij elke `COMMAND?`-prompt. Als Android de app afsluit, ga je verder waar je was, met het laatste stuk van de terminaltekst erbij.

## Verschillen met de listing uit 1978

De spelregels en kansberekening zijn ongewijzigd. Alleen een paar bekende fouten in de listing zijn gerepareerd:

- **Regel 440:** de starbase-teller begon op 2 in plaats van 0, waardoor het aantal starbases niet klopte.
- **Regel 4410:** de nauwkeurigheid van de phasers hing af van de verkeerde schaderegel (`D(7)` in plaats van `D(8)`), terwijl het bericht over de computer gaat.
- **Regels 8310–8450:** de richtingscalculator deelde door nul als begin- en eindpunt gelijk waren.
- **Afronding:** posities worden per stap vanaf het startpunt uitgerekend en afgerond op 10⁻⁶. Daardoor landt een exact berekende koers ook echt op de doelsector, en kan het schip na een botsing niet meer op sector 0 belanden.

Op een smal scherm wordt de statuskolom van de korte scan onder het raster gezet in plaats van ernaast, en de galaxykaart wordt compacter afgedrukt. De inhoud blijft gelijk.

## Broncode

```
AndroidManifest.xml
build.sh                     bouwscript zonder Gradle
zipalign.py                  minimale zipalign
keystore.jks                 ondertekeningssleutel (wachtwoord: startrek)
assets/vt323.ttf             terminallettertype (SIL Open Font License)
res/mipmap-*/ic_launcher.png
src/nl/rarevogel/startrek/
    Engine.java              de spelmotor, regel voor regel uit de BASIC-listing
    Snapshot.java            momentopname van de toestand voor het scherm
    Game.java                speldraad, terminallog en opslag
    MainActivity.java        schermindeling, knoppen en invoer
    PanelView.java           korte scan, status en galaxykaart
    CompassView.java         sleepbaar kompas
    Instructions.java        de originele instructietekst
test/
    Harness.java             speelt duizenden willekeurige potjes
    nl/rarevogel/startrek/Bot.java   richt-bot die controleert of elke sprong precies landt
```

De commentaren in `Engine.java` vermelden de regelnummers uit de originele listing, zodat je de port ernaast kunt leggen.

### Bouwen

Er is geen Gradle of Android Studio nodig. `build.sh` verwacht in `~/tools`:

- `aapt/package/bin/x64/linux/aapt2`
- `android.jar` (API 30)
- `dx.jar`
- `apksigner.jar`

Daarna:

```sh
./build.sh
```

Het script compileert de resources met `aapt2`, de Java-code met `javac` (Java 8-niveau, zonder lambda's, zodat `dx` ermee overweg kan), zet die om naar `classes.dex`, lijnt de zip uit en ondertekent de APK met v1-, v2- en v3-handtekeningen. Het resultaat is `SuperStarTrek.apk`.

Gebruik voor nieuwe versies steeds dezelfde `keystore.jks`, anders installeert Android de update niet over de bestaande app heen.

### Testen

```sh
javac -d build/test src/nl/rarevogel/startrek/Engine.java src/nl/rarevogel/startrek/Snapshot.java \
      test/Harness.java test/nl/rarevogel/startrek/Bot.java
java -cp build/test Harness 3000                    # willekeurige invoer, zoekt crashes en vastlopers
java -cp build/test nl.rarevogel.startrek.Bot 2000  # speelt gericht, controleert elke landing
```

De spelmotor heeft geen Android-afhankelijkheden en draait dus gewoon op een desktop-JVM.

## Herkomst

- *Star Trek*: Mike Mayfield, 1971.
- *Super Star Trek*: Bob Leedom, 1974, uitgegeven door David H. Ahl in *BASIC Computer Games* (1978).
- Referentielisting: `superstartrek.bas` uit het project [coding-horror/basic-computer-games](https://github.com/coding-horror/basic-computer-games).
- Lettertype: VT323 van Peter Hull (SIL Open Font License).

Star Trek is een handelsmerk van Paramount. Dit is een niet-commerciële hobbyport van een spel uit de jaren zeventig.
