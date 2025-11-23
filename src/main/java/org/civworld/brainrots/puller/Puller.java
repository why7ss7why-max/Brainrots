package org.civworld.brainrots.puller;

import com.destroystokyo.paper.entity.Pathfinder;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.civworld.brainrots.model.BrainrotModel;
import org.civworld.brainrots.model.Lobby;
import org.civworld.brainrots.repo.BrainrotRepo;
import org.civworld.brainrots.repo.LobbyRepo;

import java.util.UUID;

public class Puller {
    private final Plugin plugin;
    private final BrainrotRepo brainrotRepo;
    private final LobbyRepo lobbyRepo;

    private BukkitRunnable bukkitRunnable = null;
    private boolean said = false;
    private boolean saidLobby = false;

    public Puller(Plugin plugin, BrainrotRepo brainrotRepo, LobbyRepo lobbyRepo){
        this.plugin = plugin;
        this.brainrotRepo = brainrotRepo;
        this.lobbyRepo = lobbyRepo;
    }

    public void startPoll(){
        bukkitRunnable = new BukkitRunnable() {
            @Override
            public void run() {
                BrainrotModel brainrotModel = getRandomBrainrot();
                if(brainrotModel == null) return;

                if(!saidLobby && lobbyRepo.getLobbies().isEmpty()){
                    plugin.getLogger().warning("ОШИБКА! Лобби не созданы. Пожалуйста, создайте их через /bt.");
                    saidLobby = true;
                    return;
                }

                for (Lobby lobby : lobbyRepo.getLobbies()) {
                    CommandSender console = Bukkit.getConsoleSender();
                    Location loc = lobby.getTeleportLoc();

                    String uuid = new UUID(16, 16).toString();

                    NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, uuid);

                    npc.setName("");

                    npc.spawn(loc);

                    npc.setUseMinecraftAI(false);
                    Bukkit.dispatchCommand(console, "npc select " + npc.getId());
                    Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.dispatchCommand(console, "npc pathto " + (loc.getX() + 75) + " " + loc.getY() + " " + loc.getZ()), 10L);
                    Bukkit.dispatchCommand(console, "trait add meg_model");

                    npc.getNavigator().getDefaultParameters().speedModifier(0.5f);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        npc.despawn();
                        npc.destroy();
                    }, 20L * 30);

                    Bukkit.dispatchCommand(console, "meg npc model citizens:" + npc.getId() + ":[" + uuid + "] add " + brainrotModel.getId().toLowerCase());
                }
            }
        };

        bukkitRunnable.runTaskTimer(plugin, 0, 20L * 5);
    }

    public void stopPull(){
        if(!bukkitRunnable.isCancelled() || bukkitRunnable == null){
            plugin.getLogger().warning("Попытка остановить Pull когда он не начат!");
            return;
        }
        bukkitRunnable.cancel();
        bukkitRunnable = null;
    }

    public BrainrotModel getRandomBrainrot() {
        var brainrots = brainrotRepo.getBrainrots();
        if (brainrots.isEmpty()) {
            if (!said) {
                plugin.getLogger().warning("Бреинроты не настроены! Пожалуйста, создайте их через /bt.");
            }
            said = true;
            return null;
        }

        double totalChance = 0;
        for (BrainrotModel b : brainrots) {
            totalChance += b.getRarity().getValue();
        }

        double roll = Math.random() * totalChance;

        double cumulative = 0;
        for (BrainrotModel b : brainrots) {
            cumulative += b.getRarity().getValue();
            if (roll <= cumulative) {
                return b;
            }
        }

        return brainrots.stream().toList().get(brainrots.size() - 1);
    }
}