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
    requires jbcrypt;
    requires MaterialFX;

    exports com.coffeepos.renespresso;
    opens com.coffeepos.renespresso to javafx.fxml;

    exports com.coffeepos.renespresso.util;
    opens com.coffeepos.renespresso.util to javafx.fxml;

    exports com.coffeepos.renespresso.controller.id;
    opens com.coffeepos.renespresso.controller.id to javafx.fxml;
    exports com.coffeepos.renespresso.controller.util;
    opens com.coffeepos.renespresso.controller.util to javafx.fxml;

    exports com.coffeepos.renespresso.controller.main.admin;
    opens com.coffeepos.renespresso.controller.main.admin to javafx.fxml;
    exports com.coffeepos.renespresso.controller.main.admin.home;
    opens com.coffeepos.renespresso.controller.main.admin.home to javafx.fxml;
    exports com.coffeepos.renespresso.controller.main.admin.menu;
    opens com.coffeepos.renespresso.controller.main.admin.menu to javafx.fxml;
    exports com.coffeepos.renespresso.controller.main.admin.sales;
    opens com.coffeepos.renespresso.controller.main.admin.sales to javafx.fxml;
    exports com.coffeepos.renespresso.controller.main.admin.transact;
    opens com.coffeepos.renespresso.controller.main.admin.transact to javafx.fxml;
    exports com.coffeepos.renespresso.controller.main.admin.account;
    opens com.coffeepos.renespresso.controller.main.admin.account to javafx.fxml;

    exports com.coffeepos.renespresso.controller.main.user;
    opens com.coffeepos.renespresso.controller.main.user to javafx.fxml, javafx.base;
    exports com.coffeepos.renespresso.controller.main.user.pos;
    opens com.coffeepos.renespresso.controller.main.user.pos to javafx.fxml;
    exports com.coffeepos.renespresso.controller.main.user.om;
    opens com.coffeepos.renespresso.controller.main.user.om to javafx.fxml;
    exports com.coffeepos.renespresso.controller.main.user.About;
    opens com.coffeepos.renespresso.controller.main.user.About to javafx.fxml;
}