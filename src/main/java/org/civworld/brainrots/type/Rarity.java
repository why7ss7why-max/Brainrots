package org.civworld.brainrots.type;

import lombok.Getter;

public enum Rarity {
    COMMON(30),
    UNCOMMON(10),
    RARE(5),
    EPIC(5),
    LEGENDARY(2.5),
    MYTHIC(1),
    SECRET(0.5),
    LIMITED(0.1);

    @Getter private final double value;

    Rarity(double value) {
        this.value = value;
    }
}