package de.craftplay.shop.core.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class NumberUtil {
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.GERMANY));

    private NumberUtil() {
    }

    public static String money(double amount) {
        synchronized (MONEY_FORMAT) {
            return MONEY_FORMAT.format(amount);
        }
    }
}
