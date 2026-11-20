package pl.kamil.tvpodglad;

/** Ustawienia aplikacji - zmień tutaj i przebuduj APK. */
final class Config {
    private Config() {}

    /** Port serwera HTTP na TV. */
    static final int PORT = 8080;

    /** Maksymalna szerokość przechwytywanego obrazu (wysokość liczona z proporcji). */
    static final int MAX_WIDTH = 1280;

    /** Maksymalna liczba klatek na sekundę dla podglądu na żywo. */
    static final int FPS = 4;

    /** Jakość JPEG 1-100. */
    static final int JPEG_QUALITY = 70;

    /** Automatyczny start po włączeniu TV. */
    static final boolean AUTOSTART = true;

    /**
     * Opcjonalny klucz dostępu. Pusty = brak zabezpieczenia (każdy w sieci LAN widzi ekran).
     * Jeśli ustawisz np. "tajne123", adresy wymagają dopisania ?key=tajne123
     */
    static final String ACCESS_KEY = "";
}
