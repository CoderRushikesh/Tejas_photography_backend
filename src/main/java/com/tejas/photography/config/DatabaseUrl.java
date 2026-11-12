package com.tejas.photography.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the connection string that Neon (and Render / Railway / Heroku style hosts) hand out into JDBC settings.
 * <pre>
 *   postgresql://user:pass@ep-cool-123.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
 *   -> jdbc:postgresql://ep-cool-123.ap-southeast-1.aws.neon.tech:5432/neondb?sslmode=require
 * </pre>
 * Neon requires SSL, so sslmode=require is added when missing. channel_binding is dropped (the JDBC driver
 * does not use it).
 */
public record DatabaseUrl(String jdbcUrl, String username, String password) {

    public static DatabaseUrl parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL is empty");
        }
        String url = raw.trim();
        if (url.startsWith("jdbc:")) {
            return new DatabaseUrl(url, null, null);
        }
        if (url.startsWith("postgres://")) {
            url = "postgresql://" + url.substring("postgres://".length());
        }
        if (!url.startsWith("postgresql://")) {
            throw new IllegalArgumentException("DATABASE_URL must start with postgresql:// or jdbc:postgresql://");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "DATABASE_URL is not a valid URI (percent-encode special characters in the password)");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("DATABASE_URL has no host");
        }

        String user = null;
        String pass = null;
        String rawUserInfo = uri.getRawUserInfo();
        if (rawUserInfo != null) {
            int colon = rawUserInfo.indexOf(':');
            user = decode(colon < 0 ? rawUserInfo : rawUserInfo.substring(0, colon));
            pass = colon < 0 ? null : decode(rawUserInfo.substring(colon + 1));
        }

        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();

        List<String> params = new ArrayList<>();
        boolean hasSsl = false;
        if (uri.getRawQuery() != null) {
            for (String p : uri.getRawQuery().split("&")) {
                if (p.isBlank() || p.startsWith("channel_binding=")) {
                    continue;
                }
                if (p.startsWith("sslmode=")) {
                    hasSsl = true;
                }
                params.add(p);
            }
        }
        if (!hasSsl) {
            params.add("sslmode=require");
        }
        String jdbc = "jdbc:postgresql://" + uri.getHost() + ":" + port + path + "?" + String.join("&", params);
        return new DatabaseUrl(jdbc, user, pass);
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
