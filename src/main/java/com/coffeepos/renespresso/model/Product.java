package com.coffeepos.renespresso.model;

public record Product(int id, String name, int categoryId, String category,
                      double price, String description, String imagePath, boolean available) {
    public Product withAvailable(boolean a) {
        return new Product(id, name, categoryId, category, price, description, imagePath, a);
    }
}