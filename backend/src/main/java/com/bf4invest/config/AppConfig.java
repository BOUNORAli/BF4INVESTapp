package com.bf4invest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Value("${app.bc.number-prefix}")
    private String bcNumberPrefix;
    
    @Value("${app.default-payment-term-days}")
    private int defaultPaymentTermDays;
    
    @Value("${app.purchase-invoice-due-days}")
    private int purchaseInvoiceDueDays;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    
    public String getBcNumberPrefix() {
        return bcNumberPrefix;
    }
    
    public int getDefaultPaymentTermDays() {
        return defaultPaymentTermDays;
    }
    
    public int getPurchaseInvoiceDueDays() {
        return purchaseInvoiceDueDays;
    }
}


