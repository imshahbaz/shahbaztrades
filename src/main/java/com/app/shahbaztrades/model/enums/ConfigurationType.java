package com.app.shahbaztrades.model.enums;

import com.app.shahbaztrades.model.entity.ClientConfigurations;
import com.app.shahbaztrades.model.entity.ServerConfigurations;

public enum ConfigurationType {
    SERVER,
    CLIENT;

    public Class<?> getConfigClassName() {
        switch (this) {
            case SERVER -> {
                return ServerConfigurations.class;
            }
            case CLIENT -> {
                return ClientConfigurations.class;
            }
            default -> throw new IllegalStateException("Unexpected value: " + this);
        }
    }

}
