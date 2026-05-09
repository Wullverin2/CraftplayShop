package de.craftplay.shop.referral;

import java.security.SecureRandom;
import java.util.Locale;

public class ReferralCodeService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    public String generate(int length) {
        int size = Math.max(4, length);
        StringBuilder builder = new StringBuilder(size);
        for (int index = 0; index < size; index++) {
            builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return builder.toString().toUpperCase(Locale.ROOT);
    }
}
