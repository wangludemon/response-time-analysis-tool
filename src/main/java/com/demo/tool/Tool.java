package com.demo.tool;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kordamp.bootstrapfx.BootstrapFX;

import java.io.File;
import java.io.IOException;


public class Tool extends Application {
    public static Logger log = LogManager.getLogger();

    public static void main() {
        launch();
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        // Get system properties
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");
        String javaVersion = System.getProperty("java.version");
        String javaVendor = System.getProperty("java.vendor");

        // Log hardware-related information
        log.info("Operating System: {} (Version: {}, Arch: {})", osName, osVersion, osArch);
        log.info("Java Version: {} (Vendor: {})", javaVersion, javaVendor);

        log.info("Application started");
        FXMLLoader fxmlLoader = new FXMLLoader(Tool.class.getResource("exterior.fxml"));
        Parent root = fxmlLoader.load();
        log.info("fxml loader loaded");
        var sence = new Scene(root);
        sence.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
        log.info("stylesheets loaded");

        String parentDirectory = new File("src/main/resources/icon").getAbsolutePath();
        Image icon = new Image(new File(parentDirectory, "ana.png").toURI().toString());
        primaryStage.getIcons().add(icon);
        primaryStage.setScene(sence);

        primaryStage.show();
        log.info("Front-end loaded successfully");
    }

    @Override
    public void stop() throws Exception {
        log.info("Application exited");
    }
}