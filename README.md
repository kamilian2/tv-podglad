# TV Podgląd – okienko TV naziemnej (PiP) dla Philips Android TV

Oglądasz HDMI (PlayStation, dekoder satelitarny…), naciskasz wybrany przycisk pilota i w prawym dolnym rogu pojawia się okienko z kanałem z tunera naziemnego. Ponowne naciśnięcie chowa okienko. Gdy okienko jest widoczne, CH+/CH− zmienia w nim kanał.

## Budowanie
Push do repo → GitHub Actions (**Build APK**) → artefakt `tv-podglad-apk` (`app-debug.apk`).

## Instalacja i konfiguracja (adb z PC)
```bat
adb connect IP_TV:5555
adb install -r app-debug.apk
:: dostęp do listy kanałów
adb shell pm grant pl.kamil.tvpodglad com.android.providers.tv.permission.READ_EPG_DATA
:: włączenie usługi przechwytującej przycisk pilota
adb shell settings put secure enabled_accessibility_services pl.kamil.tvpodglad/pl.kamil.tvpodglad.KeyService
adb shell settings put secure accessibility_enabled 1
```
`enabled_accessibility_services` nadpisuje listę włączonych usług ułatwień dostępu. Jeśli masz inne, sprawdź: `adb shell settings get secure enabled_accessibility_services` i dopisz po dwukropku. Można też włączyć na TV: Ustawienia → Ułatwienia dostępu → „TV Podgląd – przycisk okienka TV”. Usługa startuje sama po włączeniu TV.

Jeśli instalowałeś wcześniejszą wersję (z podglądem ekranu), możesz cofnąć jej zgodę: `adb shell appops set pl.kamil.tvpodglad PROJECT_MEDIA default`.

## Ustawienia (aplikacja TV Podgląd na TV)
- **Usługa przycisku** – stan i skrót do ustawień ułatwień dostępu
- **Przycisk** – domyślnie niebieski; „zmień” → naciśnij dowolny przycisk pilota
- **Źródło** – automatycznie tuner naziemny albo ręcznie z listy wejść
- **Kanał** – kanał w okienku (CH+/CH− przy otwartym okienku też go zmienia i zapamiętuje)
- **Rozmiar** (mały/średni/duży), **Dźwięk** (domyślnie WYŁ), **CH+/CH− w okienku**
- **Test okienka**

## Sterowanie z Home Assistanta (opcjonalne)
```
http://IP_TV:8080/pip/toggle | show | hide | next | prev | status
http://IP_TV:8080/tv/inputs     diagnostyka: wejścia i kanały
http://IP_TV:8080/tv/channels   diagnostyka: wszystkie kanały w bazie systemu
http://IP_TV:8080/pip/tune?ch=ID[&input=ID]   ręczne strojenie kanału (test)
http://IP_TV:8080/              strona z przyciskami na telefon
```
Port i opcjonalny klucz dostępu: `Config.java` (`PORT = 0` wyłącza serwer).

## Ograniczenie sprzętowe
Okienko używa oficjalnego mechanizmu Android TV (`TvView`). Czy tuner może grać **równocześnie** z HDMI, zależy od tego, czy procesor TV ma drugi dekoder wideo. Jeśli nie ma, okienko pokaże komunikat („Tuner niedostępny”, „Rozłączono”) albo TV przełączy cały ekran. Stan: `/pip/status`.
