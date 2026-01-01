module com.github.konradcz2001.updatetracker {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires org.jsoup;
    requires com.fasterxml.jackson.databind;
    requires jdk.jsobject;
    requires java.desktop;


    opens com.github.konradcz2001.updatetracker to javafx.fxml;
    exports com.github.konradcz2001.updatetracker;
}