package org.civworld.brainrots;

import org.bukkit.plugin.java.JavaPlugin;
import org.civworld.brainrots.command.BrainrotCommand;
import org.civworld.brainrots.manager.BrainrotManager;
import org.civworld.brainrots.repo.BrainrotRepo;

public final class Brainrots extends JavaPlugin {
    @Override
    public void onEnable() {
        BrainrotRepo brainrotRepo = new BrainrotRepo();
        BrainrotManager brainrotManager = new BrainrotManager(brainrotRepo);

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
    }
}