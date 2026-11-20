# -*- coding: utf-8 -*-
#
# PiP Naziemna - wtyczka Enigma2 (OpenATV 7.x, Python 3)
#
# Wybrany przycisk pilota pokazuje/chowa okienko PiP z kanałem z wybranego bukietu
# (np. DVB-T2 z tunera USB). Gdy okienko jest widoczne, CH+/CH- zmienia w nim kanał.
#
from enigma import eActionMap, eServiceCenter, eServiceReference, eTimer, ePoint, getDesktop

from Components.ActionMap import ActionMap
from Components.ConfigList import ConfigListScreen
from Components.Label import Label
from Components.config import (config, configfile, getConfigListEntry, ConfigSubsection, ConfigInteger,
                               ConfigText, ConfigSelection, ConfigYesNo)
from Plugins.Plugin import PluginDescriptor
from Screens.MessageBox import MessageBox
from Screens.Screen import Screen

try:
    from Tools import Notifications
except ImportError:
    Notifications = None

# ---------------------------------------------------------------------------
# Ustawienia
# ---------------------------------------------------------------------------
KEY_TEXT = 388
config.plugins.pipnaziemna = ConfigSubsection()
cfg = config.plugins.pipnaziemna
cfg.key = ConfigInteger(default=KEY_TEXT, limits=(0, 9999))
cfg.bouquet = ConfigText(default="")
cfg.service = ConfigText(default="")
cfg.size = ConfigSelection(default="1", choices=[("0", "Mały"), ("1", "Średni"), ("2", "Duży")])
cfg.corner = ConfigSelection(default="br", choices=[("br", "Prawy dolny"), ("bl", "Lewy dolny"),
                                                     ("tr", "Prawy górny"), ("tl", "Lewy górny")])
cfg.chkeys = ConfigYesNo(default=True)

SIZE_FRAC = {"0": 0.25, "1": 0.33, "2": 0.45}

# Kody klawiszy (linux input)
KEY_OK, KEY_EXIT, KEY_UP, KEY_DOWN, KEY_LEFT, KEY_RIGHT, KEY_POWER, KEY_MENU = 352, 174, 103, 108, 105, 106, 116, 139
BLOCKED_FOR_LEARN = (KEY_OK, KEY_EXIT, KEY_UP, KEY_DOWN, KEY_LEFT, KEY_RIGHT, KEY_POWER, KEY_MENU)
CH_UP_KEYS = (402, 104)     # KEY_CHANNELUP, KEY_PAGEUP
CH_DOWN_KEYS = (403, 109)   # KEY_CHANNELDOWN, KEY_PAGEDOWN

# Współrzędne PiP w Enigma2 (config.av.pip) są w przestrzeni 720x576
PIP_W, PIP_H = 720, 576

try:
    from keyids import KEYIDS
    KEY_NAMES = dict((v, k.replace("KEY_", "")) for k, v in KEYIDS.items())
except Exception:
    KEY_NAMES = {}


def keyName(code):
    return KEY_NAMES.get(code, "kod %d" % code)


def pipAvailable():
    try:
        from Components.SystemInfo import BoxInfo
        v = BoxInfo.getItem("PIPAvailable")
        if v is not None:
            return bool(v)
    except Exception:
        pass
    try:
        from Components.SystemInfo import SystemInfo
        return bool(SystemInfo.get("PIPAvailable", False))
    except Exception:
        return None


def popup(text, error=False):
    if Notifications:
        Notifications.AddPopup(text, MessageBox.TYPE_ERROR if error else MessageBox.TYPE_INFO, 5, "PipNaziemna")


# ---------------------------------------------------------------------------
# Bukiety i kanały
# ---------------------------------------------------------------------------
def getBouquets():
    out = []
    root = eServiceReference('1:7:1:0:0:0:0:0:0:0:FROM BOUQUET "bouquets.tv" ORDER BY bouquet')
    lst = eServiceCenter.getInstance().list(root)
    if lst:
        for ref, name in lst.getContent("SN", True):
            out.append((ref, name))
    return out


def getServices(bouquetRef):
    out = []
    if not bouquetRef:
        return out
    lst = eServiceCenter.getInstance().list(eServiceReference(bouquetRef))
    if not lst:
        return out
    for ref, name in lst.getContent("SN", True):
        sref = eServiceReference(ref)
        if sref.flags & (eServiceReference.isMarker | eServiceReference.isDirectory):
            continue
        out.append((ref, name))
    return out


def guessBouquet(bouquets):
    for ref, name in bouquets:
        n = name.lower()
        if any(w in n for w in ("dvb-t", "dvbt", "terr", "naziem", "mux", "antena")):
            return ref
    return bouquets[0][0] if bouquets else ""


# ---------------------------------------------------------------------------
# Napis z nazwą kanału nad okienkiem
# ---------------------------------------------------------------------------
class PipNaziemnaLabel(Screen):
    def __init__(self, session, width, height, fontSize):
        self.skin = ('<screen name="PipNaziemnaLabel" position="0,0" size="%d,%d" flags="wfNoBorder" '
                     'backgroundColor="#40000000">'
                     '<widget name="text" position="0,0" size="%d,%d" font="Regular;%d" '
                     'foregroundColor="#ffffff" backgroundColor="#40000000" transparent="0" '
                     'halign="center" valign="center" /></screen>') % (width, height, width, height, fontSize)
        Screen.__init__(self, session)
        self["text"] = Label("")

    def place(self, x, y):
        self.instance.move(ePoint(int(x), int(y)))

    def setText(self, t):
        self["text"].setText(t)


# ---------------------------------------------------------------------------
# Sterownik: globalny przycisk + okienko
# ---------------------------------------------------------------------------
class PipController(object):
    def __init__(self, session):
        self.session = session
        self.learnCallback = None
        self.swallowKey = None
        self.services = []
        self.index = 0
        self.label = None
        self.labelTimer = eTimer()
        self.labelTimer.callback.append(self.hideLabel)
        eActionMap.getInstance().bindAction("", -0x7FFFFFFF, self.keyPressed)

    # ---- klawisze ----
    def keyPressed(self, key, flag):
        # flag: 0=wciśnięty, 1=puszczony, 2=powtórzenie, 3=długie, 4=ascii
        if self.swallowKey is not None:
            if key == self.swallowKey:
                if flag == 1:
                    self.swallowKey = None
                return 1
            self.swallowKey = None

        if self.learnCallback is not None:
            if flag == 0 and key not in BLOCKED_FOR_LEARN:
                cb, self.learnCallback = self.learnCallback, None
                self.swallowKey = key
                cb(key)
                return 1
            return 0

        if cfg.key.value and key == cfg.key.value:
            if flag == 1:
                self.toggle()
            return 1

        if self.isShown() and cfg.chkeys.value and (key in CH_UP_KEYS or key in CH_DOWN_KEYS):
            if flag in (0, 2):
                self.step(1 if key in CH_UP_KEYS else -1)
            return 1
        return 0

    # ---- okienko ----
    def isShown(self):
        return bool(getattr(self.session, "pipshown", False)) and hasattr(self.session, "pip")

    def toggle(self):
        if self.isShown():
            self.hide()
        else:
            self.show()

    def loadServices(self):
        bq = cfg.bouquet.value
        if not bq:
            bq = guessBouquet(getBouquets())
        self.services = getServices(bq)
        return self.services

    def show(self):
        if not self.loadServices():
            popup("PiP Naziemna: brak kanałów w wybranym bukiecie.\nUstaw w: Wtyczki → PiP Naziemna.", True)
            return
        self.index = 0
        for i, (ref, name) in enumerate(self.services):
            if ref == cfg.service.value:
                self.index = i
                break

        if self.isShown():
            self.hide()

        from Screens.PictureInPicture import PictureInPicture
        self.session.pip = self.session.instantiateDialog(PictureInPicture)
        try:
            self.session.pip.setAnimationMode(0)
        except Exception:
            pass
        self.session.pip.show()
        self.place()

        ref, name = self.services[self.index]
        if self.session.pip.playService(eServiceReference(ref), checkParentalControl=False):
            self.session.pipshown = True
            try:
                from Screens.InfoBar import InfoBar
                slist = InfoBar.instance and InfoBar.instance.servicelist
                self.session.pip.servicePath = slist.getCurrentServicePath() if slist else []
            except Exception:
                self.session.pip.servicePath = []
            self.showLabel(name)
        else:
            self.session.pipshown = False
            del self.session.pip
            popup("PiP Naziemna: nie można odtworzyć „%s” w okienku\n"
                  "(brak wolnego tunera lub dekodera PiP)." % name, True)

    def hide(self):
        self.hideLabel()
        self.session.pipshown = False
        if hasattr(self.session, "pip"):
            del self.session.pip

    def step(self, delta):
        if not self.isShown() or not self.services:
            return
        self.index = (self.index + delta) % len(self.services)
        ref, name = self.services[self.index]
        if self.session.pip.playService(eServiceReference(ref), checkParentalControl=False):
            cfg.service.value = ref
            cfg.service.save()
            self.showLabel(name)
        else:
            self.showLabel(name + " – niedostępny")

    def geometry(self):
        """Położenie okienka w przestrzeni 720x576 (x, y, w, h)."""
        frac = SIZE_FRAC.get(cfg.size.value, 0.33)
        w = int(PIP_W * frac)
        h = int(PIP_H * frac)
        mx, my = 18, 14
        c = cfg.corner.value
        x = PIP_W - w - mx if c in ("br", "tr") else mx
        y = PIP_H - h - my if c in ("br", "bl") else my
        return x, y, w, h

    def place(self):
        x, y, w, h = self.geometry()
        try:
            self.session.pip.move(x, y)
            self.session.pip.resize(w, h)
        except Exception as e:
            print("[PipNaziemna] move/resize: %s" % e)

    # ---- napis ----
    def showLabel(self, text):
        try:
            ds = getDesktop(0).size()
            W, H = ds.width(), ds.height()
            x, y, w, h = self.geometry()
            px, pw = x * W // PIP_W, w * W // PIP_W
            py, ph = y * H // PIP_H, h * H // PIP_H
            lh = max(30, H // 24)
            ly = py - lh - 4 if cfg.corner.value in ("br", "bl") else py + ph + 4
            if self.label is None:
                self.label = self.session.instantiateDialog(PipNaziemnaLabel, pw, lh, int(lh * 0.65))
            self.label.setText(text)
            self.label.place(px, ly)
            self.label.show()
            self.labelTimer.start(3000, True)
        except Exception as e:
            print("[PipNaziemna] label: %s" % e)

    def hideLabel(self):
        self.labelTimer.stop()
        if self.label is not None:
            try:
                self.label.hide()
                self.session.deleteDialog(self.label)
            except Exception:
                pass
            self.label = None


_controller = None


# ---------------------------------------------------------------------------
# Ekran ustawień
# ---------------------------------------------------------------------------
class PipNaziemnaSetup(ConfigListScreen, Screen):
    skin = """
    <screen name="PipNaziemnaSetup" position="center,center" size="1100,620" title="PiP Naziemna">
        <widget name="config" position="20,20" size="1060,420" scrollbarMode="showOnDemand" />
        <widget name="info" position="20,450" size="1060,110" font="Regular;24" />
        <widget name="key_red" position="20,570" size="250,40" font="Regular;26" foregroundColor="#ff5050" />
        <widget name="key_green" position="290,570" size="250,40" font="Regular;26" foregroundColor="#50ff50" />
        <widget name="key_yellow" position="560,570" size="250,40" font="Regular;26" foregroundColor="#ffff50" />
    </screen>"""

    def __init__(self, session):
        Screen.__init__(self, session)
        self.setTitle("PiP Naziemna")
        self["info"] = Label("")
        self["key_red"] = Label("Anuluj")
        self["key_green"] = Label("Zapisz")
        self["key_yellow"] = Label("Test okienka")

        self.keyCode = cfg.key.value
        self.keySel = ConfigSelection(choices=[("0", "")])
        self.setKeyChoice()

        self.bouquets = getBouquets() or [("", "(brak bukietów)")]
        cur = cfg.bouquet.value or guessBouquet(self.bouquets)
        if cur not in [b[0] for b in self.bouquets]:
            cur = self.bouquets[0][0]
        self.bouquetSel = ConfigSelection(default=cur, choices=self.bouquets)
        self.serviceSel = None
        self.buildServiceChoice(cfg.service.value)
        self.bouquetSel.addNotifier(self.bouquetChanged, initial_call=False)

        self.list = []
        ConfigListScreen.__init__(self, self.list, session=session)
        self["actions"] = ActionMap(["SetupActions", "ColorActions"], {
            "ok": self.keyOk,
            "cancel": self.keyCancel,
            "red": self.keyCancel,
            "green": self.keySave,
            "save": self.keySave,
            "yellow": self.keyTest,
        }, -2)
        self.buildList()

    def setKeyChoice(self):
        label = "%s   (OK = zmień)" % keyName(self.keyCode)
        self.keySel.setChoices([(str(self.keyCode), label)], default=str(self.keyCode))
        self.keySel.value = str(self.keyCode)

    def buildServiceChoice(self, wanted):
        services = getServices(self.bouquetSel.value) or [("", "(brak kanałów)")]
        refs = [s[0] for s in services]
        default = wanted if wanted in refs else refs[0]
        if self.serviceSel is None:
            self.serviceSel = ConfigSelection(default=default, choices=services)
        else:
            self.serviceSel.setChoices(services, default=default)
            self.serviceSel.value = default

    def bouquetChanged(self, element):
        self.buildServiceChoice(None)
        self.buildList()

    def buildList(self):
        self.list = [
            getConfigListEntry("Przycisk okienka", self.keySel),
            getConfigListEntry("Bukiet z kanałami (np. DVB-T2)", self.bouquetSel),
            getConfigListEntry("Kanał startowy", self.serviceSel),
            getConfigListEntry("Rozmiar okienka", cfg.size),
            getConfigListEntry("Położenie okienka", cfg.corner),
            getConfigListEntry("CH+/CH− zmienia kanał w okienku", cfg.chkeys),
        ]
        self["config"].list = self.list
        av = pipAvailable()
        pipTxt = {True: "Odbiornik zgłasza obsługę PiP.",
                  False: "UWAGA: odbiornik NIE zgłasza obsługi PiP – okienko może nie działać.",
                  None: "Nie udało się sprawdzić obsługi PiP."}[av]
        self["info"].setText("Naciśnij %s w dowolnym momencie, aby pokazać/ukryć okienko.\n%s\n"
                             "Na linii „Przycisk okienka” naciśnij OK, aby przypisać inny przycisk."
                             % (keyName(self.keyCode), pipTxt))

    def keyOk(self):
        cur = self["config"].getCurrent()
        if cur and cur[1] is self.keySel and _controller is not None:
            self.learnBox = self.session.openWithCallback(
                self.learnClosed, MessageBox,
                "Naciśnij przycisk pilota, który ma pokazywać i chować okienko.\n"
                "(OK, EXIT, strzałki, MENU i POWER są zablokowane)",
                MessageBox.TYPE_INFO, timeout=15)
            _controller.learnCallback = self.keyLearned

    def keyLearned(self, code):
        self.keyCode = code
        self.setKeyChoice()
        self.buildList()
        try:
            self.learnBox.close()
        except Exception:
            pass

    def learnClosed(self, *args):
        if _controller is not None:
            _controller.learnCallback = None

    def applyValues(self):
        cfg.key.value = self.keyCode
        cfg.bouquet.value = self.bouquetSel.value
        cfg.service.value = self.serviceSel.value

    def keyTest(self):
        if _controller is None:
            return
        self.applyValues()
        _controller.toggle()

    def keySave(self):
        self.applyValues()
        for c in (cfg.key, cfg.bouquet, cfg.service, cfg.size, cfg.corner, cfg.chkeys):
            c.save()
        configfile.save()
        self.close()

    def keyCancel(self):
        for c in (cfg.key, cfg.bouquet, cfg.service, cfg.size, cfg.corner, cfg.chkeys):
            c.cancel()
        self.close()


# ---------------------------------------------------------------------------
# Rejestracja wtyczki
# ---------------------------------------------------------------------------
def sessionstart(reason, session=None, **kwargs):
    global _controller
    if reason == 0 and session is not None and _controller is None:
        _controller = PipController(session)


def main(session, **kwargs):
    session.open(PipNaziemnaSetup)


def Plugins(**kwargs):
    return [
        PluginDescriptor(where=PluginDescriptor.WHERE_SESSIONSTART, fnc=sessionstart),
        PluginDescriptor(name="PiP Naziemna",
                         description="Okienko z TV naziemną pod wybranym przyciskiem",
                         where=PluginDescriptor.WHERE_PLUGINMENU, fnc=main),
    ]
