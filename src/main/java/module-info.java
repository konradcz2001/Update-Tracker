module com.github.konradcz2001.updatetracker {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires org.jsoup;
    requires com.fasterxml.jackson.databind;
    requires jdk.jsobject;
    requires java.desktop;
    requires java.logging;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.material2;


    opens com.github.konradcz2001.updatetracker to javafx.fxml;
    exports com.github.konradcz2001.updatetracker;
    exports com.github.konradcz2001.updatetracker.ui;
    exports com.github.konradcz2001.updatetracker.service;
    exports com.github.konradcz2001.updatetracker.util;
}