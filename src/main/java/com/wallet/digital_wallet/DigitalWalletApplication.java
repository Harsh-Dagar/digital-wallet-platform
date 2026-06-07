package com.wallet.digital_wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories
public class DigitalWalletApplication {
    public static void main(String[] args) {
        SpringApplication.run(DigitalWalletApplication.class, args);
    }
}
