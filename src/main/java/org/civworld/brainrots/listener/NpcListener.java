package org.civworld.brainrots.listener;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.CitizensEnableEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.milkbowl.vault.economy.Economy;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.civworld.brainrots.data.PlayerData;
import org.civworld.brainrots.model.BrainrotModel;
import org.civworld.brainrots.model.House;
import org.civworld.brainrots.model.Lobby;
import org.civworld.brainrots.puller.Puller;
import org.civworld.brainrots.repo.LobbyRepo;
import org.civworld.brainrots.type.Modificator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.civworld.brainrots.util.Utils.parse;

public class NpcListener implements Listener {
    private final Economy economy;
    private final Puller puller;
    private final LobbyRepo lobbyRepo;

    private final Map<NPC, Long> npcSoundCooldowns = new ConcurrentHashMap<>();
    private static final long SOUND_COOLDOWN = 1000;

    public NpcListener(Economy economy, Puller puller, LobbyRepo lobbyRepo){
        this.economy = economy;
        this.puller = puller;
        this.lobbyRepo = lobbyRepo;
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

        House house = null;
        PlayerData playerData = null;
        // ищем дом конкретного кликающего игрока в доступных лобби
        for(Lobby l : lobbyRepo.getLobbies()){
            for(House h : l.getHouses()){
                PlayerData pd = h.getPlayerData();
                if(pd == null) continue;
                if (pd.getPlayer().equals(clicker)){
                    playerData = pd;
                    house = h;
                    break;
                }
            }
            if (house != null) break;
        }

        if(playerData == null){
            clicker.sendMessage(parse("<prefix>Вы <red>не состоите<white> в <blue>лобби<white>!"));
            return;
        }

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

        long now = System.currentTimeMillis();
        Long last = npcSoundCooldowns.get(npc);

        if(last == null || now - last >= SOUND_COOLDOWN){
            for (Player p : npc.getEntity().getWorld().getPlayers()) {
                if (p.getLocation().distance(npc.getStoredLocation()) <= 16) {
                    p.playSound(
                            npc.getStoredLocation(),
                            "brainrot:sound." + brainrotModel.getId(),
                            SoundCategory.BLOCKS,
                            1.0f,
                            1.0f
                    );
                }
            }
            npcSoundCooldowns.put(npc, now);
        }

        Location newLoc = house.getPlateCloseDoor().clone().add(0, -1, house.isRight() ? 26 : -26);
        economy.withdrawPlayer(clicker, cost);
        clicker.sendMessage(parse("<prefix>С вас <gold>" + costFormatted + "<white> монет <green>списано<white>!"));
        clicker.sendMessage(parse("<prefix>Нпс идёт по координатам " + newLoc.getX() + " " + newLoc.getY() + " " + newLoc.getZ()));
        // плавное перемещение NPC небольшими шагами (значение шага можно регулировать)
        puller.moveNpcSmooth(npc, newLoc, 1.0);
    }

    public static String formatDouble(double value) {
        java.text.DecimalFormat df = new java.text.DecimalFormat("#.################");
        df.setGroupingUsed(false);
        return df.format(value);
    }
}