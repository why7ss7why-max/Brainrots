package org.civworld.brainrots.data;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.civworld.brainrots.model.BrainrotModel;
import org.civworld.brainrots.type.Modificator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PlayerData {
    @Getter private final Player player;
    @Getter private final List<HashMap<BrainrotModel, Modificator>> ownBreinrots = new ArrayList<>();
    @Getter private long lastSaved;

    public PlayerData(Player player){
        this.player = player;
        ownBreinrots.add(new HashMap<>());
        this.lastSaved = Instant.now().toEpochMilli();
    }

    public void addBrainrot(int num, BrainrotModel brainrot, Modificator modificator){
        ownBreinrots.get(num).put(brainrot, modificator);
        lastSaved = Instant.now().toEpochMilli();
    }

    public Modificator getModificator(int num, BrainrotModel brainrotModel){
        return ownBreinrots.get(num).getOrDefault(brainrotModel, Modificator.BRONZE);
    }
}