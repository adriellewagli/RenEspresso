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

    exports com.coffeepos.renespresso.util;
    exports com.coffeepos.renespresso.controller.id;
    opens com.coffeepos.renespresso.controller.id to javafx.fxml;
    exports com.coffeepos.renespresso.controller.util;
    opens com.coffeepos.renespresso.controller.util to javafx.fxml;
    exports com.coffeepos.renespresso.controller.main;
    opens com.coffeepos.renespresso.controller.main to javafx.fxml;

}