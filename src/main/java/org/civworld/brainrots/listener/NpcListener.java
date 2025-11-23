package org.civworld.brainrots.listener;

import net.citizensnpcs.api.event.NPCClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.civworld.brainrots.model.BrainrotModel;
import org.civworld.brainrots.puller.Puller;

import static org.civworld.brainrots.util.Utils.parse;

public class NpcListener implements Listener {
    private final Economy economy;
    private final Puller puller;

    public NpcListener(Economy economy, Puller puller){
        this.economy = economy;
        this.puller = puller;
    }

    @EventHandler
    public void onClick(NPCRightClickEvent event){
        Bukkit.getLogger().info("NPC clicked: " + event.getNPC().getName());
        Player clicker = event.getClicker();
        NPC npc = event.getNPC();

        BrainrotModel brainrotModel = puller.getWalkingNpc().getOrDefault(npc, null);
        if(brainrotModel == null) return;

        if(economy.getBalance(clicker) < brainrotModel.getCost()){
            clicker.sendMessage(parse("<prefix>Недостаточно <red>монет<white>!"));
            return;
        }

        clicker.sendMessage(parse("<prefix>Вам <green>хватает <white>монет: <blue>" + brainrotModel.getCost()));
    }
}