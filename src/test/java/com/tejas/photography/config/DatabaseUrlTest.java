package com.tejas.photography.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseUrlTest {

    @Test
    void parsesNeonConnectionString() {
        DatabaseUrl d = DatabaseUrl.parse(
                "postgresql://neondb_owner:p%40ss@ep-cool-123.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require");
        assertEquals("jdbc:postgresql://ep-cool-123.ap-southeast-1.aws.neon.tech:5432/neondb?sslmode=require", d.jdbcUrl());
        assertEquals("neondb_owner", d.username());
        assertEquals("p@ss", d.password());
    }

    @Test
    void addsSslModeAndAcceptsPostgresScheme() {
        DatabaseUrl d = DatabaseUrl.parse("postgres://u:p@host.example.com:6543/db");
        assertEquals("jdbc:postgresql://host.example.com:6543/db?sslmode=require", d.jdbcUrl());
    }

    @Test
    void jdbcUrlIsPassedThrough() {
        DatabaseUrl d = DatabaseUrl.parse("jdbc:postgresql://localhost:5432/x");
        assertEquals("jdbc:postgresql://localhost:5432/x", d.jdbcUrl());
        assertNull(d.username());
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseUrl.parse("mysql://a:b@c/d"));
        assertThrows(IllegalArgumentException.class, () -> DatabaseUrl.parse(" "));
    }
}
