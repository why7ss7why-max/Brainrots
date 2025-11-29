package org.civworld.brainrots.listener;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.civworld.brainrots.model.House;
import org.civworld.brainrots.model.Lobby;
import org.civworld.brainrots.repo.LobbyRepo;

import static org.civworld.brainrots.util.Utils.createHologram;
import static org.civworld.brainrots.util.Utils.parse;

public class PlateListener implements Listener {
    private final LobbyRepo lobbyRepo;
    private final Plugin plugin;

    public PlateListener(LobbyRepo lobbyRepo, Plugin plugin){
        this.lobbyRepo = lobbyRepo;
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();

        if (event.getFrom().getBlockX() == to.getBlockX() &&
                event.getFrom().getBlockY() == to.getBlockY() &&
                event.getFrom().getBlockZ() == to.getBlockZ()) {
            return;
        }

        for (Lobby lobby : lobbyRepo.getLobbies()) {
            for (House house : lobby.getHouses()) {
                if (!house.isClosed()) continue;
                if (house.getPlayerData() == null || house.getPlayerData().getPlayer() == null) continue;
                if (house.getPlayerData().getPlayer().equals(player)) continue;

                Block plate = house.getPlateCloseDoor().getBlock();

                Location pos1 = house.isRight()
                        ? plate.getLocation().clone().add(-6.5, 0, 23.5)
                        : plate.getLocation().clone().add(-6.5, 0, -22.5);
                Location pos2 = house.isRight()
                        ? plate.getLocation().clone().add(8, 9, 23.5)
                        : plate.getLocation().clone().add(8, 9, -22.5);

                if (!isInside(to, pos1, pos2)) continue;

                Location from = event.getFrom();
                double dx = from.getX() - to.getX();
                double dz = from.getZ() - to.getZ();

                double length = Math.sqrt(dx * dx + dz * dz);
                if (length < 0.01) {
                    dx = from.getX() - plate.getX();
                    dz = from.getZ() - plate.getZ();
                    length = Math.sqrt(dx * dx + dz * dz);
                    if (length < 0.5) {
                        dx = 0; dz = house.isRight() ? -1 : 1;
                    }
                }

                if (length > 0) {
                    dx /= length;
                    dz /= length;
                }

                Location safe = to.clone().subtract(dx * 0.7, 0, dz * 0.7);

                if (isInside(safe, pos1, pos2)) {
                    double centerX = plate.getX() + 0.5;
                    double centerZ = plate.getZ() + 0.5;
                    double outX = to.getX() - centerX;
                    double outZ = to.getZ() - centerZ;
                    double outLen = Math.sqrt(outX * outX + outZ * outZ);
                    if (outLen > 0.1) {
                        outX /= outLen;
                        outZ /= outLen;
                        safe = to.clone().add(outX * 2.0, 0, outZ * 2.0);
                    } else {
                        safe = plate.getLocation().clone().add(house.isRight() ? -2 : 2, 0, 0);
                    }
                }

                player.teleport(safe);
                player.sendMessage(parse("<prefix>Эта дверь <red>закрыта<white>!"));
                player.spawnParticle(Particle.CLOUD, player.getLocation(), 20, 0.3, 0.5, 0.3, 0.05);

                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPressurePlate(PlayerInteractEvent event){
        Player player = event.getPlayer();
        if(event.getAction() != Action.PHYSICAL) return;
        if (event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        Lobby lobby = null;
        House house = null;

        for(Lobby l : lobbyRepo.getLobbies()){
            for(House h : l.getHouses()){
                if (h.getPlateCloseDoor().getBlock().equals(block)) {
                    house = h;
                    lobby = l;
                    break;
                }
            }
            if(house != null) break;
        }
        if(house == null) return;

        if(house.getPlayerData() == null) return;
        if(house.getPlayerData().getPlayer() == null) return;

        if(house.getPlayerData().getPlayer() != player) {
            player.sendMessage(parse("<prefix>Вы <red>не владелец <white>этой <blue>базы<white>!"));
            return;
        }

        if(house.isClosed()){
            player.sendMessage(parse("<prefix>Дверь <red>уже закрыта<white>!"));
            return;
        }

        player.sendMessage(parse("<prefix>Вы <green>закрыли <white>дверь на <blue>45 секунд<white>!"));
        house.setClosed(true);

        Hologram hologram = createHologram(house.getPlateCloseDoor().clone().add(0, 1, 0), lobby.getNum() + "_" + house.getId() + "_plate");
        DHAPI.addHologramLine(hologram, "Дверь <red>откроется <white>через: <blue>45 сек.");

        player.teleport(house.isRight() ? player.getLocation().clone().add(0, 0, 1) : player.getLocation().clone().add(0, 0, -1));

        World world = block.getWorld();
        Location pos1 = house.isRight() ? block.getLocation().clone().add(-6.5, 0, 23.5) : block.getLocation().clone().add(-6.5, 0, -22.5);
        Location pos2 = house.isRight() ? block.getLocation().clone().add(8, 9, 23.5) : block.getLocation().clone().add(8, 9, -22.5);

        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());
        double minY = pos1.getY();
        double maxY = pos2.getY();
        double midY = (minY + maxY) / 2;

        final double stepX = 1;
        final double stepZ = 1;
        int totalTicks = 45 * 10;

        House finalHouse = house;

        int[] time = {45};

        new BukkitRunnable() {
            @Override
            public void run() {
                time[0]--;
                if(time[0] <= 1){
                    DHAPI.setHologramLine(hologram, 0, "Дверь <green>открыта");
                    hologram.updateAll();
                    cancel();
                    return;
                }

                DHAPI.setHologramLine(hologram, 0, "Дверь <red>откроется <white>через: <blue>" + time[0] + " сек.");
                hologram.updateAll();
            }
        }.runTaskTimer(plugin, 20L, 20L);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                tick++;
                double progress = (tick % 20) / 20.0;

                for(double x = minX; x <= maxX; x += stepX){
                    for(double z = minZ; z <= maxZ; z += stepZ){
                        for(double y = minY; y <= midY; y += 0.5){
                            world.spawnParticle(Particle.DUST, x, y, z, 1, new Particle.DustOptions(Color.RED, 1));
                        }
                        for(double y = maxY; y >= midY; y -= 0.5){
                            world.spawnParticle(Particle.DUST, x, y, z, 1, new Particle.DustOptions(Color.RED, 1));
                        }

                        double animBottomY = minY + (midY - minY) * progress;
                        double animTopY = maxY - (maxY - midY) * progress;

                        world.spawnParticle(Particle.DUST, x, animBottomY, z, 1, new Particle.DustOptions(Color.ORANGE, 1));
                        world.spawnParticle(Particle.DUST, x, animTopY, z, 1, new Particle.DustOptions(Color.ORANGE, 1));
                    }
                }

                if(tick >= totalTicks) {
                    cancel();
                    finalHouse.setClosed(false);
                }
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    public boolean isInside(Location loc, Location pos1, Location pos2) {
        double eps = 0.5;
        double minX = Math.min(pos1.getX(), pos2.getX()) - eps;
        double maxX = Math.max(pos1.getX(), pos2.getX()) + eps;
        double minY = Math.min(pos1.getY(), pos2.getY()) - eps;
        double maxY = Math.max(pos1.getY(), pos2.getY()) + eps;
        double minZ = Math.min(pos1.getZ(), pos2.getZ()) - eps;
        double maxZ = Math.max(pos1.getZ(), pos2.getZ()) + eps;

        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();

        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }
}