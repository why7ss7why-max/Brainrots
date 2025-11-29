package org.civworld.brainrots.data;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.MutablePair;
import org.bukkit.entity.Player;
import org.civworld.brainrots.model.BrainrotModel;
import org.civworld.brainrots.type.Modificator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PlayerData {
    @Getter private final Player player;
    @Getter private final List<MutablePair<BrainrotModel, Modificator>> ownBreinrots = new ArrayList<>();
    @Getter @Setter private long lastSaved;

    public PlayerData(Player player){
        this.player = player;
        this.lastSaved = Instant.now().toEpochMilli();
    }

    public void addBrainrot(int num, BrainrotModel brainrot, Modificator modificator){
        while(ownBreinrots.size() <= num) {
            ownBreinrots.add(null);
        }
        ownBreinrots.set(num, MutablePair.of(brainrot, modificator));
        lastSaved = Instant.now().toEpochMilli();
    }

    public Modificator getModificator(int num, BrainrotModel model){
        MutablePair<BrainrotModel, Modificator> p = ownBreinrots.get(num);
        if (p == null) return Modificator.BRONZE;
        if (!p.getLeft().equals(model)) return Modificator.BRONZE;
        return p.getRight();
    }
}