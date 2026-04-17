module com.musicapp {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires java.sql;
    requires mysql.connector.j;

    exports com.musicapp;
    exports com.musicapp.ui;
    exports com.musicapp.model;
    exports com.musicapp.dao;
    exports com.musicapp.database;
    exports com.musicapp.util;

    opens com.musicapp.ui to javafx.fxml;
}
