package com.coffeepos.renespresso.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.sql.Timestamp;

public class User {

    private final IntegerProperty userId;
    private final StringProperty username;
    private final StringProperty password;
    private final StringProperty fullName;
    private final StringProperty role;
    private final BooleanProperty isActive;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Default Constructor
    public User() {
        this.userId = new SimpleIntegerProperty(0);
        this.username = new SimpleStringProperty("");
        this.password = new SimpleStringProperty("");
        this.fullName = new SimpleStringProperty("");
        this.role = new SimpleStringProperty("CASHIER");
        this.isActive = new SimpleBooleanProperty(true);
    }

    // AUTH Constructor
    public User(int userId, String username, String fullName, String role, boolean isActive) {
        this.userId = new SimpleIntegerProperty(userId);
        this.username = new SimpleStringProperty(username);
        this.password = new SimpleStringProperty("");
        this.fullName = new SimpleStringProperty(fullName);
        this.role = new SimpleStringProperty(role);
        this.isActive = new SimpleBooleanProperty(isActive);
    }

    // Full Constructor
    public User(int userId, String username, String password, String fullName, String role, boolean isActive, Timestamp createdAt, Timestamp updatedAt) {
        this.userId = new SimpleIntegerProperty(userId);
        this.username = new SimpleStringProperty(username);
        this.password = new SimpleStringProperty(password);
        this.fullName = new SimpleStringProperty(fullName);
        this.role = new SimpleStringProperty(role);
        this.isActive = new SimpleBooleanProperty(isActive);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // --- Getters, Setters, & JavaFX Properties ---

    public int getUserId() { return userId.get(); }
    public void setUserId(int userId) { this.userId.set(userId); }
    public IntegerProperty userIdProperty() { return userId; }

    public String getUsername() { return username.get(); }
    public void setUsername(String username) { this.username.set(username); }
    public StringProperty usernameProperty() { return username; }

    public String getPassword() { return password.get(); }
    public void setPassword(String password) { this.password.set(password); }
    public StringProperty passwordProperty() { return password; }

    public String getFullName() { return fullName.get(); }
    public void setFullName(String fullName) { this.fullName.set(fullName); }
    public StringProperty fullNameProperty() { return fullName; }

    public String getRole() { return role.get(); }
    public void setRole(String role) { this.role.set(role); }
    public StringProperty roleProperty() { return role; }

    public boolean isActive() { return isActive.get(); }
    public void setIsActive(boolean isActive) { this.isActive.set(isActive); }
    public BooleanProperty isActiveProperty() { return isActive; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return fullName.get() + " (" + role.get() + ")";
    }
}