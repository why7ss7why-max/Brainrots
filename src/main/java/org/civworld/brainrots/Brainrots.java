package org.civworld.brainrots;

import org.bukkit.plugin.java.JavaPlugin;
import org.civworld.brainrots.command.BrainrotCommand;
import org.civworld.brainrots.manager.BrainrotManager;
import org.civworld.brainrots.puller.Puller;
import org.civworld.brainrots.repo.BrainrotRepo;
import org.civworld.brainrots.repo.LobbyRepo;

public final class Brainrots extends JavaPlugin {
    private Puller puller = null;

    @Override
    public void onEnable() {
        BrainrotRepo brainrotRepo = new BrainrotRepo();
        LobbyRepo lobbyRepo = new LobbyRepo();
        BrainrotManager brainrotManager = new BrainrotManager(brainrotRepo, lobbyRepo, this);

        puller = new Puller(this, brainrotRepo, lobbyRepo);
        puller.startPoll();

        var command = getCommand("brainrot");
        if(command == null){
            getLogger().warning("ERROR! plugin.yml had an error.");
            getLogger().warning("Command is not registered! Please, fix a problem.");
            getLogger().warning("COMMAND WILL NOT WORK!!!");
        } else {
            command.setExecutor(new BrainrotCommand(brainrotManager));
            getLogger().info("Command is successfully registered!");
        }

        getLogger().info("Plugin successfully enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Plugin successfully disabled!");
        puller.stopPull();
    }
}