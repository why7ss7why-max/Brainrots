package org.civworld.brainrots.config;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;

public class PlayerFile {
    private final Plugin plugin;
    private final File file;
    @Getter private final FileConfiguration config;

    public PlayerFile(String playerName, Plugin plugin){
        this.plugin = plugin;

        File folder = new File(plugin.getDataFolder(), "players");
        if(!folder.exists()){
            boolean createdFolder = folder.mkdirs();
            if(!createdFolder){
                plugin.getLogger().severe("Could not create players folder!");
            }
        }

        this.file = new File(folder, playerName + ".yml");
        if(!file.exists()){
            try {
                boolean createdFile = file.createNewFile();
                if(!createdFile){
                    plugin.getLogger().warning("Player file already exists or could not be created: " + playerName);
                }
            } catch(IOException e){
                plugin.getLogger().severe("Error creating player file for " + playerName + ": " + e.getMessage());
            }
        }

        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public void save(){
        try {
            config.save(file);
        } catch(IOException e){
            plugin.getLogger().severe("Error saving player file: " + e.getMessage());
        }
    }
}