package com.bhive.bills.service;

import java.util.List;

public record ConsumerIdentity(
        String primaryConsumerNumber,
        String hash,
        List<String> fieldNames
) {
}
