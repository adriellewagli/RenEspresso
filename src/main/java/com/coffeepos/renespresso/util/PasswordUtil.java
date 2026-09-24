package com.coffeepos.renespresso.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Utility class for secure password hashing and verification using BCrypt.
 */
public class PasswordUtil {

    // Default workload factor (12 is recommended for good balance between security and performance)
    private static final int BCRYPT_LOG_ROUNDS = 12;

    private PasswordUtil() {
        // Private constructor to prevent instantiation of utility class
    }

    /**
     * Hashes a plain text password using BCrypt with a randomly generated salt.
     *
     * @param plainPassword The plain text password to hash
     * @return The hashed password string containing salt and cost factor
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty.");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_LOG_ROUNDS));
    }

    /**
     * Verifies if a plain text password matches a previously hashed BCrypt password.
     *
     * @param plainPassword The plain text password entered during login/validation
     * @param hashedPassword The stored BCrypt hashed password from the database
     * @return true if the password matches, false otherwise
     */
    public static boolean checkPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null || hashedPassword.isEmpty()) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (IllegalArgumentException e) {
            // Catches invalid/malformed BCrypt hash formats gracefully
            return false;
        }
    }
}