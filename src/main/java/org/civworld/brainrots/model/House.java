package org.civworld.brainrots.model;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashSet;

@Getter @Setter public class House {
    private Player player;
    private final Location plateCloseDoor;
    private HashSet<BrainrotModel> brainrots;

    public House(Location plateCloseDoor){
        this.plateCloseDoor = plateCloseDoor;
    }

    public void addBrainrot(BrainrotModel brainrotModel){
        brainrots.add(brainrotModel);
    }

    public void removeBrainrot(BrainrotModel brainrotModel){
        brainrots.remove(brainrotModel);
    }
}