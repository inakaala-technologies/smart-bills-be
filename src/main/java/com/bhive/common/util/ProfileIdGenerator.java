package com.bhive.common.util;

import java.security.SecureRandom;
import java.util.function.Predicate;

public final class ProfileIdGenerator {

    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SUFFIX_LENGTH = 5;

    private ProfileIdGenerator() {
    }

    public static String nextBusinessId(Predicate<String> isTaken) {
        return nextId("BIZ", isTaken);
    }

    public static String nextCustomerId(Predicate<String> isTaken) {
        return nextId("CUS", isTaken);
    }

    private static String nextId(String prefix, Predicate<String> isTaken) {
        for (int attempt = 0; attempt < 1000; attempt++) {
            StringBuilder candidate = new StringBuilder(prefix);
            for (int index = 0; index < SUFFIX_LENGTH; index++) {
                candidate.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
            }
            String profileId = candidate.toString();
            if (!isTaken.test(profileId)) {
                return profileId;
            }
        }
        throw new IllegalStateException("Unable to allocate a unique profile ID.");
    }
}