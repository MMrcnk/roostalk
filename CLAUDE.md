# RooseTalk – instrukcja dla Claude Code

Aplikacja Android: zapisuje numer + datę/godzinę połączeń PRZYCHODZĄCYCH z wybranej karty SIM
do arkusza Google (Apps Script web app, żądanie GET – jak MacroDroid).

## Interfejs (ciemny motyw – NIE rozbudowywać bez wyraźnej prośby)
- Tło czarne; duży kogut z logo w kolorze #1A1A1A (res/drawable-nodpi/bg_chicken.png), lekko wysunięty w lewo
- Tytuł „RooseTalk” – League Spartan Bold (assets/fonts/LeagueSpartan-Bold.ttf), biały, ~36sp
- Pod nim biały tekst „pamiętaj o wyrażeniu zgody w ustawieniach na dostęp do rejestru połączeń!” (klik = ustawienia aplikacji)
- Karta #292929 (zaokrąglona) z: polem „wklej link” (#F2F2F2, tekst #6E6E6E), polem „wybierz SIM” (to samo), przełącznikiem-pigułką
- Przełącznik: wyłączony = tor #C8D1D9, biała gałka z lewej „włącz”; włączony = tor #2E8446, gałka z prawej „włączony”
- Link: po pierwszym włączeniu zablokowany (szara kłódka, Prefs "url_locked"); okienko zmiany linku otwiera TYLKO klik w kłódkę (klik w tekst = podpowiedź)
- Okienka – wszystkie tej samej szerokości (88% ekranu, maks. 360dp) (#292929, biały tekst, jasne pole, biały przycisk-pigułka z czerwoną obwódką #AE2B22, czerwony tekst):
  - zmiana linku: „Zmieniasz link do arkusza – jeżeli jesteś tego pewien to wklej nowy i kliknij zmień” / „wklej nowy link” / „zmień”
  - zmiana SIM (tylko gdy karta była już wybrana): „… wpisz „zmieniam”” / „zmień”
  - wyłączenie: „Żeby wyłączyć zapisywanie numerów przez RooseTalk wpisz „wyłączam”” / „wyłącz”
- Cała reszta tekstu: DM Sans. Mały numer wersji na dole (klik = sprawdź aktualizację)

## Jak działa
- CallReceiver: RINGING → Sender.onRinging (zapamiętuje numer + kartę; wysyła od razu tylko gdy telefon zablokowany po restarcie, status "nieznany"); IDLE → ScanWorker
- ScanWorker: kilka sekund po rozłączeniu czyta rejestr połączeń i wysyła ze statusem odebrane/nieodebrane/odrzucone; kartę bierze z rejestru, a gdy jej tam nie ma – z chwili dzwonienia
- Parametry GET: caller_id, data (yyyy-MM-dd HH:mm:ss), ts (epoch ms), status, id (do deduplikacji)
- Kod Apps Script: Kod.gs

## Zasady
- Zgodność: minSdk 22 (Android 5.1) – targetSdk 34. Każde nowsze API w `if (Build.VERSION.SDK_INT >= X)`.
  `getSystemService(Context.X_SERVICE)`, `ContextCompat.checkSelfPermission`, operacje w tle w try/catch.
- UI budowany w kodzie (MainActivity.kt), bez XML, po polsku.
- Po każdej zmianie: commit z krótkim opisem po polsku + `git push` na main
  (Actions buduje APK → Release → aplikacja pokazuje „Aktualizuj”).
- Nie zmieniaj applicationId ani podpisu.

## Praca w tle (wymóg: działa przy zamkniętej aplikacji)
- MonitorService: usługa pierwszoplanowa (specialUse), stałe ciche powiadomienie, stopWithTask=false, START_STICKY
- 3 niezależne drogi: odbiornik z manifestu + odbiornik w usłudze + ScanWorker co 15 min (też restartuje usługę)
- BootReceiver: restart telefonu i aktualizacja aplikacji → usługa wraca sama
- Przy włączaniu: zgoda na brak oszczędzania baterii, potem ekran autostartu producenta (raz)
- Tylko orientacja pionowa (screenOrientation=portrait)

## Start po restarcie
- Ustawienia (Prefs) w pamięci device-protected (API 24+) – dostępne przed pierwszym odblokowaniem; stare przenoszone automatycznie
- BootReceiver/CallReceiver/MonitorService: directBootAware; LOCKED_BOOT_COMPLETED uruchamia usługę jeszcze przed PIN-em
- Przed odblokowaniem: wysyłka bezpośrednia (Work.direct), bez WorkManagera i bez skanu rejestru
- Po BOOT_COMPLETED: jeśli jest zgoda „wyświetlanie nad innymi” – Roostalk otwiera się na 1,5 s i chowa w tło
- Nie zmieniaj kluczy w Prefs.kt ("url", "slot", "enabled", "last_call_id", "url_locked") – po aktualizacji ustawienia muszą zostać
