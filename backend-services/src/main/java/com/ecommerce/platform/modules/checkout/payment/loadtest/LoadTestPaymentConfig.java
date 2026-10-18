package com.ecommerce.platform.modules.checkout.payment.loadtest;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LoadTestPaymentProperties.class)
public class LoadTestPaymentConfig {
}
