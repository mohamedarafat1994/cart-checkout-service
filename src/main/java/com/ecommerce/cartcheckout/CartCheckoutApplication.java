package com.ecommerce.cartcheckout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CartCheckoutApplication {
    public static void main(String[] args) {
        SpringApplication.run(CartCheckoutApplication.class, args);
    }
}
