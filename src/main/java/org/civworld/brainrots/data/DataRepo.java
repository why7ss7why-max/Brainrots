package org.civworld.brainrots.data;

import lombok.Getter;
import org.bukkit.entity.Player;

import java.util.HashMap;

public class DataRepo {
    @Getter private final HashMap<Player, PlayerData> playerDatas = new HashMap<>();

    public void addPlayerData(Player player, PlayerData data){
        playerDatas.put(player, data);
    }

    public void removePlayerData(Player player){
        playerDatas.remove(player);
    }

    public PlayerData getPlayerData(Player player){
        return playerDatas.computeIfAbsent(player, PlayerData::new);
    }
}