package com.coffeepos.renespresso.util;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class ImageStore {

    private static final Path DIR = Paths.get(System.getProperty("user.home"), ".renespresso", "menu-images");

    private ImageStore() {}

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

    public static ImageView circleView(String fileName, double size) {
        if (fileName == null || fileName.isBlank()) return null;
        File f = DIR.resolve(fileName).toFile();
        return f.exists() ? circleView(f, size) : null;
    }

    public static ImageView circleView(File file, double size) {
        Image img = new Image(file.toURI().toString(), size * 3, size * 3, true, true, true);
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
        return iv;
    }

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