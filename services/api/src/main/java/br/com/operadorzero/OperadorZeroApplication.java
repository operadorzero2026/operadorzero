package br.com.operadorzero;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OperadorZeroApplication {
    public static void main(String[] args) {
        SpringApplication.run(OperadorZeroApplication.class, args);
    }
}
