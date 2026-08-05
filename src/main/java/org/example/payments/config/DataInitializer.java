package org.example.payments.config;

import java.util.UUID;
import org.example.payments.buyer.Buyer;
import org.example.payments.buyer.BuyerRepository;
import org.example.payments.product.Product;
import org.example.payments.product.ProductRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private final BuyerRepository buyerRepository;
    private final ProductRepository productRepository;

    public DataInitializer(BuyerRepository buyerRepository, ProductRepository productRepository) {
        this.buyerRepository = buyerRepository;
        this.productRepository = productRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (buyerRepository.count() == 0) {
            buyerRepository.save(new Buyer("게스트", "guest@example.com", UUID.randomUUID().toString()));
        }

        if (productRepository.count() == 0) {
            productRepository.save(new Product("티셔츠", 19_000L, 10));
            productRepository.save(new Product("후드티", 39_000L, 5));
            productRepository.save(new Product("에코백", 9_000L, 20));
        }
    }
}
