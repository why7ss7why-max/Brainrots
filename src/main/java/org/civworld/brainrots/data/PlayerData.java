package org.civworld.brainrots.data;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.MutablePair;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.civworld.brainrots.model.BrainrotModel;
import org.civworld.brainrots.type.Modificator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PlayerData {
    @Getter private final Player player;
    @Getter private final List<MutablePair<BrainrotModel, Modificator>> ownBreinrots = new ArrayList<>();
    @Getter private final List<Double> storedAmounts = new ArrayList<>();
    @Getter @Setter private boolean autoSell = false;
    @Getter @Setter private long lastSaved;

    public PlayerData(Player player){
        this.player = player;
        this.lastSaved = Instant.now().toEpochMilli();
    }

    private final double[] stored = new double[10];

    public double takeStored(int slot) {
        if (slot < 0 || slot >= stored.length) return 0;
        double v = stored[slot];
        stored[slot] = 0;
        return v;
    }

    public void saveToConfig(FileConfiguration cfg, String path) {
        for (int i = 0; i < 10; i++) {
            cfg.set(path + ".stored." + i, stored[i]);
        }
    }

    public void loadFromConfig(FileConfiguration cfg, String path) {
        for (int i = 0; i < 10; i++) {
            stored[i] = cfg.getDouble(path + ".stored." + i, 0D);
        }
    }

    public void addBrainrot(int num, BrainrotModel brainrot, Modificator modificator){
        while(ownBreinrots.size() <= num) {
            ownBreinrots.add(null);
        }
        ownBreinrots.set(num, MutablePair.of(brainrot, modificator));
        while (storedAmounts.size() <= num) storedAmounts.add(0.0);
        if (storedAmounts.get(num) == null) storedAmounts.set(num, 0.0);
        lastSaved = Instant.now().toEpochMilli();
    }

    public void removeBrainrot(int num){
        if(num < 0) return;
        if(num >= ownBreinrots.size()) return;
        ownBreinrots.set(num, null);
        if (num < storedAmounts.size()) storedAmounts.set(num, 0.0);
        lastSaved = Instant.now().toEpochMilli();
    }

    public Modificator getModificator(int num, BrainrotModel model){
        if (num < 0 || num >= ownBreinrots.size()) return Modificator.BRONZE;
        MutablePair<BrainrotModel, Modificator> p = ownBreinrots.get(num);
        if (p == null) return Modificator.BRONZE;
        if (p.getLeft() == null || !p.getLeft().equals(model)) return Modificator.BRONZE;
        return p.getRight();
    }

    public double getStoredAmount(int num){
        if (num < 0) return 0.0;
        if (num >= storedAmounts.size()) return 0.0;
        Double v = storedAmounts.get(num);
        return v == null ? 0.0 : v;
    }

    public void setStoredAmount(int num, double value){
        while (storedAmounts.size() <= num) storedAmounts.add(0.0);
        storedAmounts.set(num, value);
        lastSaved = Instant.now().toEpochMilli();
    }

    public double addToStored(int num, double amount){
        while (storedAmounts.size() <= num) storedAmounts.add(0.0);
        double newVal = (storedAmounts.get(num) == null ? 0.0 : storedAmounts.get(num)) + amount;
        storedAmounts.set(num, newVal);
        lastSaved = Instant.now().toEpochMilli();
        return newVal;
    }

    public double withdrawStored(int num){
        double value = getStoredAmount(num);
        if (num < storedAmounts.size()) storedAmounts.set(num, 0.0);
        lastSaved = Instant.now().toEpochMilli();
        return value;
    }

    public boolean toggleAutoSell(){
        this.autoSell = !this.autoSell;
        lastSaved = Instant.now().toEpochMilli();
        return this.autoSell;
    }
}