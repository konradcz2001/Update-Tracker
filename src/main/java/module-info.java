module com.github.konradcz2001.updatetracker {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires org.jsoup;
    requires com.fasterxml.jackson.databind;
    requires jdk.jsobject;
    requires java.desktop;
    requires java.logging;


    opens com.github.konradcz2001.updatetracker to javafx.fxml;
    exports com.github.konradcz2001.updatetracker;
}