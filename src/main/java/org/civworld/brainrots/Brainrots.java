package org.civworld.brainrots;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.DespawnReason;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.civworld.brainrots.command.BrainrotCommand;
import org.civworld.brainrots.config.Config;
import org.civworld.brainrots.data.DataRepo;
import org.civworld.brainrots.listener.NpcListener;
import org.civworld.brainrots.listener.PlateListener;
import org.civworld.brainrots.listener.PlayerListener;
import org.civworld.brainrots.manager.BrainrotManager;
import org.civworld.brainrots.manager.CbManager;
import org.civworld.brainrots.model.House;
import org.civworld.brainrots.model.Lobby;
import org.civworld.brainrots.placeholder.LobbyPlaceholder;
import org.civworld.brainrots.puller.Puller;
import org.civworld.brainrots.repo.BrainrotRepo;
import org.civworld.brainrots.repo.LobbyRepo;
import net.milkbowl.vault.economy.Economy;
import org.civworld.brainrots.tabcompleter.BrainrotTabCompleter;

public final class Brainrots extends JavaPlugin {
    private Puller puller = null;
    private Config config = null;
    private Plugin plugin;
    private LobbyRepo lobbyRepo = null;
    private static Economy econ = null;

    public Config getConfigManager(){
        return this.config;
    }

    @Override
    public void onEnable() {
        if (!setupEconomy() ) {
            getLogger().severe("Зависимость Vault не найдена! Пожалуйста, установите плагин: Vault");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        plugin = this;

        BrainrotRepo brainrotRepo = new BrainrotRepo();
        lobbyRepo = new LobbyRepo();
        DataRepo dataRepo = new DataRepo();

        config = new Config(this, brainrotRepo, lobbyRepo, dataRepo);
        config.loadConfig();

        puller = new Puller(this, brainrotRepo, lobbyRepo, config);
        puller.startPull();

        BrainrotManager brainrotManager = new BrainrotManager(brainrotRepo, lobbyRepo, puller);
        CbManager cbManager = new CbManager(lobbyRepo, dataRepo, puller, config);

        var command = getCommand("brainrot");
        if(command == null){
            error("ERROR! plugin.yml had an error.");
            error("Command is not registered! Please, fix a problem.");
            error("COMMAND WILL NOT WORK!!!");
        } else {
            command.setExecutor(new BrainrotCommand(brainrotManager, cbManager, this));
            command.setTabCompleter(new BrainrotTabCompleter(brainrotRepo, lobbyRepo));
            log("Command is successfully registered!");
        }

        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new LobbyPlaceholder(lobbyRepo).register();
            log("Placeholders successfully registered.");
        } else {
            log("Placeholders injected in plugin, but PlaceholderAPI is don't installed.");
        }

        if (econ == null) {
            getLogger().severe("Economy is null! Vault setup failed.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        registerEvents(new NpcListener(econ, puller, lobbyRepo, config));
        registerEvents(new PlateListener(lobbyRepo, this));
        registerEvents(new PlayerListener(lobbyRepo, config, puller));

        log("Plugin successfully enabled!");
    }

    private void registerEvents(Listener listener){
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return true;
    }

    @Override
    public void onDisable() {
        getLogger().info("Plugin successfully disabled!");
        if(puller == null) getLogger().warning("Попытка остановить Puller, когда он не начат!");
        else puller.stopPull();

        // Удаляем все домашних NPC, чтобы при перезагрузке не дублировались
        try {
            for (var npc : CitizensAPI.getNPCRegistry()) {
                try {
                    if (npc == null) continue;
                    if (npc.data().has("home")) {
                        try { if (npc.isSpawned()) npc.despawn(DespawnReason.REMOVAL); } catch (Throwable ignored) {}
                        try { CitizensAPI.getNPCRegistry().deregister(npc); } catch (Throwable ignored) {}
                        try { npc.destroy(); } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        if(config == null) getLogger().warning("Config не загружен! Не удалось сохранить конфиг.");
        else config.saveConfigData();

        if(lobbyRepo != null){
            for(Lobby lobby : lobbyRepo.getLobbies()){
                for(House house : lobby.getHouses()){
                    Hologram hologram = DHAPI.getHologram(lobby.getNum() + "_" + house.getId() + "_owner");
                    if(hologram != null){
                        hologram.delete();
                        hologram.destroy();
                    }

                    Hologram holoPlate = DHAPI.getHologram(lobby.getNum() + "_" + house.getId() + "_plate");
                    if(holoPlate != null){
                        holoPlate.destroy();
                        holoPlate.delete();
                    }

                    for (int i = 0; i < 10; i++) {
                        try {
                            Hologram slotH = DHAPI.getHologram(lobby.getNum() + "_" + house.getId() + "_slot_" + i);
                            if (slotH != null) {
                                slotH.delete();
                                slotH.destroy();
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }
    }

    private void log(String text){
        getLogger().info(text);
    }

    private void error(String text){
        getLogger().severe(text);
    }
}