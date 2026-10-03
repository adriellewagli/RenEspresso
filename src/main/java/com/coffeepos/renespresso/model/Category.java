package com.coffeepos.renespresso.model;

public record Category(int id, String name) {
    @Override public String toString() {
        return name;
    }
}