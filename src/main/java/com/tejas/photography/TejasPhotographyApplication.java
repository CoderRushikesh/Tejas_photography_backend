package com.tejas.photography;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TejasPhotographyApplication {

    public static void main(String[] args) {
        SpringApplication.run(TejasPhotographyApplication.class, args);
    }
}
