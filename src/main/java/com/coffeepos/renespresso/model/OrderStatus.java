package com.coffeepos.renespresso.model;

public enum OrderStatus {
    PREPARING("Preparing", "status-preparing"),
    PENDING("Pending", "status-pending"),
    COMPLETED("Completed", "status-completed"),
    CANCELLED("Cancelled", "status-cancelled");

    private final String label;
    private final String styleClass;

    OrderStatus(String label, String styleClass) {
        this.label = label;
        this.styleClass = styleClass;
    }

    public String label() { return label; }
    public String styleClass() { return styleClass; }
}