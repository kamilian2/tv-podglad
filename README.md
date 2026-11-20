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
