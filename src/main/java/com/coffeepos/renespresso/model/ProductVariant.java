package com.coffeepos.renespresso.model;

import java.math.BigDecimal;

public class ProductVariant {
    private int variantId;
    private int productId;
    private String sizeName;
    private BigDecimal price;

    public ProductVariant(int variantId, int productId, String sizeName, BigDecimal price) {
        this.variantId = variantId;
        this.productId = productId;
        this.sizeName = sizeName;
        this.price = price;
    }

    public int getVariantId() { return variantId; }
    public void setVariantId(int variantId) { this.variantId = variantId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getSizeName() { return sizeName; }
    public void setSizeName(String sizeName) { this.sizeName = sizeName; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    @Override
    public String toString() {
        return sizeName + " - ₱" + price;
    }
}