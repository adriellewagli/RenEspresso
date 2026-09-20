module com.coffeepos.renespresso {
    requires javafx.controls;
    requires javafx.fxml;

    requires java.sql;
    requires org.mariadb.jdbc;
    requires com.zaxxer.hikari;

    requires org.controlsfx.controls;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;

    exports com.coffeepos.renespresso;
    opens com.coffeepos.renespresso to javafx.fxml;

    exports com.coffeepos.renespresso.controller;
    opens com.coffeepos.renespresso.controller to javafx.fxml;

    exports com.coffeepos.renespresso.util;

}