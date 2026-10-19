package com.coffeepos.renespresso.util;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class ImageStore {

    /** Local folder for images uploaded through the admin screen. */
    private static final Path DIR = Paths.get(System.getProperty("user.home"), ".renespresso", "menu-images");

    /** Apache (XAMPP htdocs) folder that holds the server-side images. Change only here if the server moves. */
    private static final String IMAGE_BASE = "http://localhost/Renesspresso/";

    private ImageStore() {}

    // =====================================================================
    //  LOCAL UPLOADS (admin screen)
    // =====================================================================
    public static String save(File src) throws IOException {
        Files.createDirectories(DIR);
        String n = src.getName();
        String ext = n.contains(".") ? n.substring(n.lastIndexOf('.')).toLowerCase() : ".png";
        String fileName = UUID.randomUUID() + ext;
        Files.copy(src.toPath(), DIR.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        return fileName;
    }

    public static void delete(String fileName) {
        if (fileName == null || fileName.isBlank()) return;
        if (!Paths.get(fileName).getFileName().toString().equals(fileName)) return;  // file names only
        try { Files.deleteIfExists(DIR.resolve(fileName)); } catch (IOException ignored) {}
    }

    // =====================================================================
    //  IMAGE VIEWS
    // =====================================================================

    /**
     * Accepts any of:
     *  - a full URL            "http://localhost/Renesspresso/Shake/X.png"
     *  - a server path         "Shake/X.png"  (prefixed with IMAGE_BASE)
     *  - a local file name     "3f2a...png"   (from ~/.renespresso/menu-images)
     */
    public static ImageView circleView(String path, double size) {
        if (path == null || path.isBlank()) return null;
        path = path.trim();

        // 1. Full URL already stored
        if (path.startsWith("http://") || path.startsWith("https://"))
            return circleViewFromUrl(path, size);

        // 2. Absolute local path (e.g. C:\pics\x.png)
        File asFile = new File(path);
        if (asFile.isAbsolute())
            return asFile.exists() ? circleView(asFile, size) : null;

        // 3. "Folder/File.png" -> served by Apache in htdocs
        if (path.contains("/") || path.contains("\\"))
            return circleViewFromUrl(serverUrl(path), size);

        // 4. Plain file name -> locally uploaded image (old behavior)
        File f = DIR.resolve(path).toFile();
        return f.exists() ? circleView(f, size) : null;
    }

    public static ImageView circleView(File file, double size) {
        return buildCircle(new Image(file.toURI().toString(), size * 3, size * 3, true, true, true), size);
    }

    private static ImageView circleViewFromUrl(String url, double size) {
        // last argument = true -> loads in the background so the menu never freezes
        return buildCircle(new Image(url, size * 3, size * 3, true, true, true), size);
    }

    private static ImageView buildCircle(Image img, double size) {
        ImageView iv = new ImageView(img);
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        iv.setClip(new Circle(size / 2, size / 2, size / 2));

        Runnable crop = () -> {
            double w = img.getWidth(), h = img.getHeight(), s = Math.min(w, h);
            if (s > 0) iv.setViewport(new Rectangle2D((w - s) / 2, (h - s) / 2, s, s));
        };
        if (img.getProgress() >= 1.0) crop.run();
        else img.progressProperty().addListener((o, a, b) -> { if (b.doubleValue() >= 1.0) crop.run(); });

        img.errorProperty().addListener((o, wasError, isError) -> {
            if (isError) {
                System.err.println("Image failed to load: " + img.getUrl()
                        + (img.getException() != null ? "  (" + img.getException().getMessage() + ")" : ""));
            }
        });
        return iv;
    }

    /** "Shake/Cookies&Cream.png" -> "http://localhost/Renesspresso/Shake/Cookies%26Cream.png" */
    private static String serverUrl(String path) {
        String[] parts = path.replace('\\', '/').split("/");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = URLEncoder.encode(parts[i], StandardCharsets.UTF_8).replace("+", "%20");
        }
        return IMAGE_BASE + String.join("/", parts);
    }

    // =====================================================================
    //  HELPERS
    // =====================================================================
    public static String pathOf(String fileName) {
        return (fileName == null || fileName.isBlank()) ? "" : DIR.resolve(fileName).toString();
    }

    public static boolean isStored(String path) {
        return Paths.get(path).toAbsolutePath().normalize().startsWith(DIR.toAbsolutePath().normalize());
    }

    public static String fileNameOf(String path) {
        return Paths.get(path).getFileName().toString();
    }

    public static boolean isSupported(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".gif") || n.endsWith(".bmp");
    }
}