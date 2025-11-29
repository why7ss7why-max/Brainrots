package org.civworld.brainrots.puller;

import lombok.Getter;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.LookClose;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.civworld.brainrots.Brainrots;
import org.civworld.brainrots.config.Config;
import org.civworld.brainrots.data.PlayerData;
import org.civworld.brainrots.model.BrainrotModel;
import org.civworld.brainrots.model.House;
import org.civworld.brainrots.model.Lobby;
import org.civworld.brainrots.repo.BrainrotRepo;
import org.civworld.brainrots.repo.LobbyRepo;
import org.civworld.brainrots.type.Modificator;
import org.civworld.brainrots.type.Rarity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.civworld.brainrots.util.Utils.*;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;

public class Puller {

    private final Plugin plugin;
    private final BrainrotRepo brainrotRepo;
    private final LobbyRepo lobbyRepo;
    private final Config config;

    private BukkitRunnable mainTask = null;
    private BukkitRunnable monitorTask = null;
    private BukkitRunnable earningsTask = null;
    private BukkitRunnable saveFlushTask = null;

    private final Set<Player> dirtyPlayers = Collections.synchronizedSet(new HashSet<>());

    @Getter private final Map<NPC, Pair<BrainrotModel, Modificator>> walkingNpc = new ConcurrentHashMap<>();
    private final Map<Integer, BrainrotModel> forcedNext = new ConcurrentHashMap<>();
    private final Map<NPC, BukkitRunnable> movementTasks = new ConcurrentHashMap<>();
    private final Map<NPC, Double> movementLastDist = new ConcurrentHashMap<>();
    private final Map<NPC, Integer> movementStuck = new ConcurrentHashMap<>();

    private final Map<NPC, Integer> npcTargetHouse = new ConcurrentHashMap<>();
    private final Map<NPC, Integer> npcTargetSlot = new ConcurrentHashMap<>();
    private final Map<NPC, Player> npcOwner = new ConcurrentHashMap<>();

    private volatile List<BrainrotModel> cachedList = Collections.emptyList();
    private volatile Map<Rarity, List<BrainrotModel>> cachedByRarity = Collections.emptyMap();
    private volatile long cacheTimeMillis = 0;
    private static final long CACHE_TTL_MS = 5000;

    // updated constructor with Config
    public Puller(Plugin plugin, BrainrotRepo brainrotRepo, LobbyRepo lobbyRepo, Config config) {
        this.plugin = plugin;
        this.brainrotRepo = brainrotRepo;
        this.lobbyRepo = lobbyRepo;
        this.config = config;
    }

    public void forceNext(int lobby, BrainrotModel brainrotModel) {
        if (brainrotModel != null) forcedNext.put(lobby, brainrotModel);
    }

    public void forceNextAll(BrainrotModel model) {
        if (model == null) return;
        for (Lobby lb : lobbyRepo.getLobbies()) forcedNext.put(lb.getNum(), model);
    }

    public void startPull() {
        if (mainTask != null) return;

        monitorTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (walkingNpc.isEmpty()) return;
                for (NPC npc : new ArrayList<>(walkingNpc.keySet())) {
                    try {
                        if (npc == null || !npc.isSpawned()) {
                            walkingNpc.remove(npc);
                            continue;
                        }
                        if (!npc.getNavigator().isNavigating() && !movementTasks.containsKey(npc)) {
                            deleteNPC(npc);
                        }
                    } catch (Throwable ignored) {
                        deleteNPC(npc);
                    }
                }
            }
        };
        monitorTask.runTaskTimer(plugin, 10, 10);

        // earnings task adds earn per second to storedAmounts for home brainrots
        earningsTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    for (Lobby lobby : lobbyRepo.getLobbies()) {
                        for (House house : lobby.getHouses()) {
                            if (house.getPlayerData() == null) continue;

                            PlayerData pd = house.getPlayerData();
                            if (pd.getOwnBreinrots().isEmpty()) continue;

                            for (int i = 0; i < 10; i++) {
                                var pair = pd.getOwnBreinrots().size() > i ? pd.getOwnBreinrots().get(i) : null;
                                if (pair == null || pair.getLeft() == null) continue;

                                BrainrotModel model = pair.getLeft();
                                Modificator mod = pair.getRight() != null ? pair.getRight() : Modificator.BRONZE;

                                double earn = model.getEarn() * (mod == Modificator.BRONZE ? 1.0 : mod.getValue());
                                pd.addToStored(i, earn);

                                if (pd.getPlayer() != null)
                                    dirtyPlayers.add(pd.getPlayer());
                            }
                        }
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (Lobby lobby : lobbyRepo.getLobbies()) {
                            for (House house : lobby.getHouses()) {
                                if (house.getPlayerData() == null) continue;

                                PlayerData playerData = house.getPlayerData();

                                if(house.getPlayerData().getOwnBreinrots().isEmpty()) continue;

                                Map<Integer, NPC> existingBySlot = new HashMap<>();
                                for (NPC n : CitizensAPI.getNPCRegistry()) {
                                    try {
                                        if (n == null) continue;
                                        if (n.data().has("home") && n.data().get("home") != null && n.data().get("home").equals(playerData.getPlayer().getName())) {
                                            Object slotObj = n.data().get("home_slot");
                                            int slot = -1;
                                            try {
                                                if (slotObj instanceof Number) slot = ((Number) slotObj).intValue();
                                                else if (slotObj instanceof String) slot = Integer.parseInt((String) slotObj);
                                            } catch (Throwable ignored) {}
                                            if (slot >= 0 && slot < 10) existingBySlot.put(slot, n);
                                        }
                                    } catch (Throwable ignored) {}
                                }

                                List<MutablePair<BrainrotModel, Modificator>> owning = playerData.getOwnBreinrots();

                                for(int i = 0; i < owning.size() && i < 10; i++) {
                                    MutablePair<BrainrotModel, Modificator> p = owning.get(i);
                                    if (p == null) continue;

                                    BrainrotModel brainrot = p.getLeft();
                                    Modificator mod = p.getRight();

                                    NPC npc = existingBySlot.get(i);

                                    if (npc == null || !npc.isSpawned()) continue;

                                    double stored = playerData.getStoredAmount(i);
                                    String storedText = "&a$" + formatNumber(stored);

                                    int lobbyNum = -1;
                                    for (Lobby lb : lobbyRepo.getLobbies()){
                                        for (House h : lb.getHouses()){
                                            if (h.getId() == house.getId()) { lobbyNum = lb.getNum(); break; }
                                        }
                                        if (lobbyNum != -1) break;
                                    }
                                    if (lobbyNum == -1) continue;

                                    String holoName = lobbyNum + "_" + house.getId() + "_slot_" + i;
                                    Hologram holo = DHAPI.getHologram(holoName);
                                    try {
                                        if (holo == null) {
                                            Location holoLoc = getLocationForHologram(house, i);
                                            if (holoLoc == null) continue;
                                            holo = createHologram(holoLoc, holoName);
                                            if (holo != null) DHAPI.addHologramLine(holo, storedText);
                                        } else {
                                            DHAPI.setHologramLine(holo, 0, storedText);
                                        }
                                    } catch (Throwable e) {
                                        plugin.getLogger().warning("Ошибка при обновлении DH голограммы: " + e.getMessage());
                                    }

                                }
                            }
                        }
                    });
                } catch (Throwable ignored) {}
            }
        };
        earningsTask.runTaskTimer(plugin, 20L, 20L);

        saveFlushTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    List<Player> toSave;
                    synchronized (dirtyPlayers) {
                        if (dirtyPlayers.isEmpty()) return;
                        toSave = new ArrayList<>(dirtyPlayers);
                        dirtyPlayers.clear();
                    }
                    for (Player p : toSave) {
                        try { if (p != null && p.isOnline()) config.savePlayerData(p); }
                        catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
        };
        saveFlushTask.runTaskTimer(plugin, 20L, 20L);

        mainTask = new BukkitRunnable() {
            @Override
            public void run() {
                List<Lobby> lobbies = new ArrayList<>(lobbyRepo.getLobbies());
                if (lobbies.isEmpty()) return;
                refreshCacheIfNeeded();

                CommandSender console = Bukkit.getConsoleSender();
                for (Lobby lobby : lobbies) {
                    if (!hasPlayersInLobby(lobby)) continue;

                    BrainrotModel forced = forcedNext.remove(lobby.getNum());
                    BrainrotModel model = forced != null ? forced : getRandomBrainrotCached();
                    if (model == null) return;

                    Bukkit.getScheduler().runTask(plugin,
                            () -> createNpcSync(lobby, model, console));
                }
            }
        };
        mainTask.runTaskTimerAsynchronously(plugin, 60L, 60L);
    }

    private boolean hasPlayersInLobby(Lobby lobby) {
        try {
            if (lobby == null || lobby.getTeleportLoc() == null) return false;
            for (var p : Bukkit.getOnlinePlayers()) {
                try {
                    if (!p.getWorld().equals(lobby.getTeleportLoc().getWorld())) continue;
                    if (p.getLocation().distance(lobby.getTeleportLoc()) <= 100) return true;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public Location getLocationForHologram(House house, int slot) {
        if (house == null || house.getPlateCloseDoor() == null) return null;
        if(slot < 5) return getLocationByIdBrainrot(slot, house.getPlateCloseDoor(), house.isRight()).clone().add(-1.75, 0, 0);
        return getLocationByIdBrainrot(slot, house.getPlateCloseDoor(), house.isRight()).clone().add(1.75, 0, 0);
    }

    public void updateBalanceHologram(House house, int slot) {
        if (house == null) return;
        PlayerData pd = house.getPlayerData();
        if (pd == null) return;
        if (slot < 0 || slot >= 10) return;

        double stored = pd.getStoredAmount(slot);
        String storedText = "&a$" + formatNumber(stored);

        int lobbyNum = -1;
        for (Lobby lb : lobbyRepo.getLobbies()){
            for (House h : lb.getHouses()){
                if (h.getId() == house.getId()) { lobbyNum = lb.getNum(); break; }
            }
            if (lobbyNum != -1) break;
        }
        if (lobbyNum == -1) return;

        String holoName = lobbyNum + "_" + house.getId() + "_slot_" + slot;
        Location holoLoc = getLocationForHologram(house, slot);
        if (holoLoc == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Hologram holo = DHAPI.getHologram(holoName);
                if (holo == null) {
                    holo = createHologram(holoLoc, holoName);
                    if (holo != null) DHAPI.addHologramLine(holo, storedText);
                } else {
                    DHAPI.setHologramLine(holo, 0, storedText);
                }
            } catch (Throwable e) {
                plugin.getLogger().warning("Ошибка при обновлении DH голограммы: " + e.getMessage());
            }
        });
    }

    public void stopPull() {
        if (mainTask != null) mainTask.cancel();
        if (monitorTask != null) monitorTask.cancel();
        if (earningsTask != null) earningsTask.cancel();
        if (saveFlushTask != null) saveFlushTask.cancel();
        if (mainTask != null) mainTask.cancel();
        mainTask = null;
        monitorTask = null;
        earningsTask = null;
        saveFlushTask = null;

        if (Bukkit.isPrimaryThread()) {
            for (NPC npc : new ArrayList<>(walkingNpc.keySet())) deleteNPC(npc);
            walkingNpc.clear();
        } else {
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (NPC npc : new ArrayList<>(walkingNpc.keySet())) deleteNPC(npc);
                    walkingNpc.clear();
                });
            } else {
                walkingNpc.clear();
            }
        }
    }

    private void createNpcSync(Lobby lobby, BrainrotModel model, CommandSender console) {
        try {
            if (model == null) return;

            Location start = lobby.getTeleportLoc().clone();
            Location end = start.clone().add(145, 0, 0);

            String uuid = UUID.randomUUID().toString();
            NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, uuid);
            npc.setName("");
            npc.spawn(start);

            npc.data().setPersistent("persistent", false);

            npc.setUseMinecraftAI(false);
            npc.getNavigator().getLocalParameters()
                    .speedModifier(0.9f)
                    .stuckAction(null)
                    .distanceMargin(1.0)
                    .useNewPathfinder(true);
            npc.getNavigator().getDefaultParameters().range(200f);

            LookClose look = npc.getOrAddTrait(LookClose.class);
            look.lookClose(false);

            List<String> cmds = new ArrayList<>();
            cmds.add("npc select " + npc.getId());
            cmds.add("trait add meg_model");
            cmds.add("meg npc model citizens:" + npc.getId() + ":[" + uuid + "] add " + model.getId().toLowerCase());
            cmds.add("meg npc state citizens:" + npc.getId() + ":[" + uuid + "] " + model.getId().toLowerCase() + " add walk");

            cmds.add("npc hologram lineheight 0.25");
            cmds.add("npc hitbox --width " + model.getWidthHitbox() + " --height " + model.getHeightHitbox());
            if(model.getModificator() == Modificator.BRONZE) cmds.add("npc hologram add &a$" + formatNumber(model.getCost()));
            else cmds.add("npc hologram add &a$" + formatNumber(model.getCost() * model.getModificator().getValue()));
            if(model.getModificator() == Modificator.BRONZE) cmds.add("npc hologram add &e$" + formatNumber(model.getEarn()) + "/с");
            else cmds.add("npc hologram add &e$" + formatNumber(model.getEarn() * model.getModificator().getValue()) + "/с");
            cmds.add("npc hologram marginbottom 0 " + model.getMarginHologram());

            if (model.getRarity().equals(Rarity.BRAINROT_GOD)) {
                cmds.add("npc hologram add &#FF0000B&#FF4000r&#FF7F00a&#FFBF00i&#FFFF00n&#80FF00r&#00FF00o&#0000FFt &#4A00E9G&#6F00DEo&#9400D3d");
            }
            else {
                cmds.add("npc hologram add &f" + colorFromRarity(model.getRarity()) + capitalizeFirst(model.getRarity().toString()));
            }
            cmds.add("npc hologram add &f" + capitalizeFirst(model.getDisplayName()));

            if(model.getModificator() != Modificator.BRONZE) {
                if (model.getModificator() == Modificator.RAINBOW) {
                    cmds.add("npc hologram add &#FF0000R&#FF7F00a&#FFFF00i&#80FF00n&#00FF00b&#0000FFo&#4B0082w");
                } else if(model.getModificator() == Modificator.YIN_YANG) {
                    cmds.add("npc hologram add &#FFFFFFY&#ECECECi&#D8D8D8n&#C5C5C5g &#9E9E9EY&#8B8B8Ba&#777777n&#646464g");
                } else {
                    if(model.getModificator() == Modificator.GALAXY){
                        cmds.add("npc glowing");
                    }
                    cmds.add("npc hologram add &f" + colorFromModificator(model.getModificator()) + capitalizeFirst(model.getModificator() + ""));
                }
            }

            for (String c : cmds) {
                try {
                    Bukkit.dispatchCommand(console, c);
                } catch (Exception e) {
                    plugin.getLogger().warning("Ошибка при выполнении команды NPC: " + e.getMessage());
                }
            }

            walkingNpc.put(npc, new MutablePair<>(model, model.getModificator()));
            npc.getNavigator().getLocalParameters().pathDistanceMargin(3.0F);
            npc.getNavigator().getLocalParameters().stationaryTicks(40);
            npc.getNavigator().getLocalParameters().avoidWater(false);
            npc.getNavigator().getLocalParameters().range(200);
            npc.getNavigator().getLocalParameters().updatePathRate(40);
            npc.getNavigator().setTarget(end);

        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось создать NPC: " + e.getMessage());
        }
    }

    public void onNpcArrived(NPC npc, BrainrotModel brainrot, Modificator modificator, int houseId, int reservedSlot, Player player) {
        if(!player.isOnline()) return;

        House house = null;
        for(Lobby l : lobbyRepo.getLobbies()){
            for(House h : l.getHouses()){
                if(h.getPlayerData() == null) continue;
                if(h.getPlayerData().getPlayer().equals(player)){
                    house = h;
                }
            }
            if(house != null) break;
        }
        if(house == null) return;

        PlayerData pd = house.getPlayerData();
        if (reservedSlot >= 0 && reservedSlot < 10) {
            pd.addBrainrot(reservedSlot, brainrot, modificator);
        } else {
            int freeSlot = -1;
            for (int i = 0; i < 10; i++) {
                if (i >= pd.getOwnBreinrots().size() || pd.getOwnBreinrots().get(i) == null) {
                    freeSlot = i;
                    break;
                }
            }
            if (freeSlot != -1) pd.addBrainrot(freeSlot, brainrot, modificator);
            else plugin.getLogger().info("Нет свободного слота для бреинрота у игрока " + player.getName());
        }
        updateHomeBrainrots(house);

        try {
            if (plugin instanceof Brainrots) {
                Player savePlayer = pd.getPlayer();
                if (savePlayer != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            ((Brainrots) plugin).getConfigManager().savePlayerData(savePlayer);
                        } catch (Throwable ignored) {}
                    });
                }
            }
        } catch (Throwable ignored) {}

        Bukkit.broadcast(parse("NPC дошёл до дома " + houseId));
    }

    public Location getLocationByIdBrainrot(int num, Location loc, boolean isRight){
        Location location = switch(num){
            case 0 -> isRight ? loc.clone().add(6, 1, 19) : loc.clone().add(6, 1, -19);
            case 1 -> isRight ? loc.clone().add(6, 1, 15) : loc.clone().add(6, 1, -15);
            case 2 -> isRight ? loc.clone().add(6, 1, 11) : loc.clone().add(6, 1, -11);
            case 3 -> isRight ? loc.clone().add(6, 1, 7) : loc.clone().add(6, 1, -7);
            case 4 -> isRight ? loc.clone().add(6, 1, 3) : loc.clone().add(6, 1, -3);
            case 5 -> isRight ? loc.clone().add(-6, 1, 3) : loc.clone().add(-6, 1, -3);
            case 6 -> isRight ? loc.clone().add(-6, 1, 7) : loc.clone().add(-6, 1, -7);
            case 7 -> isRight ? loc.clone().add(-6, 1, 11) : loc.clone().add(-6, 1, -11);
            case 8 -> isRight ? loc.clone().add(-6, 1, 15) : loc.clone().add(-6, 1, -15);
            case 9 -> isRight ? loc.clone().add(-6, 1, 19) : loc.clone().add(-6, 1, -19);
            default -> null;
        };

        if(num < 5) location.setYaw(90);
        else location.setYaw(-90);
        return location;
    }

    public void updateHomeBrainrots(House house){
        if(house.getPlayerData() == null) return;

        PlayerData playerData = house.getPlayerData();
        if(playerData.getPlayer() == null) return;
        if(playerData.getOwnBreinrots().isEmpty()) return;

        try {
            String ply = playerData.getPlayer().getName();
            plugin.getLogger().info("updateHomeBrainrots: player=" + ply + " slots=" + playerData.getOwnBreinrots().size());
            for (int i = 0; i < playerData.getOwnBreinrots().size() && i < 10; i++) {
                var p = playerData.getOwnBreinrots().get(i);
                if (p == null) continue;
                if (p.getLeft() != null) plugin.getLogger().info("  slot=" + i + " id=" + p.getLeft().getId() + " mod=" + (p.getRight() != null ? p.getRight().name() : "BRONZE"));
            }
        } catch (Throwable ignored) {}

        Map<Integer, NPC> existingBySlot = new HashMap<>();
        for (NPC n : CitizensAPI.getNPCRegistry()) {
            try {
                if (n == null) continue;
                if (n.data().has("home") && n.data().get("home") != null && n.data().get("home").equals(playerData.getPlayer().getName())) {
                    Object slotObj = n.data().get("home_slot");
                    int slot = -1;
                    try {
                        if (slotObj instanceof Number) slot = ((Number) slotObj).intValue();
                        else if (slotObj instanceof String) slot = Integer.parseInt((String) slotObj);
                    } catch (Throwable ignored) {}
                    if (slot >= 0 && slot < 10) existingBySlot.put(slot, n);
                }
            } catch (Throwable ignored) {}
        }

        List<MutablePair<BrainrotModel, Modificator>> owning = playerData.getOwnBreinrots();

        for(int i = 0; i < owning.size() && i < 10; i++){
            MutablePair<BrainrotModel, Modificator> p = owning.get(i);
            if (p == null) continue;

            BrainrotModel brainrot = p.getLeft();
            Modificator mod = p.getRight();

            NPC npc = existingBySlot.get(i);

            double stored = playerData.getStoredAmount(i);

            if (npc == null || !npc.isSpawned()) {
                try {
                    Location loc = getLocationByIdBrainrot(i, house.getPlateCloseDoor(), house.isRight());

                    String uuid = UUID.randomUUID().toString();
                    npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, uuid);
                    npc.setName("");
                    npc.spawn(loc);

                    npc.data().set("home", playerData.getPlayer().getName());
                    npc.data().set("home_slot", i);

                    npc.setUseMinecraftAI(false);
                    LookClose look = npc.getOrAddTrait(LookClose.class);
                    look.lookClose(false);

                    List<String> cmds = new ArrayList<>();
                    cmds.add("npc select " + npc.getId());
                    cmds.add("trait add meg_model");
                    cmds.add("meg npc model citizens:" + npc.getId() + ":[" + uuid + "] add " + brainrot.getId().toLowerCase());

                    cmds.add("npc hologram lineheight 0.25");
                    cmds.add("npc hitbox --width " + brainrot.getWidthHitbox() + " --height " + brainrot.getHeightHitbox());
                    if(brainrot.getModificator() == Modificator.BRONZE) cmds.add("npc hologram add &c$" + formatNumber((double) brainrot.getCost() / 10));
                    else cmds.add("npc hologram add &c$" + formatNumber((brainrot.getCost() * brainrot.getModificator().getValue()) / 10));
                    if(brainrot.getModificator() == Modificator.BRONZE) cmds.add("npc hologram add &e$" + formatNumber(brainrot.getEarn()) + "/с");
                    else cmds.add("npc hologram add &e$" + formatNumber(brainrot.getEarn() * brainrot.getModificator().getValue()) + "/с");

                    if(brainrot.getId().equals("garammararam")) cmds.add("npc hologram marginbottom 0 1.5");
                    else cmds.add("npc hologram marginbottom 0 " + brainrot.getMarginHologram());

                    if (brainrot.getRarity().equals(Rarity.BRAINROT_GOD)) {
                        cmds.add("npc hologram add &#FF0000B&#FF4000r&#FF7F00a&#FFBF00i&#FFFF00n&#80FF00r&#00FF00o&#0000FFt &#4A00E9G&#6F00DEo&#9400D3d");
                    }
                    else {
                        cmds.add("npc hologram add &f" + colorFromRarity(brainrot.getRarity()) + capitalizeFirst(brainrot.getRarity().toString()));
                    }
                    cmds.add("npc hologram add &f" + capitalizeFirst(brainrot.getDisplayName()));

                    if(brainrot.getModificator() != Modificator.BRONZE) {
                        if (brainrot.getModificator() == Modificator.RAINBOW) {
                            cmds.add("npc hologram add &#FF0000R&#FF7F00a&#FFFF00i&#80FF00n&#00FF00b&#0000FFo&#4B0082w");
                        } else if(brainrot.getModificator() == Modificator.YIN_YANG) {
                            cmds.add("npc hologram add &#FFFFFFY&#ECECECi&#D8D8D8n&#C5C5C5g &#9E9E9EY&#8B8B8Ba&#777777n&#646464g");
                        } else {
                            if(brainrot.getModificator() == Modificator.GALAXY){
                                cmds.add("npc glowing");
                            }
                            cmds.add("npc hologram add &f" + colorFromModificator(brainrot.getModificator()) + capitalizeFirst(brainrot.getModificator() + ""));
                        }
                    }

                    for (String c : cmds) {
                        try {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
                        } catch (Exception e) {
                            plugin.getLogger().warning("Ошибка при выполнении команды NPC: " + e.getMessage());
                        }
                    }

                    // Создаём DecentHolograms для баланса сразу при создании NPC, чтобы не было задержки
                    try {
                        int lobbyNum = -1;
                        for (Lobby lb : lobbyRepo.getLobbies()){
                            for (House h : lb.getHouses()){
                                if (h.getId() == house.getId()) { lobbyNum = lb.getNum(); break; }
                            }
                            if (lobbyNum != -1) break;
                        }
                        if (lobbyNum != -1) {
                            String holoName = lobbyNum + "_" + house.getId() + "_slot_" + i;
                            Location holoLoc = getLocationForHologram(house, i);
                            if (holoLoc != null) {
                                Hologram slotH = DHAPI.getHologram(holoName);
                                if (slotH == null) {
                                    slotH = createHologram(holoLoc, holoName);
                                    if (slotH != null) DHAPI.addHologramLine(slotH, "&a$" + formatNumber(stored));
                                } else {
                                    DHAPI.setHologramLine(slotH, 0, "&a$" + formatNumber(stored));
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                } catch (Throwable e) {
                    plugin.getLogger().warning("Не удалось создать NPC для дома: " + e.getMessage());
                    continue;
                }
            }

            try {
                Location holoLoc = getLocationForHologram(house, i);
                try {
                    int lobbyNum = -1;
                    for (Lobby lb : lobbyRepo.getLobbies()){
                        for (House h : lb.getHouses()){
                            if (h.getId() == house.getId()) { lobbyNum = lb.getNum(); break; }
                        }
                        if (lobbyNum != -1) break;
                    }
                    if (lobbyNum != -1 && holoLoc != null) {
                        String holoName = lobbyNum + "_" + house.getId() + "_slot_" + i;
                        Hologram slotH = DHAPI.getHologram(holoName);
                        if (slotH == null) {
                            slotH = createHologram(holoLoc, holoName);
                            if (slotH != null) DHAPI.addHologramLine(slotH, "&a$" + formatNumber(stored));
                        } else {
                            DHAPI.setHologramLine(slotH, 0, "&a$" + formatNumber(stored));
                        }
                    }
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}
        }

        for (Map.Entry<Integer, NPC> e : existingBySlot.entrySet()) {
            int slot = e.getKey();
            NPC n = e.getValue();
            boolean has = slot < owning.size() && owning.get(slot) != null && owning.get(slot).getLeft() != null;
            if (!has) {
                try { deleteNPC(n); } catch (Throwable ignored) {}
            }
        }
    }

    private void deleteNPC(NPC npc) {
        if (npc == null) return;

        try {
            Object home = null;
            Object slotObj = null;
            try { home = npc.data().get("home"); } catch (Throwable ignored) {}
            try { slotObj = npc.data().get("home_slot"); } catch (Throwable ignored) {}
            if (home != null && slotObj != null) {
                int slot = -1;
                try {
                    if (slotObj instanceof Number) slot = ((Number) slotObj).intValue();
                    else if (slotObj instanceof String) slot = Integer.parseInt((String) slotObj);
                } catch (Throwable ignored) {}

                if (slot >= 0) {
                    final int fslot = slot;
                    final String ownerName = String.valueOf(home);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            for (Lobby lb : lobbyRepo.getLobbies()){
                                for (House h : lb.getHouses()){
                                    if (h.getPlayerData() == null) continue;
                                    if (ownerName.equals(h.getPlayerData().getPlayer().getName())) {
                                        // если совпадает владелец - удалим голограмму для слота
                                        String holoName = lb.getNum() + "_" + h.getId() + "_slot_" + fslot;
                                        try {
                                            Hologram slotH = DHAPI.getHologram(holoName);
                                            if (slotH != null) {
                                                slotH.delete();
                                                slotH.destroy();
                                            }
                                        } catch (Throwable ignored) {}
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                    });
                }
            }
        } catch (Throwable ignored) {}

        try {
            BukkitRunnable t = movementTasks.remove(npc);
            if (t != null) t.cancel();
        } catch (Throwable ignored) {}
        movementLastDist.remove(npc);
        movementStuck.remove(npc);

        try { npc.despawn(DespawnReason.REMOVAL); } catch (Throwable ignored) {}
        try { CitizensAPI.getNPCRegistry().deregister(npc); } catch (Throwable ignored) {}
        try { npc.destroy(); } catch (Throwable ignored) {}
        walkingNpc.remove(npc);
    }

    public boolean isNpcGoingToHouse(NPC npc, int houseId) {
        return npcTargetHouse.getOrDefault(npc, -1) == houseId;
    }

    public void moveNpcSegmented(NPC npc, BrainrotModel brainrot, Modificator modificator, Location finalEnd, int houseId, int reservedSlot, Player player) {
        if (npc == null || !npc.isSpawned()) return;
        npcTargetHouse.put(npc, houseId);
        npcTargetSlot.put(npc, reservedSlot);
        npcOwner.put(npc, player);

        double segment = 10;      // длина каждого рывка
        double advance = 7;       // после скольких блоков обновлять цель

        Location current = npc.getStoredLocation();
        if (current == null) return;

        Location next = pointTowards(current, finalEnd, segment);
        npc.getNavigator().setTarget(next);

        BukkitRunnable task = new BukkitRunnable() {
            Location lastPoint = next;

            @Override
            public void run() {
                try {
                    if (!npc.isSpawned()) {
                        cancel();
                        movementTasks.remove(npc);
                        Integer slot = npcTargetSlot.remove(npc);
                        Player owner = npcOwner.remove(npc);
                        Integer hid = npcTargetHouse.remove(npc);
                        if (slot != null && owner != null && hid != null) {
                            for (Lobby l : lobbyRepo.getLobbies()){
                                for (House h : l.getHouses()){
                                    if (h.getId() == hid && h.getPlayerData() != null && h.getPlayerData().getPlayer().equals(owner)){
                                        h.getPlayerData().removeBrainrot(slot);
                                    }
                                }
                            }
                        }
                        return;
                    }

                    Location cur = npc.getStoredLocation();
                    if (cur == null) return;

                    if (cur.getWorld() == null || !cur.getWorld().equals(finalEnd.getWorld())) {
                        return;
                    }

                    double distToFinal = cur.distance(finalEnd);
                    if (distToFinal < 3.5) {
                        if(!walkingNpc.containsKey(npc)) return;
                        int hid = npcTargetHouse.getOrDefault(npc, houseId);
                        int reserved = npcTargetSlot.getOrDefault(npc, -1);

                        movementTasks.remove(npc);
                        walkingNpc.remove(npc);
                        npcTargetHouse.remove(npc);
                        npcTargetSlot.remove(npc);
                        Player owner = npcOwner.remove(npc);
                        movementLastDist.remove(npc);
                        movementStuck.remove(npc);

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                if (npc.isSpawned()) npc.despawn(DespawnReason.REMOVAL);
                                CitizensAPI.getNPCRegistry().deregister(npc);
                                npc.destroy();
                            } catch (Throwable ignored) {}
                            onNpcArrived(npc, brainrot, modificator, hid, reserved, owner != null ? owner : player);
                        });

                        cancel();
                        return;
                    }

                    double distToLast = cur.distance(lastPoint);

                    if (distToLast <= (segment - advance)) {
                        Location newPoint = pointTowards(cur, finalEnd, segment);
                        lastPoint = newPoint;
                        npc.getNavigator().setTarget(newPoint);
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Ошибка в задаче перемещения NPC: " + t.getMessage());
                    try { cancel(); } catch (Throwable ignored) {}
                    movementTasks.remove(npc);
                }
            }
        };
        movementTasks.put(npc, task);
        task.runTaskTimer(plugin, 0, 4);
    }

    private Location pointTowards(Location from, Location to, double d) {
        Vector v = to.toVector().subtract(from.toVector());
        if (v.length() < d) return to.clone();
        v.normalize().multiply(d);
        return from.clone().add(v);
    }

    private void refreshCacheIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - cacheTimeMillis < CACHE_TTL_MS && !cachedList.isEmpty()) return;

        List<BrainrotModel> list = new ArrayList<>(brainrotRepo.getBrainrots());
        if (list.isEmpty()) {
            cachedList = Collections.emptyList();
            cachedByRarity = Collections.emptyMap();
            cacheTimeMillis = now;
            return;
        }

        cachedList = List.copyOf(list);
        cachedByRarity = cachedList.stream()
                .collect(Collectors.groupingBy(
                        BrainrotModel::getRarity,
                        Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList)
                ));
        cacheTimeMillis = now;
    }

    private BrainrotModel getRandomBrainrotCached() {
        if (cachedList.isEmpty()) return null;

        Map<Rarity, List<BrainrotModel>> byRarity = cachedByRarity;
        if (byRarity.isEmpty())
            return cachedList.get(ThreadLocalRandom.current().nextInt(cachedList.size()));

        double total = 0;
        Map<Rarity, Double> weight = new EnumMap<>(Rarity.class);

        for (var e : byRarity.entrySet()) {
            double w = e.getKey().getValue() * e.getValue().size();
            weight.put(e.getKey(), w);
            total += w;
        }

        if (total <= 0)
            return cachedList.get(ThreadLocalRandom.current().nextInt(cachedList.size()));

        double roll = ThreadLocalRandom.current().nextDouble() * total;
        double cur = 0;

        Rarity chosen = null;
        for (var e : weight.entrySet()) {
            cur += e.getValue();
            if (roll <= cur) {
                chosen = e.getKey();
                break;
            }
        }
        if (chosen == null) chosen = cachedList.getFirst().getRarity();
        Modificator chosenMod = null;

        double totalMod = 0;
        for (Modificator m : Modificator.values()) {
            totalMod += m.getChance();
        }

        double rollMod = ThreadLocalRandom.current().nextDouble() * totalMod;
        double curMod = 0;

        for (Modificator m : Modificator.values()) {
            curMod += m.getChance();
            if (rollMod <= curMod) {
                chosenMod = m;
                break;
            }
        }

        if (chosenMod == null) chosenMod = Modificator.BRONZE;

        List<BrainrotModel> list = byRarity.get(chosen);
        BrainrotModel model = list.get(ThreadLocalRandom.current().nextInt(list.size()));

        model.setModificator(chosenMod);

        return model;
    }

    public boolean isSlotReserved(Player player, int slot, int houseId) {
        if (player == null) return false;
        for (Map.Entry<NPC, Integer> e : npcTargetSlot.entrySet()) {
            NPC n = e.getKey();
            Integer s = e.getValue();
            try {
                if (s == null || s != slot) continue;
                Player owner = npcOwner.get(n);
                Integer hid = npcTargetHouse.get(n);
                if (owner != null && owner.equals(player) && hid != null && hid == houseId) return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public int countReservedSlots(Player player, int houseId) {
        if (player == null) return 0;
        int c = 0;
        for (Map.Entry<NPC, Integer> e : npcTargetSlot.entrySet()) {
            NPC n = e.getKey();
            Integer s = e.getValue();
            try {
                Player owner = npcOwner.get(n);
                Integer hid = npcTargetHouse.get(n);
                if (owner != null && owner.equals(player) && hid != null && hid == houseId && s != null) c++;
            } catch (Throwable ignored) {}
        }
        return c;
    }

    public void reserveSlot(NPC npc, Player owner, int houseId, int slot) {
        if (npc == null || owner == null) return;
        npcTargetHouse.put(npc, houseId);
        npcTargetSlot.put(npc, slot);
        npcOwner.put(npc, owner);
    }

    public void unreserveSlot(NPC npc) {
        if (npc == null) return;
        npcTargetSlot.remove(npc);
        npcOwner.remove(npc);
        npcTargetHouse.remove(npc);
    }

    public void removeHomeNpcs(String playerName) {
        if (playerName == null || playerName.isEmpty()) return;
        try {
            List<NPC> toRemove = new ArrayList<>();
            for (NPC n : CitizensAPI.getNPCRegistry()) {
                try {
                    if (n == null) continue;
                    if (n.data().has("home")) {
                        Object home = n.data().get("home");
                        if (home != null && playerName.equals(String.valueOf(home))) toRemove.add(n);
                    }
                } catch (Throwable ignored) {}
            }
            for (NPC n : toRemove) {
                try { deleteNPC(n); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }
}
