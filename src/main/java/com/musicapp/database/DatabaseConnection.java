package com.musicapp.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    private static final String DEFAULT_URL =
            "jdbc:mysql://127.0.0.1:3306/musicapp?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true";
    private static final String DEFAULT_USERNAME = "root";
    private static final String DEFAULT_PASSWORD = "123456";

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        String url = read("MUSICAPP_DB_URL", DEFAULT_URL);
        String username = read("MUSICAPP_DB_USER", DEFAULT_USERNAME);
        String password = read("MUSICAPP_DB_PASSWORD", DEFAULT_PASSWORD);
        return DriverManager.getConnection(url, username, password);
    }

    private static String read(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        return (value == null || value.isBlank()) ? fallback : value;
    }
}