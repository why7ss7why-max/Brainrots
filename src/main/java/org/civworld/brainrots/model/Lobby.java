package org.civworld.brainrots.model;

import lombok.Getter;
import lombok.Setter;
import org.apache.tools.ant.Location;

import java.util.HashSet;

@Getter @Setter public class Lobby {
    private final HashSet<House> houses = new HashSet<>();
    private final Location teleportLoc;
    private final int num;

    public Lobby(Location teleportLoc, int num){
        this.teleportLoc = teleportLoc;
        this.num = num;
    }

    public void addHouse(House house){
        houses.add(house);
    }
}