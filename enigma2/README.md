# PiP Naziemna – wtyczka Enigma2 dla Zgemmy (OpenATV 7.x)

Wybrany przycisk pilota Zgemmy pokazuje/chowa okienko PiP z kanałem z wybranego bukietu (np. DVB-T2 z tunera USB) w rogu ekranu. Gdy okienko jest widoczne, **CH+/CH−** zmienia w nim kanał (zapamiętywany).

## Instalacja (SSH/telnet na Zgemmie)
```sh
wget -q -O - https://raw.githubusercontent.com/kamilian2/tv-podglad/main/enigma2/install.sh | sh
```
GUI zrestartuje się samo. Aktualizacja: to samo polecenie.

## Ustawienia
Menu → Wtyczki → **PiP Naziemna**:
- **Przycisk okienka** – domyślnie TEXT; OK na tej linii → naciśnij nowy przycisk
- **Bukiet z kanałami** – np. bukiet z kanałami DVB-T2 (po zeskanowaniu tunera naziemnego)
- **Kanał startowy**, **Rozmiar**, **Położenie** (domyślnie prawy dolny róg), **CH+/CH−**
- Żółty = test okienka, zielony = zapisz

## Wymagania
- PiP w odbiorniku (drugi dekoder – w ustawieniach wtyczki widać, czy odbiornik go zgłasza)
- Wolny tuner dla kanału w okienku: dla TV naziemnej – tuner DVB-T2 (np. USB) i zeskanowane kanały.
  Z jednym tunerem satelitarnym w okienku zagrają tylko kanały z tego samego transpondera co główny obraz.

Odinstalowanie: `rm -rf /usr/lib/enigma2/python/Plugins/Extensions/PipNaziemna && init 4 && sleep 3 && init 3`
