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
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.civworld.brainrots.model.BrainrotModel;
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
            npc.getNavigator().setTarget(end);

        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось создать NPC: " + e.getMessage());
        }
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

    private void clearMovementState(NPC npc){
        if (npc == null) return;
        movementTasks.remove(npc);
        movementLastDist.remove(npc);
        movementStuck.remove(npc);
    }

    public void moveNpcSmooth(NPC npc, Location end, double step) {
        if (npc == null || end == null) return;
        try {
            if (!npc.isSpawned()) return;

            // cancel previous movement for this npc
            BukkitRunnable prev = movementTasks.remove(npc);
            if (prev != null) prev.cancel();
            // сбрасываем состояние застревания при старте нового перемещения
            movementLastDist.remove(npc);
            movementStuck.put(npc, 0);

            // ensure navigator parameters are reasonable for smooth movement
            // используем навигатор Citizens (Minecraft AI отключён) — часто стабильнее для NPC-путей
            try { npc.setUseMinecraftAI(false); } catch (Throwable ignored) {}
            npc.getNavigator().getLocalParameters()
                    .speedModifier(0.9f)
                    .distanceMargin(0.45)
                    .useNewPathfinder(true)
                    .stuckAction(null);

            World world = end.getWorld();
            double stepDistance = Math.max(0.5, step); // немного больше по умолчанию

            plugin.getLogger().info("[Brainrots] NPC " + npc.getId() + " moveNpcSmooth start -> target=" + end.getBlockX() + "," + end.getBlockY() + "," + end.getBlockZ());
            // даём навигатору сразу полную цель, он попытается пройти путь сам
            try { npc.getNavigator().setTarget(end); } catch (Throwable ignored) {}

            // задача мониторинга прогресса: запускается каждые 4 тика
            BukkitRunnable task = new BukkitRunnable() {
                int checks = 0;

                @Override
                public void run() {
                    try {
                        if (!npc.isSpawned()) {
                            this.cancel();
                            movementTasks.remove(npc);
                            return;
                        }

                        Location cur = npc.getStoredLocation();
                        if (cur == null && npc.getEntity() != null) cur = npc.getEntity().getLocation();
                        if (cur == null) {
                            this.cancel();
                            movementTasks.remove(npc);
                            return;
                        }

                        if (!cur.getWorld().equals(world)) {
                            this.cancel();
                            movementTasks.remove(npc);
                            return;
                        }

                        double dx = end.getX() - cur.getX();
                        double dy = end.getY() - cur.getY();
                        double dz = end.getZ() - cur.getZ();
                        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);

                        // если близко к цели — завершаем
                        if (dist <= Math.max(0.7, stepDistance)) {
                            try { npc.getNavigator().setTarget(end); } catch (Throwable ignored) {}
                            this.cancel();
                            movementTasks.remove(npc);
                            movementLastDist.remove(npc);
                            movementStuck.remove(npc);
                            return;
                        }

                        // детектируем застой: проверяем каждые 3 проверок для сглаживания
                        Double prev = movementLastDist.get(npc);
                        if (prev == null) {
                            movementLastDist.put(npc, dist);
                        } else {
                            double delta = prev - dist;
                            if (delta < 0.02) {
                                movementStuck.put(npc, movementStuck.getOrDefault(npc, 0) + 1);
                            } else {
                                movementStuck.put(npc, 0);
                            }
                            movementLastDist.put(npc, dist);
                        }

                        checks++;
                        int stuck = movementStuck.getOrDefault(npc, 0);
                        if (checks % 8 == 0) {
                            // периодическая отладочная информация
                            plugin.getLogger().info("[Brainrots] NPC " + npc.getId() + " pos=" + cur.getBlockX() + "," + cur.getBlockY() + "," + cur.getBlockZ() + " dist=" + String.format("%.2f", dist) + " navigating=" + npc.getNavigator().isNavigating());
                        }
                        if (stuck > 60 && checks % 4 == 0) {
                             // терпеливая попытка: ставим промежуточную цель дальше по линии к конечной
                             double forward = Math.min(5.0, stepDistance * 4);
                             double factor = Math.min(forward / dist, 0.9);
                             // не ставим промежуточную цель, если она слишком близка
                             if (factor * dist < 1.0) {
                                 // слишком малое смещение, пропускаем
                             } else {
                              double ix = cur.getX() + dx * factor;
                              double iy = cur.getY() + dy * factor;
                              double iz = cur.getZ() + dz * factor;
                              Location intermediate = new Location(world, ix, iy, iz);
                              try {
                                  // временно ставим промежуточную цель
                                 npc.getNavigator().setTarget(intermediate);
                                 plugin.getLogger().info("[Brainrots] NPC " + npc.getId() + " setting intermediate target -> "+ intermediate.getBlockX()+","+intermediate.getBlockY()+","+intermediate.getBlockZ());
                              } catch (Throwable ignored) {}
                             }
                         } else if (checks % 8 == 0) {
                             // периодически убеждаемся, что финальная цель установлена
                             try { npc.getNavigator().setTarget(end); } catch (Throwable ignored) {}
                         }
                        // если длительно застрял — переключаемся в режим постепенной телепортации по направлению
                        if (stuck > 120) {
                            plugin.getLogger().warning("[Brainrots] NPC " + npc.getId() + " switching to teleport fallback due to stuck=" + stuck);
                            // отменяем текущую мониторзадачу
                            this.cancel();
                            movementTasks.remove(npc);

                            // запускаем задачу постепенной телепортации
                            double teleportStep = Math.max(0.25, stepDistance / 2.0);
                            BukkitRunnable teleportTask = new BukkitRunnable() {
                                @Override
                                public void run() {
                                    try {
                                        if (!npc.isSpawned()) { this.cancel(); movementTasks.remove(npc); return; }
                                        Location cur2 = npc.getStoredLocation();
                                        if (cur2 == null && npc.getEntity() != null) cur2 = npc.getEntity().getLocation();
                                        if (cur2 == null) { this.cancel(); movementTasks.remove(npc); return; }
                                        double dx2 = end.getX() - cur2.getX();
                                        double dy2 = end.getY() - cur2.getY();
                                        double dz2 = end.getZ() - cur2.getZ();
                                        double dist2 = Math.sqrt(dx2*dx2 + dy2*dy2 + dz2*dz2);
                                        if (dist2 <= 0.7) { try{ npc.getNavigator().setTarget(end);}catch(Throwable ignored){} this.cancel(); movementTasks.remove(npc); movementLastDist.remove(npc); movementStuck.remove(npc); return; }
                                        double ratio2 = teleportStep / dist2;
                                        double nx2 = cur2.getX() + dx2 * ratio2;
                                        double ny2 = cur2.getY() + dy2 * ratio2;
                                        double nz2 = cur2.getZ() + dz2 * ratio2;
                                        if (npc.getEntity() != null) {
                                            npc.getEntity().teleport(new Location(cur2.getWorld(), nx2, ny2, nz2, cur2.getYaw(), cur2.getPitch()));
                                        }
                                    } catch (Throwable e) {
                                        this.cancel(); movementTasks.remove(npc);
                                    }
                                }
                            };
                            movementTasks.put(npc, teleportTask);
                            teleportTask.runTaskTimer(plugin, 0L, 1L);
                            return;
                        }

                    } catch (Throwable t) {
                        this.cancel();
                        movementTasks.remove(npc);
                    }
                }
            };

            movementTasks.put(npc, task);
            // run every 4 ticks
            task.runTaskTimer(plugin, 0L, 4L);

         } catch (Throwable ignored) {}
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
