open module com.example.demo {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;
    requires org.apache.logging.log4j;

    exports com.demo.tool;
    exports com.demo.tool.resource;


    requires com.fasterxml.jackson.databind;



}