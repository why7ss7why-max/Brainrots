package org.civworld.brainrots.model;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.civworld.brainrots.data.PlayerData;

@Getter @Setter public class House {
    private final Location plateCloseDoor;
    private PlayerData playerData = null;
    private final int id;
    private final boolean right;
    private boolean closed = false;

    public House(Location plateCloseDoor, int id, boolean right){
        this.plateCloseDoor = plateCloseDoor;
        this.id = id;
        this.right = right;
    }

    /**
     * Обновляет холограмму NPC, используя id этого дома.
     * Вызывает консольные команды:
     *   npc select <id>
     *   npc hologram set 0 <amount>
     */
    public void updateNpcBalance(int amount){
        updateNpcBalanceById(this.id, amount);
    }

    /**
     * Обновляет холограмму NPC по явному id NPC.
     * Простая синхронная отправка команд от имени консоли.
     */
    public static void updateNpcBalanceById(int npcId, int amount){
        if(npcId < 0) return;
        String selectCmd = "npc select " + npcId;
        String hologramCmd = "npc hologram set 0 " + amount;
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), selectCmd);
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), hologramCmd);
    }
}