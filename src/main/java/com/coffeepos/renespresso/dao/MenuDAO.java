package com.coffeepos.renespresso.dao;

import com.coffeepos.renespresso.config.DatabaseManager;
import com.coffeepos.renespresso.model.ArchivedCategory;
import com.coffeepos.renespresso.model.Category;
import com.coffeepos.renespresso.model.Product;
import com.coffeepos.renespresso.model.ProductVariant;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MenuDAO {

    static {
        ensureSchema();
    }

    public static void ensureSchema() {
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE products ADD COLUMN IF NOT EXISTS is_archived TINYINT(1) NOT NULL DEFAULT 0");
            stmt.execute("ALTER TABLE categories ADD COLUMN IF NOT EXISTS is_archived TINYINT(1) NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            try (Connection conn = DatabaseManager.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                try (ResultSet rs = meta.getColumns(null, null, "products", "is_archived")) {
                    if (!rs.next()) {
                        try (Statement st = conn.createStatement()) {
                            st.execute("ALTER TABLE products ADD COLUMN is_archived TINYINT(1) NOT NULL DEFAULT 0");
                        }
                    }
                }
                try (ResultSet rs = meta.getColumns(null, null, "categories", "is_archived")) {
                    if (!rs.next()) {
                        try (Statement st = conn.createStatement()) {
                            st.execute("ALTER TABLE categories ADD COLUMN is_archived TINYINT(1) NOT NULL DEFAULT 0");
                        }
                    }
                }
            } catch (SQLException ex) {
                System.err.println("MenuDAO ensureSchema note: " + ex.getMessage());
            }
        }
    }

    // =========================================================================
    // CATEGORIES
    // =========================================================================

    public static List<Category> getCategories() throws SQLException {
        String sql = "SELECT category_id, name FROM categories WHERE COALESCE(is_archived, 0) = 0 ORDER BY sort_order, name";
        List<Category> list = new ArrayList<>();
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Category(rs.getInt("category_id"), rs.getString("name")));
            }
        }
        return list;
    }

    public static List<ArchivedCategory> getArchivedCategories() throws SQLException {
        String sql = "SELECT c.category_id, c.name, "
                + "(SELECT COUNT(*) FROM products p WHERE p.category_id = c.category_id) AS dish_count "
                + "FROM categories c "
                + "WHERE COALESCE(c.is_archived, 0) = 1 "
                + "ORDER BY c.name";
        List<ArchivedCategory> list = new ArrayList<>();
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new ArchivedCategory(rs.getInt("category_id"), rs.getString("name"), rs.getInt("dish_count")));
            }
        }
        return list;
    }

    public static int getArchivedCategoriesCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM categories WHERE COALESCE(is_archived, 0) = 1";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public static Category addCategory(String name) throws SQLException {
        String sql = "INSERT INTO categories (name, sort_order, is_archived) "
                + "VALUES (?, (SELECT COALESCE(MAX(sort_order), 0) + 1 FROM (SELECT sort_order FROM categories) t), 0)";
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

    public static void archiveCategory(int id) throws SQLException {
        String sql = "UPDATE categories SET is_archived = 1 WHERE category_id = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public static void restoreCategory(int id) throws SQLException {
        String sql = "UPDATE categories SET is_archived = 0 WHERE category_id = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public static boolean isCategoryArchived(int id) throws SQLException {
        String sql = "SELECT COALESCE(is_archived, 0) FROM categories WHERE category_id = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    public static int getActiveProductCountByCategory(int categoryId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM products WHERE category_id = ? AND COALESCE(is_archived, 0) = 0";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public static int getTotalProductCountByCategory(int categoryId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM products WHERE category_id = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public static void deleteCategory(int id) throws SQLException {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM categories WHERE category_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // =========================================================================
    // PRODUCTS (DISHES)
    // =========================================================================

    public static List<Product> getProducts() throws SQLException {
        String sql = "SELECT p.id, p.name, p.category_id, c.name AS category, "
                + "(SELECT COALESCE(MIN(price), 0) FROM product_variants WHERE product_id = p.id) AS price, "
                + "p.description, p.image_path, p.available, p.quantity "
                + "FROM products p JOIN categories c ON c.category_id = p.category_id "
                + "WHERE COALESCE(p.is_archived, 0) = 0 AND COALESCE(c.is_archived, 0) = 0 "
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

    public static List<Product> getArchivedProducts() throws SQLException {
        String sql = "SELECT p.id, p.name, p.category_id, c.name AS category, "
                + "(SELECT COALESCE(MIN(price), 0) FROM product_variants WHERE product_id = p.id) AS price, "
                + "p.description, p.image_path, p.available, p.quantity "
                + "FROM products p JOIN categories c ON c.category_id = p.category_id "
                + "WHERE COALESCE(p.is_archived, 0) = 1 "
                + "ORDER BY p.name";
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

    public static int getArchivedProductsCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM products WHERE COALESCE(is_archived, 0) = 1";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public static int addProduct(String name, int categoryId, String description,
                                 String imagePath, boolean available, int quantity) throws SQLException {
        String sql = "INSERT INTO products (name, category_id, description, image_path, available, quantity, is_archived) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0)";
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

    public static void archiveProduct(int id) throws SQLException {
        String sql = "UPDATE products SET is_archived = 1, available = 0 WHERE id = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public static void restoreProduct(int id) throws SQLException {
        String sql = "UPDATE products SET is_archived = 0, available = (CASE WHEN quantity > 0 THEN 1 ELSE 0 END) WHERE id = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public static boolean productHasSalesHistory(int productId) throws SQLException {
        String sql = "SELECT 1 FROM transaction_items ti "
                + "JOIN product_variants pv ON pv.variant_id = ti.variant_id "
                + "WHERE pv.product_id = ? LIMIT 1";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static void deleteProduct(int id) throws SQLException {
        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps1 = c.prepareStatement("DELETE FROM product_variants WHERE product_id = ?")) {
                    ps1.setInt(1, id);
                    ps1.executeUpdate();
                }
                try (PreparedStatement ps2 = c.prepareStatement("DELETE FROM products WHERE id = ?")) {
                    ps2.setInt(1, id);
                    ps2.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public static boolean productExistsByName(String name, int excludeId) throws SQLException {
        if (name == null || name.isBlank()) return false;
        String sql = "SELECT 1 FROM products WHERE LOWER(TRIM(name)) = LOWER(TRIM(?)) AND id <> ? AND COALESCE(is_archived, 0) = 0";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            ps.setInt(2, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static Map<Integer, List<ProductVariant>> getAllVariants() throws SQLException {
        String sql = "SELECT variant_id, product_id, size_name, price FROM product_variants ORDER BY price ASC";
        Map<Integer, List<ProductVariant>> map = new HashMap<>();
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int pid = rs.getInt("product_id");
                map.computeIfAbsent(pid, k -> new ArrayList<>()).add(
                        new ProductVariant(
                                rs.getInt("variant_id"), pid,
                                rs.getString("size_name"), rs.getBigDecimal("price")
                        ));
            }
        }
        return map;
    }

}