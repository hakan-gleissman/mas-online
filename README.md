# MAS Online

En modulär Spring Boot-applikation för kortspel online. Första spelmodulen är MAS.

## Kör lokalt

```bash
mvn spring-boot:run
```

Öppna sedan `http://localhost:8080`.

## Status

- Startsida med spelkatalog
- MAS-lobby med skapa match och gå med i väntande match
- Spelbord med Thymeleaf, tunn JavaScript-klient och native WebSocket
- Väntande MAS-match:
  - alla spelare kan föreslå vad förloraren ska bli "dagens"
  - matchskaparen väljer vilket förslag som gäller
  - matchskaparen startar spelet
- Omgång 1:
  - startknapp som låser matchen för fler spelare
  - aktiva spelaren skickar kort från hand eller drar från högen och skickar
  - mottagaren svarar med samma färg om möjligt
  - högre kort ger mottagaren sticket
  - lägre kort ger avsändaren sticket
  - saknad färg gör att mottagaren tar upp kortet
  - spelare fylls automatiskt upp till tre kort så länge högen räcker
  - sista spelade kortets färg sparas som trumf när omgång 1 är klar
- Omgång 2:
  - vunna stick från omgång 1 blir spelarens hand
  - ingen hög används i omgång 2
  - aktuellt stick har fast antal kort baserat på spelarna som hade kort när sticket startade
  - när alla i sticket har lagt varsitt kort kastas korten bort
  - den som lade sista kortet startar nästa stick om spelaren fortfarande har kort
  - sista spelaren med kort kvar utses till dagens valda förlorartitel

## Nästa naturliga steg

- Bättre matchinställningar, till exempel max antal spelare
- Persistens för matcher och spelare om servern startas om
