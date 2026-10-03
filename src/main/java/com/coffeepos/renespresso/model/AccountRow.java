package com.coffeepos.renespresso.model;

import java.time.LocalDateTime;

public record AccountRow(int id, String name, String username, String role,
                         String status, LocalDateTime lastLogin, LocalDateTime created) {}