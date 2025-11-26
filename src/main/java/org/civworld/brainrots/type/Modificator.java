package org.civworld.brainrots.type;

import lombok.Getter;

@Getter public enum Modificator {
    BRONZE(1, 74.5),
    GOLD(1.25, 7.5),
    DIAMOND(1.5, 5),
    BLOODROT(2, 3.5),
    CELESTIAL(4, 2.5),
    CANDY(4, 2),
    LAVA(6, 1.75),
    GALAXY(6, 1.75),
    YIN_YANG(7.5, 0.5),
    RADIOACTIVE(8.5, 0.4),
    RAINBOW(10, 0.1);

    private final double value;
    private final double chance;

    Modificator(double value, double chance) {
        this.value = value;
        this.chance = chance;
    }
}