package fr.mathilde.easybans.database.migration;

import fr.mathilde.easybans.database.DatabaseProvider;
import fr.mathilde.easybans.database.DatabaseType;

import java.sql.Connection;
import java.sql.SQLException;

public interface Migration {

    int version();

    String description();

    void apply(Connection connection, DatabaseType type, DatabaseProvider db) throws SQLException;
}
