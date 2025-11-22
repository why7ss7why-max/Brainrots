package org.civworld.brainrots.model;

import lombok.Getter;
import lombok.Setter;
import org.civworld.brainrots.type.Modificator;
import org.civworld.brainrots.type.Rarity;

@Getter @Setter public class BrainrotModel {
    private final String id;
    private final String displayName;
    private final Rarity rarity;
    private final int cost;
    private final Modificator modificator;
    private final int earn;

    public BrainrotModel(String id, String displayName, Rarity rarity, int cost, Modificator modificator, int earn){
        this.id = id;
        this.displayName = displayName;
        this.rarity = rarity;
        this.cost = cost;
        this.modificator = modificator;
        this.earn = earn;
    }
}