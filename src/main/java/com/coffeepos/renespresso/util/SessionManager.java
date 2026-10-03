package com.coffeepos.renespresso.util;

import com.coffeepos.renespresso.model.User;

public final class SessionManager {

    private static User currentUser;

    private SessionManager() {}

    public static void login(User user) { currentUser = user; }

    public static void logout() { currentUser = null; }

    public static User getCurrentUser() { return currentUser; }

    public static boolean isLoggedIn() { return currentUser != null; }
}