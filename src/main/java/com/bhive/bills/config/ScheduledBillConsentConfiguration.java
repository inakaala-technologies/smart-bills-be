package com.bhive.bills.config;

import com.bhive.bills.service.ScheduledBillConsentProvider;
import com.bhive.bills.service.UnavailableScheduledBillConsentProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ScheduledBillConsentConfiguration {

    @Bean
    @ConditionalOnMissingBean(ScheduledBillConsentProvider.class)
    public ScheduledBillConsentProvider scheduledBillConsentProvider() {
        return new UnavailableScheduledBillConsentProvider();
    }
}
