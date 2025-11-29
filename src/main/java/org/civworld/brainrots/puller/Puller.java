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
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.civworld.brainrots.Brainrots;
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

public class Puller {

    private final Plugin plugin;
    private final BrainrotRepo brainrotRepo;
    private final LobbyRepo lobbyRepo;

    private BukkitRunnable mainTask = null;
    private BukkitRunnable monitorTask = null;

    @Getter private final Map<NPC, Pair<BrainrotModel, Modificator>> walkingNpc = new ConcurrentHashMap<>();
    private final Map<Integer, BrainrotModel> forcedNext = new ConcurrentHashMap<>();
    private final Map<NPC, BukkitRunnable> movementTasks = new ConcurrentHashMap<>();
    private final Map<NPC, Double> movementLastDist = new ConcurrentHashMap<>();
    private final Map<NPC, Integer> movementStuck = new ConcurrentHashMap<>();

    private final Map<NPC, Integer> npcTargetHouse = new ConcurrentHashMap<>();

    private volatile List<BrainrotModel> cachedList = Collections.emptyList();
    private volatile Map<Rarity, List<BrainrotModel>> cachedByRarity = Collections.emptyMap();
    private volatile long cacheTimeMillis = 0;
    private static final long CACHE_TTL_MS = 5000;

    public Puller(Plugin plugin, BrainrotRepo brainrotRepo, LobbyRepo lobbyRepo) {
        this.plugin = plugin;
        this.brainrotRepo = brainrotRepo;
        this.lobbyRepo = lobbyRepo;
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
                        // не удаляем NPC, если для него запущена задача плавного перемещения
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

        mainTask = new BukkitRunnable() {
            @Override
            public void run() {
                List<Lobby> lobbies = new ArrayList<>(lobbyRepo.getLobbies());
                if (lobbies.isEmpty()) return;
                refreshCacheIfNeeded();

                CommandSender console = Bukkit.getConsoleSender();
                for (Lobby lobby : lobbies) {
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

    public void stopPull() {
        if (mainTask != null) mainTask.cancel();
        if (monitorTask != null) monitorTask.cancel();
        mainTask = null;
        monitorTask = null;

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

    public void onNpcArrived(NPC npc, BrainrotModel brainrot, Modificator modificator, int houseId, Player player) {
        if(!player.isOnline()) return;

        House house = null;
        for(Lobby l : lobbyRepo.getLobbies()){
            for(House h : l.getHouses()){
                if(h.getPlayerData() == null) continue;
                if(h.getPlayerData().getPlayer().equals(player)){
                    house = h;
                }
                break;
            }
            if(house != null) break;
        }
        if(house == null) return;

        house.getPlayerData().getOwnBreinrots().add(new MutablePair<>(brainrot, modificator));
        updateHomeBrainrots(house);

        Bukkit.broadcast(parse("NPC дошёл до дома " + houseId));
    }

    public void updateHomeBrainrots(House house){
        if(house.getPlayerData() == null) return;

        PlayerData playerData = house.getPlayerData();
        if(playerData.getPlayer() == null) return;
        if(playerData.getOwnBreinrots().isEmpty()) return;

        List<NPC> toRemove = walkingNpc.keySet().stream()
                .filter(npc -> npc.data().has("home") && npc.data().get("home").equals(house.getPlayerData().getPlayer().getUniqueId()))
                .toList();
        toRemove.forEach(this::deleteNPC);

        List<MutablePair<BrainrotModel, Modificator>> owning = playerData.getOwnBreinrots();

        for(int i = 0; i < owning.size() && i < 10; i++){
            MutablePair<BrainrotModel, Modificator> p = owning.get(i);
            if (p == null) continue;

            BrainrotModel brainrot = p.getLeft();
            Modificator mod = p.getRight();

            Location loc = getLocationByIdBrainrot(i, house.getPlateCloseDoor(), house.isRight());

            String uuid = UUID.randomUUID().toString();
            NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, uuid);
            npc.setName("");
            npc.spawn(loc);

            npc.data().set("home", playerData.getPlayer().getUniqueId());

            npc.setUseMinecraftAI(false);
            LookClose look = npc.getOrAddTrait(LookClose.class);
            look.lookClose(false);

            List<String> cmds = new ArrayList<>();
            cmds.add("npc select " + npc.getId());
            cmds.add("trait add meg_model");
            cmds.add("meg npc model citizens:" + npc.getId() + ":[" + uuid + "] add " + brainrot.getId().toLowerCase());

            cmds.add("npc hologram lineheight 0.25");
            cmds.add("npc hitbox --width " + brainrot.getWidthHitbox() + " --height " + brainrot.getHeightHitbox());
            if(mod == Modificator.BRONZE) cmds.add("npc hologram add &a$" + formatNumber(brainrot.getCost()));
            else cmds.add("npc hologram add &a$" + formatNumber(brainrot.getCost() * mod.getValue()));
            if(mod == Modificator.BRONZE) cmds.add("npc hologram add &e$" + formatNumber(brainrot.getEarn()) + "/с");
            else cmds.add("npc hologram add &e$" + formatNumber(brainrot.getEarn() * mod.getValue()) + "/с");
            cmds.add("npc hologram marginbottom 0 " + brainrot.getMarginHologram());

            if (brainrot.getRarity().equals(Rarity.BRAINROT_GOD)) {
                cmds.add("npc hologram add &#FF0000B&#FF4000r&#FF7F00a&#FFBF00i&#FFFF00n&#80FF00r&#00FF00o&#0000FFt &#4A00E9G&#6F00DEo&#9400D3d");
            } else {
                cmds.add("npc hologram add &f" + colorFromRarity(brainrot.getRarity()) + capitalizeFirst(brainrot.getRarity().toString()));
            }
            cmds.add("npc hologram add &f" + capitalizeFirst(brainrot.getDisplayName()));

            if(mod != Modificator.BRONZE) {
                if(mod == Modificator.RAINBOW){
                    cmds.add("npc hologram add &#FF0000R&#FF7F00a&#FFFF00i&#80FF00n&#00FF00b&#0000FFo&#4B0082w");
                } else if(mod == Modificator.YIN_YANG){
                    cmds.add("npc hologram add &#FFFFFFY&#ECECECi&#D8D8D8n&#C5C5C5g &#9E9E9EY&#8B8B8Ba&#777777n&#646464g");
                } else {
                    if(mod == Modificator.GALAXY) cmds.add("npc glowing");
                    cmds.add("npc hologram add &f" + colorFromModificator(mod) + capitalizeFirst(mod + ""));
                }
            }

            for (String c : cmds) {
                try { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c); } catch (Exception e) { plugin.getLogger().warning("Ошибка при выполнении команды NPC: " + e.getMessage()); }
            }

            walkingNpc.put(npc, new MutablePair<>(brainrot, mod));
        }
    }

    public Location getLocationByIdBrainrot(int num, Location loc, boolean isRight){
        return switch(num){
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
    }

    private void deleteNPC(NPC npc) {
        if (npc == null) return;

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

    public void moveNpcSegmented(NPC npc, BrainrotModel brainrot, Modificator modificator, Location finalEnd, int houseId, Player player) {
        if (npc == null || !npc.isSpawned()) return;
        npcTargetHouse.put(npc, houseId);

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

                        movementTasks.remove(npc);
                        walkingNpc.remove(npc);
                        npcTargetHouse.remove(npc);
                        movementLastDist.remove(npc);
                        movementStuck.remove(npc);

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                if (npc.isSpawned()) npc.despawn(DespawnReason.REMOVAL);
                                CitizensAPI.getNPCRegistry().deregister(npc);
                                npc.destroy();
                            } catch (Throwable ignored) {}
                            onNpcArrived(npc, brainrot, modificator, hid, player);
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
}