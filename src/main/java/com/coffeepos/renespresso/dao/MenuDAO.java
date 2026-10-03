package com.coffeepos.renespresso.dao;

import com.coffeepos.renespresso.config.DatabaseManager;
import com.coffeepos.renespresso.model.Category;
import com.coffeepos.renespresso.model.Product;

import java.math.BigDecimal;
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
        String sql = "SELECT p.product_id, p.name, p.category_id, c.name AS category, p.price, "
                + "p.description, p.image_path, p.is_available "
                + "FROM products p JOIN categories c ON c.category_id = p.category_id "
                + "ORDER BY c.sort_order, p.name";
        List<Product> list = new ArrayList<>();
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Product(
                        rs.getInt("product_id"),
                        rs.getString("name"),
                        rs.getInt("category_id"),
                        rs.getString("category"),
                        rs.getDouble("price"),
                        rs.getString("description"),
                        rs.getString("image_path"),
                        rs.getBoolean("is_available")));
            }
        }
        return list;
    }

    public static void addProduct(String name, int categoryId, BigDecimal price, String description,
                                  String imagePath, boolean available) throws SQLException {
        String sql = "INSERT INTO products (name, category_id, price, description, image_path, is_available) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, categoryId);
            ps.setBigDecimal(3, price);
            ps.setString(4, description);
            ps.setString(5, imagePath);
            ps.setInt(6, available ? 1 : 0);
            ps.executeUpdate();
        }
    }

    public static void updateProduct(int id, String name, int categoryId, BigDecimal price, String description,
                                     String imagePath, boolean available) throws SQLException {
        String sql = "UPDATE products SET name = ?, category_id = ?, price = ?, description = ?, "
                + "image_path = ?, is_available = ? WHERE product_id = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, categoryId);
            ps.setBigDecimal(3, price);
            ps.setString(4, description);
            ps.setString(5, imagePath);
            ps.setInt(6, available ? 1 : 0);
            ps.setInt(7, id);
            ps.executeUpdate();
        }
    }
    public static void setAvailable(int id, boolean available) throws SQLException {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE products SET is_available = ? WHERE product_id = ?")) {
            ps.setInt(1, available ? 1 : 0);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public static void deleteProduct(int id) throws SQLException {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM products WHERE product_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}