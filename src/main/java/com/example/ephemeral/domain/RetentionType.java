package com.example.ephemeral.domain;

public enum RetentionType {
    VIEW_ONCE_5MIN("view-once"),
    STORE_24HR("store-24hr");

    private final String keyPrefix;

    RetentionType(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }
}
