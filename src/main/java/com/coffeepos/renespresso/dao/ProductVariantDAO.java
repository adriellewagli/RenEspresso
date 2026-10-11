package com.coffeepos.renespresso.dao;

import com.coffeepos.renespresso.config.DatabaseManager;
import com.coffeepos.renespresso.model.ProductVariant;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProductVariantDAO {

    public static List<ProductVariant> getVariantsByProductId(int productId) throws SQLException {
        String sql = "SELECT variant_id, product_id, size_name, price " +
                "FROM product_variants WHERE product_id = ? ORDER BY price ASC";

        List<ProductVariant> list = new ArrayList<>();
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ProductVariant(
                            rs.getInt("variant_id"),
                            rs.getInt("product_id"),
                            rs.getString("size_name"),
                            rs.getBigDecimal("price")
                    ));
                }
            }
        }
        return list;
    }

    public static void addVariant(int productId, String sizeName, BigDecimal price) throws SQLException {
        String sql = "INSERT INTO product_variants (product_id, size_name, price) VALUES (?, ?, ?)";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setString(2, sizeName);
            ps.setBigDecimal(3, price);
            ps.executeUpdate();
        }
    }

    public static void updateVariant(int variantId, String sizeName, BigDecimal price) throws SQLException {
        String sql = "UPDATE product_variants SET size_name = ?, price = ? WHERE variant_id = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sizeName);
            ps.setBigDecimal(2, price);
            ps.setInt(3, variantId);
            ps.executeUpdate();
        }
    }

    public static void deleteVariant(int variantId) throws SQLException {
        String sql = "DELETE FROM product_variants WHERE variant_id = ?";
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, variantId);
            ps.executeUpdate();
        }
    }
}