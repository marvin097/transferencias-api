package com.izalco.transferencias;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TransferenciasApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(TransferenciasApiApplication.class, args);
	}

}
