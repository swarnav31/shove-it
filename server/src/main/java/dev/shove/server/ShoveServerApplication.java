package dev.shove.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ShoveServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoveServerApplication.class, args);
    }
}

