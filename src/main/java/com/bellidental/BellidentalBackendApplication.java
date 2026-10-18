package com.bellidental;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BellidentalBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BellidentalBackendApplication.class, args);
	}

}
