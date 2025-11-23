package org.civworld.brainrots.type;

import lombok.Getter;

public enum Rarity {
    COMMON(15),
    RARE(5),
    EPIC(5),
    LEGENDARY(2.5),
    MYTHIC(1),
    BRAINROT_GOD(0.5),
    SECRET(0.25),
    LIMITED(0.1);

    @Getter private final double value;

    Rarity(double value) {
        this.value = value;
    }
}