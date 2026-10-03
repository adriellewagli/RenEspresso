package com.coffeepos.renespresso.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AccountRow(
        int id,
        String name,
        String username,
        String email,
        String role,
        String status,
        LocalDateTime lastLogin,
        LocalDate created
) {}