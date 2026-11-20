#!/bin/sh
# Instalacja wtyczki PiP Naziemna na Enigma2 (OpenATV)
D=/usr/lib/enigma2/python/Plugins/Extensions/PipNaziemna
URL=https://raw.githubusercontent.com/kamilian2/tv-podglad/main/enigma2/PipNaziemna
mkdir -p "$D"
for f in __init__.py plugin.py; do
  wget -q -O "$D/$f" "$URL/$f" || { echo "Błąd pobierania $f"; exit 1; }
done
echo "Zainstalowano PiP Naziemna w $D - restart GUI..."
init 4 && sleep 3 && init 3
