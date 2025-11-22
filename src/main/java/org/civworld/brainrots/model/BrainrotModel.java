package org.civworld.brainrots.model;

import org.civworld.brainrots.type.Modificator;
import org.civworld.brainrots.type.Rarity;

public class BrainrotModel {
    private final String id;
    private final String displayname;
    private final Rarity rarity;
    private final int cost;
    private final Modificator modificator;
    private final int earn;

    public BrainrotModel(String id, String displayname, Rarity rarity, int cost, Modificator modificator, int earn){
        this.id = id;
        this.displayname = displayname;
        this.rarity = rarity;
        this.cost = cost;
        this.modificator = modificator;
        this.earn = earn;
    }
}