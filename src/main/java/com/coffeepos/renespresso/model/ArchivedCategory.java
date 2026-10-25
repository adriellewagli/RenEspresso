package com.coffeepos.renespresso.model;

/**
 * Represents an archived category with the number of dishes associated with it.
 */
public record ArchivedCategory(int id, String name, int dishCount) {
    @Override
    public String toString() {
        return name;
    }
}
