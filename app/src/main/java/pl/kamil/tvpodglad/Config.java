package pl.kamil.tvpodglad;

/** Stałe ustawienia - zmień tutaj i przebuduj APK. */
final class Config {
    private Config() {}

    static final String TAG = "TvPodglad";

    /** Port serwera HTTP do sterowania okienkiem (Home Assistant). 0 = serwer wyłączony. */
    static final int PORT = 8080;

    /** Opcjonalny klucz: jeśli ustawisz np. "tajne123", adresy wymagają ?key=tajne123 */
    static final String ACCESS_KEY = "";
}
