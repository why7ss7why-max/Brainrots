package org.civworld.brainrots.model;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.civworld.brainrots.data.PlayerData;

@Getter @Setter public class House {
    private final Location plateCloseDoor;
    private PlayerData playerData;
    private final int id;
    private final boolean right;

    public House(Location plateCloseDoor, int id, boolean right){
        this.plateCloseDoor = plateCloseDoor;
        this.id = id;
        this.right = right;
    }
}