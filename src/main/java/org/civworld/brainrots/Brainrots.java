package org.civworld.brainrots;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.civworld.brainrots.command.BrainrotCommand;
import org.civworld.brainrots.config.Config;
import org.civworld.brainrots.listener.NpcListener;
import org.civworld.brainrots.manager.BrainrotManager;
import org.civworld.brainrots.puller.Puller;
import org.civworld.brainrots.repo.BrainrotRepo;
import org.civworld.brainrots.repo.LobbyRepo;
import net.milkbowl.vault.economy.Economy;
import org.civworld.brainrots.tabcompleter.BrainrotTabCompleter;

public final class Brainrots extends JavaPlugin {
    private Puller puller = null;
    private Config config = null;
    private static Economy econ = null;

    @Override
    public void onEnable() {
        if (!setupEconomy() ) {
            getLogger().severe("Зависимость Vault не найдена! Пожалуйста, установите плагин: Vault");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        BrainrotRepo brainrotRepo = new BrainrotRepo();
        LobbyRepo lobbyRepo = new LobbyRepo();

        config = new Config(this, brainrotRepo, lobbyRepo);
        config.loadConfig();

        puller = new Puller(this, brainrotRepo, lobbyRepo);
        puller.startPull();

        BrainrotManager brainrotManager = new BrainrotManager(brainrotRepo, lobbyRepo, this, puller);

        var command = getCommand("brainrot");
        if(command == null){
            error("ERROR! plugin.yml had an error.");
            error("Command is not registered! Please, fix a problem.");
            error("COMMAND WILL NOT WORK!!!");
        } else {
            command.setExecutor(new BrainrotCommand(brainrotManager));
            command.setTabCompleter(new BrainrotTabCompleter(brainrotRepo, lobbyRepo));
            log("Command is successfully registered!");
        }

        if (econ == null) {
            getLogger().severe("Economy is null! Vault setup failed.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Bukkit.getPluginManager().registerEvents(new NpcListener(econ, puller), this);

        log("Plugin successfully enabled!");
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

        if(config == null) getLogger().warning("Config не загружен! Не удалось сохранить конфиг.");
        else config.saveConfigData();
    }

    private void log(String text){
        getLogger().info(text);
    }

    private void error(String text){
        getLogger().severe(text);
    }
}