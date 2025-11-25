package org.civworld.brainrots.config;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.civworld.brainrots.data.DataRepo;
import org.civworld.brainrots.data.PlayerData;
import org.civworld.brainrots.model.BrainrotModel;
import org.civworld.brainrots.model.House;
import org.civworld.brainrots.model.Lobby;
import org.civworld.brainrots.repo.BrainrotRepo;
import org.civworld.brainrots.repo.LobbyRepo;
import org.civworld.brainrots.type.Modificator;
import org.civworld.brainrots.type.Rarity;

import java.util.HashMap;

public final class Config {
    private final Plugin plugin;
    private final BrainrotRepo brainrotRepo;
    private final LobbyRepo lobbyRepo;
    private final DataRepo dataRepo;

    public Config(Plugin plugin, BrainrotRepo brainrotRepo, LobbyRepo lobbyRepo, DataRepo dataRepo){
        this.plugin = plugin;
        this.brainrotRepo = brainrotRepo;
        this.lobbyRepo = lobbyRepo;
        this.dataRepo = dataRepo;
    }

    public void loadConfig(){
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        // Загружаем brainrots
        if(config.contains("brainrots")){
            for(String key : config.getConfigurationSection("brainrots").getKeys(false)){
                String path = "brainrots." + key + ".";
                String displayName = config.getString(path + "displayName", key);
                Rarity rarity = Rarity.valueOf(config.getString(path + "rarity", "COMMON"));
                int cost = config.getInt(path + "cost", 0);
                int earn = config.getInt(path + "earn", 0);

                BrainrotModel model = new BrainrotModel(key, displayName, rarity, cost, earn);
                model.setMarginHologram(config.getDouble(path + "marginBottom", 0));
                model.setHeightHitbox(config.getDouble(path + "heightHitbox", 1.8));
                model.setWidthHitbox(config.getDouble(path + "widthHitbox", 0.6));

                brainrotRepo.addBrainrot(model);
            }
        }

        // Загружаем лобби и дома
        if(config.contains("lobbies")){
            for(String lobbyKey : config.getConfigurationSection("lobbies").getKeys(false)){
                String lobbyPath = "lobbies." + lobbyKey + ".";
                Location teleportLoc = config.getLocation(lobbyPath + "teleportLoc");
                int num = Integer.parseInt(lobbyKey);
                Lobby lobby = new Lobby(teleportLoc, num);

                if(config.contains(lobbyPath + "houses")){
                    for(String houseKey : config.getConfigurationSection(lobbyPath + "houses").getKeys(false)){
                        String housePath = lobbyPath + "houses." + houseKey + ".";
                        Location plate = config.getLocation(housePath + "plateCloseDoor");
                        boolean right = config.getBoolean(housePath + "right");
                        House house = new House(plate, Integer.parseInt(houseKey), right);

                        lobby.addHouse(house);
                    }
                }

                lobbyRepo.addLobby(lobby);
            }
        }
    }

    public void saveConfigData(){
        // Сохраняем brainrots
        FileConfiguration config = plugin.getConfig();
        config.set("brainrots", null);
        for(BrainrotModel brainrotModel : brainrotRepo.getBrainrots()){
            String path = "brainrots." + brainrotModel.getId() + ".";
            config.set(path + "displayName" , brainrotModel.getDisplayName());
            config.set(path + "cost" , brainrotModel.getCost());
            config.set(path + "earn" , brainrotModel.getEarn());
            config.set(path + "rarity" , brainrotModel.getRarity().name());
            config.set(path + "marginBottom" , brainrotModel.getMarginHologram());
            config.set(path + "heightHitbox" , brainrotModel.getHeightHitbox());
            config.set(path + "widthHitbox" , brainrotModel.getWidthHitbox());
        }

        config.set("lobbies", null);
        for(Lobby lobby : lobbyRepo.getLobbies()){
            String path = "lobbies." + lobby.getNum() + ".";
            config.set(path + "teleportLoc", lobby.getTeleportLoc());
            for(House house : lobby.getHouses()){
                String housePath = path + "houses." + house.getId() + ".";
                config.set(housePath + "plateCloseDoor", house.getPlateCloseDoor());
            }
        }

        if(dataRepo != null){
            for(PlayerData playerData : dataRepo.getPlayerDatas().values()){
                if(playerData == null || playerData.getPlayer() == null) continue;

                PlayerFile pf = new PlayerFile(playerData.getPlayer().getName(), plugin);
                FileConfiguration configPf = pf.getConfig();

                configPf.set("lastSaved", playerData.getLastSaved());

                for(int i = 0; i < playerData.getOwnBreinrots().size(); i++){
                    HashMap<BrainrotModel, Modificator> map = playerData.getOwnBreinrots().get(i);
                    for(BrainrotModel model : map.keySet()){
                        if(model == null) continue;
                        Modificator mod = map.get(model);
                        if(mod == null) mod = Modificator.BRONZE;

                        String playerPath = "ownBreinrots." + i + "." + model.getId() + ".modificator";
                        configPf.set(playerPath, mod.name());
                    }
                }

                pf.save();
            }
        }

        plugin.saveConfig();
    }
}
