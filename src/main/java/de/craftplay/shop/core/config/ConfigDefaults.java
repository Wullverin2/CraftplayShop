package de.craftplay.shop.core.config;

import java.util.List;

public final class ConfigDefaults {
    public static final List<String> LANGUAGES = List.of("de_DE", "en_US");
    public static final List<String> GUI_FILES = List.of(
            "main.yml",
            "servershop.yml",
            "servershop_category.yml",
            "settings.yml",
            "directtrade.yml",
            "autosellchest.yml",
            "playershop.yml",
            "rankshop.yml",
            "permissionshop.yml",
            "referral.yml",
            "admin.yml"
    );

    private ConfigDefaults() {
    }
}
