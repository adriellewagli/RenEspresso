package com.coffeepos.renespresso.model;

public record Product(
        int id,
        String name,
        int categoryId,
        String category,
        double price,
        String description,
        String imagePath,
        boolean available,
        int quantity
) {
    public Product withAvailable(boolean next) {
        return new Product(id, name, categoryId, category, price, description, imagePath, next, quantity);
    }
}