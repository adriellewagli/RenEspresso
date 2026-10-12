package com.coffeepos.renespresso.model;

public class CartItem {
    private final int productId;
    private final int variantId;
    private final int stock;
    private final String name;
    private final String size;
    private final double price;
    private int quantity = 1;

    public CartItem(int productId, int variantId, String name, String size, double price, int stock) {
        this.productId = productId;
        this.variantId = variantId;
        this.name = name;
        this.size = size == null ? "" : size;
        this.price = price;
        this.stock = stock;
    }

    public int getProductId() { return productId; }
    public int getVariantId() { return variantId; }
    public int getStock() { return stock; }
    public String getName() { return name; }
    public String getSize() { return size; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public void increment() { quantity++; }
    public void decrement() { if (quantity > 1) quantity--; }
    public double getLineTotal() { return price * quantity; }
}