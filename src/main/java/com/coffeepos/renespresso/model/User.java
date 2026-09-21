package com.coffeepos.renespresso.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.sql.Timestamp;

public class User {

    private final IntegerProperty userId;
    private final StringProperty username;
    private final StringProperty passwordHash;
    private final StringProperty fullName;
    private final StringProperty role;
    private final StringProperty status;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    //SA USER
    public User() {
        this.userId = new SimpleIntegerProperty(0);
        this.username = new SimpleStringProperty("");
        this.passwordHash = new SimpleStringProperty("");
        this.fullName = new SimpleStringProperty("");
        this.role = new SimpleStringProperty("");
        this.status = new SimpleStringProperty("active");
    }

    //AUTH
    public User(int userId, String username, String fullName, String role, String status) {
        this.userId = new SimpleIntegerProperty(userId);
        this.username = new SimpleStringProperty(username);
        this.passwordHash = new SimpleStringProperty("");
        this.fullName = new SimpleStringProperty(fullName);
        this.role = new SimpleStringProperty(role);
        this.status = new SimpleStringProperty(status);
    }

    // Full Constructor
    public User(int userId, String username, String passwordHash, String fullName, String role, String status, Timestamp createdAt, Timestamp updatedAt) {
        this.userId = new SimpleIntegerProperty(userId);
        this.username = new SimpleStringProperty(username);
        this.passwordHash = new SimpleStringProperty(passwordHash);
        this.fullName = new SimpleStringProperty(fullName);
        this.role = new SimpleStringProperty(role);
        this.status = new SimpleStringProperty(status);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // --- Getters, Setters, & JavaFX Properties ---

    public int getUserId() {
        return userId.get();
    }

    public void setUserId(int userId) {
        this.userId.set(userId);
    }

    public IntegerProperty userIdProperty() {
        return userId;
    }

    public String getUsername() {
        return username.get();
    }

    public void setUsername(String username) {
        this.username.set(username);
    }

    public StringProperty usernameProperty() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash.get();
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash.set(passwordHash);
    }

    public StringProperty passwordHashProperty() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName.get();
    }

    public void setFullName(String fullName) {
        this.fullName.set(fullName);
    }

    public StringProperty fullNameProperty() {
        return fullName;
    }

    public String getRole() {
        return role.get();
    }

    public void setRole(String role) {
        this.role.set(role);
    }

    public StringProperty roleProperty() {
        return role;
    }

    public String getStatus() {
        return status.get();
    }

    public void setStatus(String status) {
        this.status.set(status);
    }

    public StringProperty statusProperty() {
        return status;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return fullName.get() + " (" + role.get() + ")";
    }
}