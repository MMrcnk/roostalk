# Roostalk – instrukcja dla Claude Code

Aplikacja Android: zapisuje numer + datę/godzinę połączeń PRZYCHODZĄCYCH z wybranej karty SIM
do arkusza Google (Apps Script web app, żądanie GET – jak MacroDroid).

## Interfejs (NIE rozbudowywać bez wyraźnej prośby)
- Na górze: „Roostalk” (DM Sans Medium, czarny, wyśrodkowany), bez paska akcji
- Cała aplikacja w DM Sans (app/src/main/assets/fonts)
- Tekst: „pamiętaj o wyrażeniu zgody w ustawieniach na dostęp do rejestru połączeń!”
- Pole „wklej link” (pełny URL lub samo ID wdrożenia AKfycb…) + niebieskie koło z 3 kropkami → popup SIM1/SIM2
- Przełącznik: szary „WŁĄCZ” po lewej → zielony „WŁĄCZONE” przesunięty w prawo
- Tło: bardzo delikatny (ok. 4% czerni) kogut z logo – res/drawable-nodpi/bg_chicken.png, ImageView pod ScrollView
- Zmiana SIM (gdy karta już była wybrana): popup „Hej! Zmieniasz SIM… wpisz „zmieniam””, czerwony „ZMIEŃ”; pierwszy wybór bez pytania
- Wyłączenie: popup (jasnoniebieski) „Żeby wyłączyć wpisz „WYLACZAM””, pole tekstowe, czerwony przycisk „WYŁĄCZ” – bez poprawnego wpisu nie wyłącza
- Mały numer wersji na dole (klik = sprawdź aktualizację)

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
- Nie zmieniaj kluczy w Prefs.kt ("url", "slot", "enabled", "last_call_id") – po aktualizacji ustawienia muszą zostać
