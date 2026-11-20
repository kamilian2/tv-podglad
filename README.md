# TV Podgląd – zrzuty i podgląd ekranu Philips Android TV przez HTTP

Aplikacja na Android TV (od 8.0 Oreo). Przechwytuje ekran telewizora (MediaProjection) i wystawia go w sieci domowej:

| Adres | Co robi |
|---|---|
| `http://IP_TV:8080/` | strona z podglądem na żywo (telefon, tablet, PC) |
| `http://IP_TV:8080/snapshot.jpg` | pojedynczy zrzut ekranu (JPG) |
| `http://IP_TV:8080/snapshot.jpg?download` | zrzut do pobrania |
| `http://IP_TV:8080/stream.mjpg` | strumień MJPEG (Home Assistant, VLC, przeglądarka) |
| `http://IP_TV:8080/status` | JSON ze stanem |

Bez zewnętrznych bibliotek, kodowanie JPEG tylko wtedy, gdy ktoś ogląda.

## 1. Budowanie APK (GitHub Actions)
1. Załóż nowe repo na GitHubie (np. `kamilian2/tv-podglad`) i wrzuć zawartość tego folderu.
2. Zakładka **Actions** → workflow **Build APK** uruchomi się sam po pushu.
3. Po zakończeniu pobierz artefakt `tv-podglad-apk` (w środku `app-debug.apk`).

## 2. Instalacja na TV (adb z PC)
```bat
adb connect IP_TV:5555
adb install -r app-debug.apk
```

**Ważne – zgoda bez okienka.** Android przy każdym starcie przechwytywania pyta „Rozpocząć nagrywanie/przesyłanie?”.
Żeby działało automatycznie (też po restarcie TV), nadaj stałą zgodę:
```bat
adb shell appops set pl.kamil.tvpodglad PROJECT_MEDIA allow
```
(Na Androidzie 8 możesz też w okienku zaznaczyć „Nie pokazuj ponownie”.)

Opcjonalnie wyłącz optymalizację baterii, żeby system nie ubijał usługi:
```bat
adb shell dumpsys deviceidle whitelist +pl.kamil.tvpodglad
```

## 3. Uruchomienie
Otwórz na TV aplikację **TV Podgląd** → pokaże adres, np. `http://192.168.1.50:8080`. Naciśnij **Ukryj** – usługa działa w tle.
Po włączeniu TV startuje sama (`Config.AUTOSTART`).

Start/stop zdalnie przez adb (np. z Home Assistanta):
```bat
adb shell am start -n pl.kamil.tvpodglad/.MainActivity --ez autostart true
adb shell am stopservice pl.kamil.tvpodglad/.CaptureService
```

## 4. Telefon
- **Przeglądarka:** otwórz `http://IP_TV:8080` → „Dodaj do ekranu głównego” i masz „aplikację”.
- **Home Assistant (aplikacja na telefon):** Ustawienia → Urządzenia i usługi → Dodaj integrację → **MJPEG IP Camera**
  - MJPEG URL: `http://IP_TV:8080/stream.mjpg`
  - Still image URL: `http://IP_TV:8080/snapshot.jpg`
  
  Powstaje encja `camera.*` – kafelek z podglądem TV na dashboardzie, w powiadomieniach itd.

## 5. Ustawienia (`Config.java`)
- `PORT` – domyślnie 8080
- `MAX_WIDTH` – 1280 (720p); daj 1920 dla Full HD (większe obciążenie)
- `FPS` – 4 kl./s na żywo
- `JPEG_QUALITY` – 70
- `ACCESS_KEY` – ustaw np. `"tajne123"`, wtedy adresy wymagają `?key=tajne123` (zalecane, jeśli w sieci są goście)

## Ograniczenia
- **Netflix, Disney+, HBO Max, Prime Video i inne treści z DRM** będą czarne – system blokuje przechwytywanie chronionych warstw. YouTube, menu, launcher, aplikacje, gry zwykle działają.
- **Tuner TV / HDMI** na Philipsie bywa renderowany na osobnej, chronionej warstwie sprzętowej – wtedy obraz z kanału lub HDMI też może być czarny (zależy od modelu i firmware).
- Gdy TV przejdzie w czuwanie, klatki przestają przychodzić – na stronie zostaje ostatni obraz.

---

# Okienko TV naziemnej (od wersji 1.1)

Oglądasz HDMI (PlayStation, dekoder satelitarny…), naciskasz wybrany przycisk pilota i w prawym dolnym rogu pojawia się okienko z kanałem z tunera naziemnego. Ponowne naciśnięcie chowa okienko.

## Jednorazowa konfiguracja (adb z PC)
```bat
adb install -r app-debug.apk
:: dostęp do listy kanałów
adb shell pm grant pl.kamil.tvpodglad com.android.providers.tv.permission.READ_EPG_DATA
:: włączenie usługi przechwytującej przycisk pilota
adb shell settings put secure enabled_accessibility_services pl.kamil.tvpodglad/pl.kamil.tvpodglad.KeyService
adb shell settings put secure accessibility_enabled 1
```
Uwaga: polecenie `enabled_accessibility_services` nadpisuje listę włączonych usług ułatwień dostępu. Jeśli masz inne, sprawdź je najpierw: `adb shell settings get secure enabled_accessibility_services` i dopisz po dwukropku. Usługę można też włączyć w TV: Ustawienia → Ułatwienia dostępu → „TV Podgląd – przycisk okienka TV”.

## Ustawienia (aplikacja TV Podgląd na TV)
- **Przycisk:** domyślnie niebieski (`PROG_BLUE`). „Przycisk: …” → naciśnij dowolny przycisk pilota, żeby go przypisać.
- **Źródło:** automatycznie tuner naziemny albo ręczny wybór z listy wejść.
- **Kanał:** kanał w okienku. Gdy okienko jest widoczne, CH+/CH− zmienia w nim kanał (zapamiętywany).
- **Rozmiar** (mały/średni/duży) i **dźwięk z okienka** (domyślnie wyłączony, żeby nie mieszał się z HDMI).

## Sterowanie z Home Assistanta / przeglądarki
```
http://IP_TV:8080/pip/toggle   pokaż/ukryj
http://IP_TV:8080/pip/show     pokaż
http://IP_TV:8080/pip/hide     ukryj
http://IP_TV:8080/pip/next     następny kanał
http://IP_TV:8080/pip/prev     poprzedni kanał
http://IP_TV:8080/pip/status   stan okienka
http://IP_TV:8080/tv/inputs    diagnostyka: wejścia i kanały widoczne dla aplikacji
```
(Serwer HTTP działa, gdy uruchomiony jest podgląd ekranu.)

## Ograniczenie sprzętowe
Okienko używa oficjalnego mechanizmu Android TV (`TvView`). Czy obraz z tunera może być wyświetlany **równocześnie** z HDMI, zależy od tego, czy procesor telewizora ma drugi dekoder wideo. Jeśli nie ma, możliwe skutki to czarne okienko z komunikatem, przełączenie głównego obrazu albo „Tuner niedostępny”. Stan widać w `/pip/status`.
