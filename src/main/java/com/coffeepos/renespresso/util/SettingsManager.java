package com.coffeepos.renespresso.util;

import com.coffeepos.renespresso.dao.SettingsDAO;
import javafx.scene.image.Image;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/** Cached access to the app settings stored in the database. */
public final class SettingsManager {

    // ---- keys
    public static final String STORE_NAME = "store.name";
    public static final String STORE_TAGLINE = "store.tagline";
    public static final String STORE_ADDRESS = "store.address";
    public static final String STORE_CONTACT = "store.contact";
    public static final String STORE_TIN = "store.tin";
    public static final String STORE_LOGO_PATH = "store.logo_path";

    private static final Map<String, String> DEFAULTS = Map.of(
            STORE_NAME, "RenEspresso",
            STORE_TAGLINE, "Coffee Shop"
    );

    private static final Map<String, String> cache = new HashMap<>();
    private static boolean loaded = false;

    private static Image logoImage;
    private static String logoImagePath;

    private SettingsManager() {}

    // ------------------------------------------------------------------ LOAD / SAVE

    /** Re-reads everything from the database. */
    public static synchronized void reload() {
        try {
            SettingsDAO.ensureTable();
            cache.clear();
            cache.putAll(SettingsDAO.loadAll());
        } catch (SQLException e) {
            System.err.println("SettingsManager: could not load settings, using defaults. " + e.getMessage());
        } finally {
            loaded = true;
            logoImagePath = null;
            logoImage = null;
        }
    }

    private static synchronized void ensureLoaded() {
        if (!loaded) reload();
    }

    public static synchronized String get(String key) {
        ensureLoaded();
        if (cache.containsKey(key)) return cache.get(key);
        return DEFAULTS.getOrDefault(key, "");
    }

    /** Writes the values to the database, then updates the in-memory copy. */
    public static synchronized void save(Map<String, String> values) throws SQLException {
        SettingsDAO.ensureTable();
        SettingsDAO.saveAll(values);
        ensureLoaded();
        cache.putAll(values);
        logoImagePath = null;   // force the logo to be re-read
        logoImage = null;
    }

    // ------------------------------------------------------------------ STORE INFORMATION

    public static String getStoreName() {
        String n = get(STORE_NAME);
        return n == null || n.isBlank() ? DEFAULTS.get(STORE_NAME) : n;
    }
    public static String getStoreTagline() { return get(STORE_TAGLINE); }
    public static String getStoreAddress() { return get(STORE_ADDRESS); }
    public static String getStoreContact() { return get(STORE_CONTACT); }
    public static String getStoreTin()     { return get(STORE_TIN); }

    /** The store logo, or null if none was uploaded. */
    public static synchronized Image getLogoImage() {
        String path = get(STORE_LOGO_PATH);
        if (path == null || path.isBlank()) return null;
        if (!path.equals(logoImagePath)) {
            File f = new File(path);
            logoImage = f.isFile() ? new Image(f.toURI().toString(), 256, 256, true, true) : null;
            logoImagePath = path;
        }
        return logoImage;
    }

    /**
     * Copies the chosen image into the app folder (user home / RenEspresso) and returns the new path.
     * The logo is kept as a file, not inside the database, so the Excel backup does not break.
     */
    public static File storeLogoFile(File source) throws IOException {
        Path dir = Paths.get(System.getProperty("user.home"), "RenEspresso");
        Files.createDirectories(dir);

        // remove any old logo (it may have a different extension)
        File[] old = dir.toFile().listFiles((d, name) -> name.startsWith("store-logo."));
        if (old != null) for (File f : old) Files.deleteIfExists(f.toPath());

        String name = source.getName().toLowerCase();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : ".png";
        Path target = dir.resolve("store-logo" + ext);
        Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        return target.toFile();
    }

    public static void deleteLogoFiles() {
        Path dir = Paths.get(System.getProperty("user.home"), "RenEspresso");
        File[] old = dir.toFile().listFiles((d, name) -> name.startsWith("store-logo."));
        if (old != null) for (File f : old) f.delete();
    }
}