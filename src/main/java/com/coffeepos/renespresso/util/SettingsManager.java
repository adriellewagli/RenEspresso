package com.coffeepos.renespresso.util;

import com.coffeepos.renespresso.dao.SettingsDAO;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

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

    /** Width of a printed receipt in characters (the dashed line on your receipts is 42). */
    public static final int RECEIPT_WIDTH = 42;

    private static final Map<String, String> DEFAULTS = Map.of(
            STORE_NAME, "RenEspresso",
            STORE_TAGLINE, "Coffee Shop"
    );

    private static final Map<String, String> cache = new HashMap<>();
    private static boolean loaded = false;

    // Bind labels to these so they update right after "Save Changes"
    private static final ReadOnlyStringWrapper storeNameProp =
            new ReadOnlyStringWrapper(DEFAULTS.get(STORE_NAME));
    private static final ReadOnlyStringWrapper storeTaglineProp =
            new ReadOnlyStringWrapper(DEFAULTS.get(STORE_TAGLINE));

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
            publish();
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
        publish();
    }

    /** Pushes the current name and tagline into the bindable properties (on the FX thread). */
    private static void publish() {
        String name = getStoreName();
        String tagline = getStoreTagline();
        Runnable r = () -> {
            storeNameProp.set(name);
            storeTaglineProp.set(tagline);
        };
        if (Platform.isFxApplicationThread()) r.run();
        else Platform.runLater(r);
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

    public static ReadOnlyStringProperty storeNameProperty()    { return storeNameProp.getReadOnlyProperty(); }
    public static ReadOnlyStringProperty storeTaglineProperty() { return storeTaglineProp.getReadOnlyProperty(); }

    // ------------------------------------------------------------------ RECEIPT HEADER

    /** Centered receipt header: name, tagline, address, contact, TIN. Blank fields are skipped. */
    public static String receiptHeader() {
        return receiptHeader(RECEIPT_WIDTH);
    }

    public static String receiptHeader(int width) {
        StringBuilder sb = new StringBuilder();
        appendCentered(sb, getStoreName(), width);
        appendCentered(sb, getStoreTagline(), width);

        String address = getStoreAddress();
        if (address != null && !address.isBlank()) {
            for (String line : wrap(address.trim(), width)) appendCentered(sb, line, width);
        }
        String contact = getStoreContact();
        if (contact != null && !contact.isBlank()) appendCentered(sb, "Tel: " + contact.trim(), width);
        String tin = getStoreTin();
        if (tin != null && !tin.isBlank()) appendCentered(sb, "TIN: " + tin.trim(), width);
        return sb.toString();
    }

    private static void appendCentered(StringBuilder sb, String text, int width) {
        if (text == null || text.isBlank()) return;
        text = text.trim();
        int pad = Math.max(0, (width - text.length()) / 2);
        sb.append(" ".repeat(pad)).append(text).append('\n');
    }

    private static java.util.List<String> wrap(String text, int width) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (cur.length() > 0 && cur.length() + 1 + word.length() > width) {
                lines.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(word);
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }
}