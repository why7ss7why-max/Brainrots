package org.civworld.brainrots.config;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.civworld.brainrots.model.BrainrotModel;
import org.civworld.brainrots.model.House;
import org.civworld.brainrots.model.Lobby;
import org.civworld.brainrots.repo.BrainrotRepo;
import org.civworld.brainrots.repo.LobbyRepo;
import org.civworld.brainrots.type.Modificator;
import org.civworld.brainrots.type.Rarity;

public final class Config {
    private final Plugin plugin;
    private final BrainrotRepo brainrotRepo;
    private final LobbyRepo lobbyRepo;

    public Config(Plugin plugin, BrainrotRepo brainrotRepo, LobbyRepo lobbyRepo){
        this.plugin = plugin;
        this.brainrotRepo = brainrotRepo;
        this.lobbyRepo = lobbyRepo;
    }

    public void loadConfig(){
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        if(config.contains("brainrots")){
            for(String key : config.getConfigurationSection("brainrots").getKeys(false)){
                String path = "brainrots." + key + ".";
                String displayName = config.getString(path + "displayName", key);
                Rarity rarity = Rarity.valueOf(config.getString(path + "rarity", "COMMON"));
                int cost = config.getInt(path + "cost", 0);
                Modificator modificator = Modificator.valueOf(config.getString(path + "modificator", "BRONZE"));
                int earn = config.getInt(path + "earn", 0);

                BrainrotModel model = new BrainrotModel(key, displayName, rarity, cost, modificator, earn);
                model.setMarginHologram(config.getDouble(path + "marginBottom", 0));
                model.setHeightHitbox(config.getDouble(path + "heightHitbox", 1.8));
                model.setWidthHitbox(config.getDouble(path + "widthHitbox", 0.6));

                brainrotRepo.addBrainrot(model);
            }
        }

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
                        House house = new House(plate);
                        house.setId(Integer.parseInt(houseKey));

                        if(config.contains(housePath + "brainrots")){
                            for(String brainrotKey : config.getConfigurationSection(housePath + "brainrots").getKeys(false)){
                                String brPath = housePath + "brainrots." + brainrotKey + ".";
                                BrainrotModel brModel = brainrotRepo.getById(brainrotKey);
                                if(brModel != null){
                                    house.addBrainrot(brModel);
                                }
                            }
                        }

                        lobby.addHouse(house);
                    }
                }

                lobbyRepo.addLobby(lobby);
            }
        }
    }

    public void saveConfigData(){
        FileConfiguration config = plugin.getConfig();

        config.set("brainrots", null);
        for(BrainrotModel brainrotModel : brainrotRepo.getBrainrots()){
            String path = "brainrots." + brainrotModel.getId() + ".";
            config.set(path + "displayName" , brainrotModel.getDisplayName());
            config.set(path + "cost" , brainrotModel.getCost());
            config.set(path + "earn" , brainrotModel.getEarn());
            config.set(path + "modificator" , brainrotModel.getModificator().name()); // <- только имя
            config.set(path + "rarity" , brainrotModel.getRarity().name());          // <- только имя
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
                for(BrainrotModel brainrotModel : house.getBrainrots()){
                    String bPath = housePath + "brainrots." + brainrotModel.getId() + ".";
                    config.set(bPath + "cost", brainrotModel.getCost());
                    config.set(bPath + "modificator", brainrotModel.getModificator().name()); // <- только имя
                    config.set(bPath + "earn", brainrotModel.getEarn());
                    config.set(bPath + "displayName", brainrotModel.getDisplayName());
                }
            }
        }

        plugin.saveConfig();
        Bukkit.getLogger().info("Config saved successfully!");
    }
}
