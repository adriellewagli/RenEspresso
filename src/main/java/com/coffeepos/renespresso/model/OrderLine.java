package com.coffeepos.renespresso.model;

public record OrderLine(String name, String size, int qty, double lineTotal) {

    public String label() {
        return size == null || size.isBlank() ? name : name + " (" + size + ")";
    }
}
