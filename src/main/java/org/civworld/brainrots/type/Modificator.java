package org.civworld.brainrots.type;

import lombok.Getter;

@Getter public enum Modificator {
    BRONZE(1),
    GOLD(1.25),
    DIAMOND(1.50),
    RAINBOW(10),
    LAVA(6),
    BLOODROT(2),
    CELESTIAL(4),
    CANDY(4),
    GALAXY(6),
    YIN_YANG(7.5),
    RADIOACTIVE(8.5);

    private final double value;

    Modificator(double value) {
        this.value = value;
    }
}