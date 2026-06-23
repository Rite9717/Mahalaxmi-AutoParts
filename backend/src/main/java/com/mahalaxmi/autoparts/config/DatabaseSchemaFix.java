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
            executeQuietly("alter table part modify column part_number varchar(255) null");
            executeQuietly("alter table part modify column car_compatibility text not null");
            executeQuietly("alter table bill modify column status enum('PENDING','PARTIALLY_PAID','FULLY_PAID','PAID','CANCELLED') not null default 'PAID'");
            executeQuietly("alter table bill modify column bill_type enum('ONGOING','FINAL') not null default 'FINAL'");
            executeQuietly("update bill set amount_paid = grand_total where amount_paid is null and status in ('PAID','FULLY_PAID')");
            executeQuietly("update bill set amount_paid = 0 where amount_paid is null");
            executeQuietly("update bill set balance_amount = 0 where balance_amount is null and status in ('PAID','FULLY_PAID')");
            executeQuietly("update bill set balance_amount = grand_total - amount_paid where balance_amount is null");
            normalizeVehicleCatalog();
        } catch (Exception ignored) {
            // Older installations may already have the correct column definitions.
        }
    }

    private void executeQuietly(String sql) {
        try {
            jdbc.execute(sql);
        } catch (Exception ignored) {
            // Schema drift is fixed best-effort at startup.
        }
    }

    private void normalizeVehicleCatalog() {
        try {
            jdbc.execute("update car_model set name = upper(trim(name)), series = case when series is null or trim(series) = '' then 'STANDARD' else upper(trim(series)) end");
            jdbc.execute("insert into car_brand (name, created_at) select 'PETROL', now() where not exists (select 1 from car_brand where upper(name) = 'PETROL')");
            jdbc.execute("insert into car_brand (name, created_at) select 'DIESEL', now() where not exists (select 1 from car_brand where upper(name) = 'DIESEL')");
            jdbc.execute("""
                    insert into car_model (brand_id, name, series, created_at)
                    select id, 'ALL', 'VARIENT', now()
                    from car_brand brand
                    where upper(brand.name) = 'PETROL'
                      and not exists (
                        select 1 from car_model model
                        where model.brand_id = brand.id and upper(model.name) = 'ALL' and upper(coalesce(model.series, '')) = 'VARIENT'
                      )
                    """);
            jdbc.execute("""
                    insert into car_model (brand_id, name, series, created_at)
                    select id, 'ALL', 'VARIENT', now()
                    from car_brand brand
                    where upper(brand.name) = 'DIESEL'
                      and not exists (
                        select 1 from car_model model
                        where model.brand_id = brand.id and upper(model.name) = 'ALL' and upper(coalesce(model.series, '')) = 'VARIENT'
                      )
                    """);
            jdbc.execute("drop temporary table if exists car_model_keep");
            jdbc.execute("drop temporary table if exists car_model_duplicates");
            jdbc.execute("""
                    create temporary table car_model_keep as
                    select min(id) keep_id, brand_id, name, coalesce(series, '') series_key
                    from car_model
                    group by brand_id, name, coalesce(series, '')
                    """);
            jdbc.execute("""
                    create temporary table car_model_duplicates as
                    select model.id duplicate_id, keepers.keep_id
                    from car_model model
                    join car_model_keep keepers
                      on keepers.brand_id = model.brand_id
                     and keepers.name = model.name
                     and keepers.series_key = coalesce(model.series, '')
                    where model.id <> keepers.keep_id
                    """);
            jdbc.execute("""
                    insert into part_model_compatibility (part_id, model_id)
                    select link.part_id, duplicate.keep_id
                    from part_model_compatibility link
                    join car_model_duplicates duplicate on duplicate.duplicate_id = link.model_id
                    where not exists (
                      select 1 from part_model_compatibility existing
                      where existing.part_id = link.part_id and existing.model_id = duplicate.keep_id
                    )
                    """);
            jdbc.execute("""
                    delete link
                    from part_model_compatibility link
                    join car_model_duplicates duplicate on duplicate.duplicate_id = link.model_id
                    """);
            jdbc.execute("""
                    delete model
                    from car_model model
                    join car_model_duplicates duplicate on duplicate.duplicate_id = model.id
                    """);
            jdbc.execute("drop temporary table if exists car_model_keep");
            jdbc.execute("drop temporary table if exists car_model_duplicates");
        } catch (Exception ignored) {
            // Catalog cleanup is best-effort; normal app startup should continue.
        }
    }
}
