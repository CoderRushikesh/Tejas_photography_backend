package com.tejas.photography.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * One DataSource for both Neon and local Postgres.
 * <ul>
 *   <li>DATABASE_URL set (Neon's "postgresql://user:pass@host/db?sslmode=require") -> parsed into JDBC settings.</li>
 *   <li>otherwise spring.datasource.url / username / password are used (local Postgres, or H2 in tests).</li>
 * </ul>
 * The pool is deliberately tiny: Neon free allows few connections and suspends idle compute, so connections
 * are retired quickly and the pool may shrink to zero.
 */
@Configuration
@EnableConfigurationProperties(DataSourceProperties.class)
public class DataSourceConfig {

    @Bean
    public DataSource dataSource(DataSourceProperties props,
                                 @Value("${DATABASE_URL:}") String databaseUrl,
                                 @Value("${DB_POOL_SIZE:5}") int poolSize) {
        String jdbcUrl = props.determineUrl();
        String username = props.determineUsername();
        String password = props.determinePassword();
        String driver = props.determineDriverClassName();

        if (StringUtils.hasText(databaseUrl)) {
            DatabaseUrl parsed = DatabaseUrl.parse(databaseUrl);
            jdbcUrl = parsed.jdbcUrl();
            if (parsed.username() != null) {
                username = parsed.username();
                password = parsed.password();
            }
            driver = "org.postgresql.Driver";
        }

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(username);
        ds.setPassword(password);
        if (StringUtils.hasText(driver)) {
            ds.setDriverClassName(driver);
        }
        ds.setPoolName("tejas-pool");
        ds.setMaximumPoolSize(poolSize);
        ds.setMinimumIdle(0);
        ds.setIdleTimeout(60_000);
        ds.setMaxLifetime(240_000);
        ds.setConnectionTimeout(30_000); // a suspended Neon compute needs a moment to wake up
        return ds;
    }
}
