package com.coffeepos.renespresso.util;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import com.coffeepos.renespresso.config.DatabaseManager;

public final class ImageStore {

    /** Local folder for images uploaded through the admin screen. */
    private static final Path DIR = Paths.get(System.getProperty("user.home"), ".renespresso", "menu-images");

    /** Apache (XAMPP htdocs) folder that holds the server-side images. Derived from configured server host. */
    public static String getBaseUrl() {
        String host = DatabaseManager.getHost();
        if (host == null || host.isBlank()) host = "localhost";
        return "http://" + host + "/Renesspresso/";
    }

    public static String getApiUrl() {
        return getBaseUrl() + "api.php";
    }

    private ImageStore() {}

    // =====================================================================
    //  IMAGE UPLOADS & REMOTE SERVER INTEGRATION
    // =====================================================================

    public static String save(File src) throws IOException {
        return save(src, null, null, null);
    }

    public static String save(File src, String categoryName, String dishName, String oldPath) throws IOException {
        // 1. Attempt upload to Apache server via PHP API
        try {
            String serverPath = uploadToServer(src, categoryName, dishName, oldPath);
            if (serverPath != null && !serverPath.isBlank()) {
                return serverPath;
            }
        } catch (Exception e) {
            System.err.println("ImageStore: Remote server upload note (falling back to local): " + e.getMessage());
        }

        // 2. Fallback: Save to local user folder
        Files.createDirectories(DIR);
        String n = src.getName();
        String ext = n.contains(".") ? n.substring(n.lastIndexOf('.')).toLowerCase() : ".png";
        String fileName = UUID.randomUUID() + ext;
        Files.copy(src.toPath(), DIR.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        return fileName;
    }

    public static String uploadToServer(File src, String categoryName, String dishName, String oldPath) throws IOException {
        String boundary = "===" + System.currentTimeMillis() + "===";
        String lineEnd = "\r\n";
        String twoHyphens = "--";

        URL url = URI.create(getApiUrl() + "?action=upload").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(12000);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Connection", "Keep-Alive");
        conn.setRequestProperty("User-Agent", "RenEspresso-POS");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream os = conn.getOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true)) {

            // Form param: category
            if (categoryName != null && !categoryName.isBlank()) {
                writer.append(twoHyphens).append(boundary).append(lineEnd);
                writer.append("Content-Disposition: form-data; name=\"category\"").append(lineEnd);
                writer.append("Content-Type: text/plain; charset=UTF-8").append(lineEnd);
                writer.append(lineEnd).append(categoryName).append(lineEnd);
            }

            // Form param: custom_name
            if (dishName != null && !dishName.isBlank()) {
                writer.append(twoHyphens).append(boundary).append(lineEnd);
                writer.append("Content-Disposition: form-data; name=\"custom_name\"").append(lineEnd);
                writer.append("Content-Type: text/plain; charset=UTF-8").append(lineEnd);
                writer.append(lineEnd).append(dishName).append(lineEnd);
            }

            // Form param: old_path
            if (oldPath != null && !oldPath.isBlank()) {
                writer.append(twoHyphens).append(boundary).append(lineEnd);
                writer.append("Content-Disposition: form-data; name=\"old_path\"").append(lineEnd);
                writer.append("Content-Type: text/plain; charset=UTF-8").append(lineEnd);
                writer.append(lineEnd).append(oldPath).append(lineEnd);
            }

            // File field
            String fileName = src.getName();
            writer.append(twoHyphens).append(boundary).append(lineEnd);
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(fileName).append("\"").append(lineEnd);
            writer.append("Content-Type: application/octet-stream").append(lineEnd);
            writer.append(lineEnd);
            writer.flush();

            // Write file binary
            Files.copy(src.toPath(), os);
            os.flush();

            writer.append(lineEnd);
            writer.append(twoHyphens).append(boundary).append(twoHyphens).append(lineEnd);
            writer.flush();
        }

        int status = conn.getResponseCode();
        if (status >= 200 && status < 300) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                String respStr = response.toString();
                String path = extractJsonField(respStr, "path");
                if (path != null && !path.isBlank()) {
                    return path;
                }
            }
        }
        return null;
    }

    public static void delete(String path) {
        if (path == null || path.isBlank()) return;

        // 1. Remote server deletion if path has directory separator
        if (path.contains("/") || path.contains("\\")) {
            new Thread(() -> {
                try {
                    String clean = path.replace('\\', '/');
                    URL u = URI.create(getApiUrl() + "?action=delete&path=" + URLEncoder.encode(clean, StandardCharsets.UTF_8)).toURL();
                    HttpURLConnection c = (HttpURLConnection) u.openConnection();
                    c.setConnectTimeout(4000);
                    c.setRequestMethod("GET");
                    c.getResponseCode();
                } catch (Exception ignored) {}
            }, "image-delete-thread").start();
        }

        // 2. Local file deletion fallback
        try {
            Path p = DIR.resolve(path);
            if (p.toAbsolutePath().normalize().startsWith(DIR.toAbsolutePath().normalize())) {
                Files.deleteIfExists(p);
            }
        } catch (IOException ignored) {}
    }

    public static boolean createCategoryFolder(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) return false;
        try {
            URL u = URI.create(getApiUrl() + "?action=create_category_folder&category=" + URLEncoder.encode(categoryName, StandardCharsets.UTF_8)).toURL();
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(4000);
            c.setRequestMethod("GET");
            return c.getResponseCode() == 200;
        } catch (Exception e) {
            System.err.println("ImageStore: createCategoryFolder note: " + e.getMessage());
            return false;
        }
    }

    public static boolean deleteCategoryFolder(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) return false;
        try {
            URL u = URI.create(getApiUrl() + "?action=delete_category_folder&category=" + URLEncoder.encode(categoryName, StandardCharsets.UTF_8)).toURL();
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(4000);
            c.setRequestMethod("GET");
            return c.getResponseCode() == 200;
        } catch (Exception e) {
            System.err.println("ImageStore: deleteCategoryFolder note: " + e.getMessage());
            return false;
        }
    }

    private static String extractJsonField(String json, String key) {
        if (json == null) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        java.util.regex.Matcher m = p.matcher(json);
        return m.find() ? m.group(1).replace("\\/", "/") : null;
    }

    // =====================================================================
    //  IMAGE VIEWS & LOADING EFFECTS
    // =====================================================================

    /**
     * Resolves a relative or full image path into an HTTP server URL if applicable.
     */
    public static String resolveImageUrl(String path) {
        if (path == null || path.isBlank()) return null;
        path = path.trim();
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        if (path.contains("/") || path.contains("\\")) return serverUrl(path);
        return null;
    }

    /**
     * Resolves a local image file if present.
     */
    public static File resolveLocalFile(String path) {
        if (path == null || path.isBlank()) return null;
        path = path.trim();
        File asFile = new File(path);
        if (asFile.isAbsolute() && asFile.exists()) return asFile;
        File f = DIR.resolve(path).toFile();
        return f.exists() ? f : null;
    }

    /**
     * Builds a full circular photo card containing:
     * 1. A warm coffee-cream placeholder layer (with stylized coffee cup emblem / initial)
     * 2. An animated loading indicator ring & pulse while the image is loading over network
     * 3. An ImageView that smoothly fades in on load completion
     * 4. Automatic error / disconnection fallback: if the server disconnects or fails,
     *    the loading indicator immediately hides and the card gracefully stays in normal placeholder state.
     */
    public static StackPane circlePhotoNode(String path, String dishName, double size) {
        StackPane root = new StackPane();
        root.setMinSize(size, size);
        root.setPrefSize(size, size);
        root.setMaxSize(size, size);
        root.getStyleClass().add("photo-container");

        double radius = size / 2.0;

        // --- LAYER 1: Background & Clip ---
        Circle bg = new Circle(radius);
        bg.setFill(Color.web("#FAF2E8"));
        bg.setStroke(Color.web("#E6D3BE"));
        bg.setStrokeWidth(Math.max(1.0, size * 0.02));

        Circle clip = new Circle(radius, radius, radius);
        root.setClip(clip);

        // --- LAYER 2: Fallback / Placeholder Layer ---
        Node placeholderNode = createPlaceholder(dishName, size);
        root.getChildren().addAll(bg, placeholderNode);

        if (path == null || path.isBlank()) {
            return root; // No image specified, display clean placeholder
        }

        // --- LAYER 3: Animated Loading Ring & Soft Pulse ---
        double arcRadius = Math.max(8.0, radius - (size * 0.08));
        Arc loadingArc = new Arc(0, 0, arcRadius, arcRadius, 0, 90);
        loadingArc.setFill(null);
        loadingArc.setStroke(Color.web("#C08552"));
        loadingArc.setStrokeWidth(Math.max(2.0, size * 0.045));
        loadingArc.setStrokeLineCap(StrokeLineCap.ROUND);

        RotateTransition rotate = new RotateTransition(Duration.millis(900), loadingArc);
        rotate.setByAngle(360);
        rotate.setCycleCount(Animation.INDEFINITE);
        rotate.setInterpolator(Interpolator.LINEAR);

        FadeTransition pulse = new FadeTransition(Duration.millis(600), placeholderNode);
        pulse.setFromValue(0.45);
        pulse.setToValue(0.95);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);

        root.getChildren().add(loadingArc);
        rotate.play();
        pulse.play();

        // --- LAYER 4: Image View ---
        ImageView iv = new ImageView();
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        iv.setOpacity(0.0);
        root.getChildren().add(iv);

        String resolvedUrl = resolveImageUrl(path);
        File localFile = (resolvedUrl == null) ? resolveLocalFile(path) : null;

        Runnable startLoad = () -> {
            Image img;
            if (resolvedUrl != null) {
                img = new Image(resolvedUrl, size * 3, size * 3, true, true, true);
            } else if (localFile != null && localFile.exists()) {
                img = new Image(localFile.toURI().toString(), size * 3, size * 3, true, true, true);
            } else {
                rotate.stop();
                pulse.stop();
                loadingArc.setVisible(false);
                placeholderNode.setOpacity(1.0);
                return;
            }

            iv.setImage(img);

            Runnable crop = () -> {
                double w = img.getWidth(), h = img.getHeight(), s = Math.min(w, h);
                if (s > 0) iv.setViewport(new Rectangle2D((w - s) / 2, (h - s) / 2, s, s));
            };

            Runnable onLoaded = () -> {
                crop.run();
                rotate.stop();
                pulse.stop();
                loadingArc.setVisible(false);
                placeholderNode.setOpacity(1.0);

                FadeTransition fadeIn = new FadeTransition(Duration.millis(250), iv);
                fadeIn.setFromValue(iv.getOpacity());
                fadeIn.setToValue(1.0);
                fadeIn.play();
            };

            Runnable onError = () -> {
                rotate.stop();
                pulse.stop();
                loadingArc.setVisible(false);
                placeholderNode.setOpacity(1.0);
                iv.setOpacity(0.0); // Gracefully stays at clean normal placeholder
            };

            if (img.getProgress() >= 1.0) {
                if (img.isError()) onError.run();
                else onLoaded.run();
            } else {
                img.progressProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal.doubleValue() >= 1.0) {
                        if (img.isError()) onError.run();
                        else onLoaded.run();
                    }
                });
            }

            img.errorProperty().addListener((obs, oldVal, isErr) -> {
                if (isErr) onError.run();
            });
        };

        startLoad.run();

        // Attach reload action for server reconnection auto-recovery
        root.getProperties().put("reloadAction", (Runnable) () -> {
            iv.setOpacity(0.0);
            loadingArc.setVisible(true);
            rotate.play();
            pulse.play();
            startLoad.run();
        });

        return root;
    }

    /**
     * Creates a placeholder emblem (warm coffee cup or dish initial).
     */
    private static Node createPlaceholder(String dishName, double size) {
        StackPane p = new StackPane();
        p.setMinSize(size, size);
        p.setMaxSize(size, size);

        if (size >= 48) {
            SVGPath cup = new SVGPath();
            cup.setContent("M20 3H4v10c0 2.21 1.79 4 4 4h6c2.21 0 4-1.79 4-4v-3h2c1.11 0 2-.9 2-2V5c0-1.11-.89-2-2-2zm0 5h-2V5h2v3zM4 19h16v2H4z");
            cup.setFill(Color.web("#C08552"));
            double scale = (size * 0.36) / 24.0;
            cup.setScaleX(scale);
            cup.setScaleY(scale);
            p.getChildren().add(cup);
        } else {
            String initial = (dishName != null && !dishName.isBlank()) ? dishName.substring(0, 1).toUpperCase() : "☕";
            Label lbl = new Label(initial);
            lbl.setStyle("-fx-text-fill: #895737; -fx-font-weight: bold; -fx-font-size: " + Math.max(11, (int)(size * 0.44)) + "px;");
            p.getChildren().add(lbl);
        }
        return p;
    }

    /**
     * Triggers a reload on a photo card when the server reconnects.
     */
    public static void reloadPhotoNode(StackPane photoNode) {
        if (photoNode == null) return;
        Object action = photoNode.getProperties().get("reloadAction");
        if (action instanceof Runnable r) {
            r.run();
        }
    }

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
        return getBaseUrl() + String.join("/", parts);
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