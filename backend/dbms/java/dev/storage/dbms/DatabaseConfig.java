package dev.storage.dbms;

import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the JDBC data source. All SQL is written by hand in the repositories and migrator. */
@Configuration
class DatabaseConfig {

    @Bean
    DataSource dataSource(@Value("${DB_URL}") String url,
                          @Value("${DB_USER}") String user,
                          @Value("${DB_PASSWORD}") String password) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(url);
        ds.setUser(user);
        ds.setPassword(password);
        return ds;
    }
}
