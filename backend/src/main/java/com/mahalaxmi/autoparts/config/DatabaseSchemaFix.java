package com.mahalaxmi.autoparts.config;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSchemaFix {
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public DatabaseSchemaFix(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @PostConstruct
    void relaxLegacyInventoryColumns() {
        try (var connection = dataSource.getConnection()) {
            String database = connection.getMetaData().getDatabaseProductName();
            if (database == null || !database.toLowerCase().contains("mysql")) {
                return;
            }
            jdbc.execute("alter table part modify column part_number varchar(255) null");
            jdbc.execute("alter table part modify column car_compatibility text not null");
        } catch (Exception ignored) {
            // Older installations may already have the correct column definitions.
        }
    }
}
