package com.coffeepos.renespresso.dao;

import com.coffeepos.renespresso.config.DatabaseManager;
import com.coffeepos.renespresso.model.Category;
import com.coffeepos.renespresso.model.Product;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class MenuDAO {

    public static List<Category> getCategories() throws SQLException {
        String sql = "SELECT category_id, name FROM categories ORDER BY sort_order, name";
        List<Category> list = new ArrayList<>();
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(new Category(rs.getInt("category_id"), rs.getString("name")));
        }
        return list;
    }

    public static Category addCategory(String name) throws SQLException {
        String sql = "INSERT INTO categories (name, sort_order) "
                + "VALUES (?, (SELECT COALESCE(MAX(sort_order), 0) + 1 FROM (SELECT sort_order FROM categories) t))";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Category(keys.getInt(1), name);
            }
        }
    }

    public static List<Product> getProducts() throws SQLException {
        String sql = "SELECT p.id, p.name, p.category_id, c.name AS category, "
                + "(SELECT COALESCE(MIN(price), 0) FROM product_variants WHERE product_id = p.id) AS price, "
                + "p.description, p.image_path, p.available, p.quantity " // <-- added p.quantity
                + "FROM products p JOIN categories c ON c.category_id = p.category_id "
                + "ORDER BY c.sort_order, p.name";
        List<Product> list = new ArrayList<>();
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Product(
                        rs.getInt("id"), rs.getString("name"), rs.getInt("category_id"),
                        rs.getString("category"), rs.getDouble("price"), rs.getString("description"),
                        rs.getString("image_path"), rs.getBoolean("available"), rs.getInt("quantity")
                ));
            }
        }
        return list;
    }

    public static int addProduct(String name, int categoryId, String description,
                                 String imagePath, boolean available, int quantity) throws SQLException {
        String sql = "INSERT INTO products (name, category_id, description, image_path, available, quantity) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setInt(2, categoryId);
            ps.setString(3, description);
            ps.setString(4, imagePath);
            ps.setInt(5, available ? 1 : 0);
            ps.setInt(6, quantity);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public static void updateProduct(int id, String name, int categoryId, String description,
                                     String imagePath, boolean available, int quantity) throws SQLException {
        String sql = "UPDATE products SET name = ?, category_id = ?, description = ?, "
                + "image_path = ?, available = ?, quantity = ? WHERE id = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, categoryId);
            ps.setString(3, description);
            ps.setString(4, imagePath);
            ps.setInt(5, available ? 1 : 0);
            ps.setInt(6, quantity);
            ps.setInt(7, id);
            ps.executeUpdate();
        }
    }

    public static void setAvailable(int id, boolean available) throws SQLException {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE products SET available = ? WHERE id = ?")) {
            ps.setInt(1, available ? 1 : 0);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public static void deleteProduct(int id) throws SQLException {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM products WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public static void deleteCategory(int id) throws SQLException {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM categories WHERE category_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public static java.util.Map<Integer, java.util.List<com.coffeepos.renespresso.model.ProductVariant>> getAllVariants() throws SQLException {
        String sql = "SELECT variant_id, product_id, size_name, price FROM product_variants ORDER BY price ASC";
        java.util.Map<Integer, java.util.List<com.coffeepos.renespresso.model.ProductVariant>> map = new java.util.HashMap<>();
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int pid = rs.getInt("product_id");
                map.computeIfAbsent(pid, k -> new java.util.ArrayList<>()).add(
                        new com.coffeepos.renespresso.model.ProductVariant(
                                rs.getInt("variant_id"), pid,
                                rs.getString("size_name"), rs.getBigDecimal("price")
                        ));
            }
        }
        return map;
    }

}