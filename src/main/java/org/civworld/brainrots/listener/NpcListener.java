package org.civworld.brainrots.listener;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.CitizensEnableEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.milkbowl.vault.economy.Economy;
import org.apache.commons.lang3.tuple.Pair;
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
    public void onClick(NPCRightClickEvent event){
        Player clicker = event.getClicker();
        NPC npc = event.getNPC();

        Pair<BrainrotModel, Modificator> pair = puller.getWalkingNpc().getOrDefault(npc, null);
        if(pair == null) return;

        BrainrotModel brainrotModel = pair.getKey();
        if(brainrotModel == null) return;

        Modificator modificator = pair.getValue();
        double earn = brainrotModel.getEarn() * modificator.getValue();
        double cost = modificator == Modificator.BRONZE ? brainrotModel.getCost() : brainrotModel.getCost() * modificator.getValue();

        if(economy.getBalance(clicker) < cost){
            clicker.sendMessage(parse("<prefix>Недостаточно <red>монет<white>!"));
            return;
        }

        clicker.sendMessage(parse("<prefix>Вам <green>хватает <white>монет: <blue>" + cost));
    }
}