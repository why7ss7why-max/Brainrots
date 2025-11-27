package org.civworld.brainrots.listener;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.CitizensEnableEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.milkbowl.vault.economy.Economy;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.civworld.brainrots.model.BrainrotModel;
import org.civworld.brainrots.puller.Puller;
import org.civworld.brainrots.type.Modificator;

import static org.civworld.brainrots.util.Utils.parse;

public class NpcListener implements Listener {
    private final Economy economy;
    private final Puller puller;

    public NpcListener(Economy economy, Puller puller){
        this.economy = economy;
        this.puller = puller;
    }

    @EventHandler
    public void onLoad(CitizensEnableEvent event){
        for(NPC npc : CitizensAPI.getNPCRegistry()){
            if(npc.getName().equals("putevoditel") || npc.getName().equals("quests")) continue;
            npc.despawn();
            npc.destroy();
            CitizensAPI.getNPCRegistry().deregister(npc);
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "citizens save");
    }

    @EventHandler
    public void onClick(NPCRightClickEvent event){
        Player clicker = event.getClicker();
        NPC npc = event.getNPC();

        Pair<BrainrotModel, Modificator> pair = puller.getWalkingNpc().getOrDefault(npc, null);
        if(pair == null) return;

        BrainrotModel brainrotModel = pair.getKey();
        if(brainrotModel == null) return;

        Modificator modificator = pair.getValue();
        double cost = modificator == Modificator.BRONZE ? brainrotModel.getCost() : brainrotModel.getCost() * modificator.getValue();

        String costFormatted = formatDouble(cost);

        if(economy.getBalance(clicker) < cost){
            clicker.sendMessage(parse("<prefix>Недостаточно <red>монет<white>!"));
            return;
        }

        clicker.sendMessage(parse("<prefix>Вам <green>хватает <white>монет: <blue>" + costFormatted));
    }

    public static String formatDouble(double value) {
        java.text.DecimalFormat df = new java.text.DecimalFormat("#.################");
        df.setGroupingUsed(false);
        return df.format(value);
    }
}