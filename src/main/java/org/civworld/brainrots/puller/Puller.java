package org.civworld.brainrots.puller;

import lombok.Getter;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.LookClose;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.civworld.brainrots.model.BrainrotModel;
import org.civworld.brainrots.model.Lobby;
import org.civworld.brainrots.repo.BrainrotRepo;
import org.civworld.brainrots.repo.LobbyRepo;
import org.civworld.brainrots.type.Rarity;

import java.util.*;
import java.util.stream.Collectors;

import static org.civworld.brainrots.util.Utils.*;

public class Puller {

    private final Plugin plugin;
    private final BrainrotRepo brainrotRepo;
    private final LobbyRepo lobbyRepo;

    private BukkitRunnable mainTask = null;
    private boolean warnedNoBrainrots = false;
    private boolean warnedNoLobbies = false;

    @Getter
    private final Map<NPC, BrainrotModel> walkingNpc = new HashMap<>();

    private final Map<Integer, BrainrotModel> forcedNext = new HashMap<>();

    public Puller(Plugin plugin, BrainrotRepo brainrotRepo, LobbyRepo lobbyRepo) {
        this.plugin = plugin;
        this.brainrotRepo = brainrotRepo;
        this.lobbyRepo = lobbyRepo;
    }

    public void forceNext(int lobby, BrainrotModel brainrotModel) {
        forcedNext.put(lobby, brainrotModel);
    }

    public void forceNextAll(BrainrotModel brainrotModel) {
        for (Lobby lb : lobbyRepo.getLobbies()) {
            forcedNext.put(lb.getNum(), brainrotModel);
        }
    }

    public void startPull() {
        if (mainTask != null) return;

        mainTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (lobbyRepo.getLobbies().isEmpty()) {
                    if (!warnedNoLobbies) {
                        plugin.getLogger().warning("Лобби не созданы! NPC не будут спавниться.");
                        warnedNoLobbies = true;
                    }
                    return;
                }
                warnedNoLobbies = false;

                CommandSender console = Bukkit.getConsoleSender();

                for (Lobby lobby : lobbyRepo.getLobbies()) {
                    BrainrotModel model = forcedNext.remove(lobby.getNum());

                    if (model == null) {
                        model = getRandomBrainrot();
                    }

                    Location start = lobby.getTeleportLoc().clone();
                    Location end = start.clone().add(145, 0, 0);

                    String uuid = UUID.randomUUID().toString();
                    NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, uuid);
                    npc.setName("");
                    npc.spawn(start);

                    npc.setUseMinecraftAI(false);
                    npc.getNavigator().getLocalParameters()
                            .speedModifier(0.9f)
                            .stuckAction(null)
                            .distanceMargin(1.0)
                            .useNewPathfinder(true);
                    npc.getNavigator().getDefaultParameters().range(200f);

                    LookClose look = npc.getOrAddTrait(LookClose.class);
                    look.lookClose(false);

                    Bukkit.dispatchCommand(console, "npc select " + npc.getId());
                    Bukkit.dispatchCommand(console, "trait add meg_model");
                    Bukkit.dispatchCommand(console,
                            "meg npc model citizens:" + npc.getId() + ":[" + uuid + "] add " + model.getId().toLowerCase());

                    Bukkit.dispatchCommand(console, "npc hologram lineheight 0.25");
                    Bukkit.dispatchCommand(console, "npc hitbox --width " + model.getWidthHitbox() + " --height " + model.getHeightHitbox());
                    Bukkit.dispatchCommand(console, "npc hologram add &a$" + formatNumber(model.getCost()));
                    Bukkit.dispatchCommand(console, "npc hologram marginbottom 0 " + model.getMarginHologram());
                    Bukkit.dispatchCommand(console, "npc hologram add &e$" + formatNumber(model.getEarn()) + "/с");

                    if (model.getRarity().equals(Rarity.BRAINROT_GOD)) {
                        Bukkit.dispatchCommand(console,
                                "npc hologram add &#FF0000B&#FF4000r&#FF7F00a&#FFBF00i&#FFFF00n&#80FF00r&#00FF00o&#0000FFt &#4A00E9G&#6F00DEo&#9400D3d");
                    } else {
                        Bukkit.dispatchCommand(console,
                                "npc hologram add &f" + colorFromRarity(model.getRarity()) + capitalizeFirst(model.getRarity().toString()));
                    }

                    Bukkit.dispatchCommand(console, "npc hologram add &f" + capitalizeFirst(model.getDisplayName()));

                    walkingNpc.put(npc, model);

                    npc.getNavigator().setTarget(end);

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!npc.isSpawned()) {
                                walkingNpc.remove(npc);
                                cancel();
                                return;
                            }

                            if (!npc.getNavigator().isNavigating() || npc.getStoredLocation().distance(end) <= 2.0) {
                                npc.despawn(DespawnReason.REMOVAL);
                                npc.destroy();
                                walkingNpc.remove(npc);
                                cancel();
                            }
                        }
                    }.runTaskTimer(plugin, 10, 10);
                }
            }
        };

        mainTask.runTaskTimer(plugin, 0, 60);
    }

    public void stopPull() {
        if (mainTask != null) {
            mainTask.cancel();
            mainTask = null;
        }

        for (NPC npc : walkingNpc.keySet()) {
            npc.despawn();
            npc.destroy();
        }
        walkingNpc.clear();
    }

    private BrainrotModel getRandomBrainrot() {
        var list = brainrotRepo.getBrainrots().stream().toList();
        if (list.isEmpty()) {
            if (!warnedNoBrainrots) {
                plugin.getLogger().warning("Бреинроты не настроены! Создайте через /bt");
                warnedNoBrainrots = true;
            }
            return null;
        }
        warnedNoBrainrots = false;

        Map<Rarity, List<BrainrotModel>> byRarity = list.stream()
                .collect(Collectors.groupingBy(BrainrotModel::getRarity));

        double totalWeight = byRarity.values().stream()
                .mapToDouble(l -> l.getFirst().getRarity().getValue())
                .sum();

        double roll = Math.random() * totalWeight;
        double cum = 0;
        Rarity chosenRarity = null;

        for (var entry : byRarity.entrySet()) {
            cum += entry.getValue().getFirst().getRarity().getValue();
            if (roll <= cum) {
                chosenRarity = entry.getKey();
                break;
            }
        }

        if (chosenRarity == null) chosenRarity = list.getFirst().getRarity();

        List<BrainrotModel> options = byRarity.get(chosenRarity);
        return options.get(new Random().nextInt(options.size()));
    }
}