package com.coffeepos.renespresso.util;

import com.coffeepos.renespresso.dao.UserDAO;
import com.coffeepos.renespresso.model.User;

public final class SessionManager {

    private static User currentUser;

    private SessionManager() {}

    public static synchronized void login(User user) {
        currentUser = user;
        if (user != null && user.getUserId() > 0) {
            UserDAO.setActiveStatus(user.getUserId(), true);
        }
    }

    public static synchronized void logout() {
        if (currentUser != null) {
            if (currentUser.getUserId() > 0) {
                UserDAO.setActiveStatus(currentUser.getUserId(), false);
            }
            currentUser = null;
        }
    }

    public static synchronized User getCurrentUser() { return currentUser; }

    public static synchronized void updateCurrentUserProfile(String fullName, String username) {
        if (currentUser != null) {
            currentUser.setFullName(fullName);
            currentUser.setUsername(username);
        }
    }

    public static synchronized boolean isLoggedIn() { return currentUser != null; }
}