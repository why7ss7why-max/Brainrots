package org.civworld.brainrots.config;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class Config {

    private final Plugin plugin;
    private final BrainrotRepo brainrotRepo;
    private final LobbyRepo lobbyRepo;
    private final DataRepo dataRepo;

    // Папка с данными игроков
    private final File playersFolder;
    private final Map<String, FileConfiguration> playerConfigs = new HashMap<>();

    public Config(Plugin plugin, BrainrotRepo brainrotRepo, LobbyRepo lobbyRepo, DataRepo dataRepo) {
        this.plugin = plugin;
        this.brainrotRepo = brainrotRepo;
        this.lobbyRepo = lobbyRepo;
        this.dataRepo = dataRepo;

        this.playersFolder = new File(plugin.getDataFolder(), "players");
        if (!playersFolder.exists()) {
            playersFolder.mkdirs();
        }
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        // === Загрузка бреинротов из config.yml ===
        if (config.contains("brainrots")) {
            for (String key : config.getConfigurationSection("brainrots").getKeys(false)) {
                String path = "brainrots." + key + ".";
                String displayName = config.getString(path + "displayName", key);
                Rarity rarity = Rarity.valueOf(config.getString(path + "rarity", "COMMON").toUpperCase());
                int cost = config.getInt(path + "cost", 0);
                int earn = config.getInt(path + "earn", 0);

                BrainrotModel model = new BrainrotModel(key, displayName, rarity, cost, earn);
                model.setMarginHologram(config.getDouble(path + "marginBottom", 0.0));
                model.setHeightHitbox(config.getDouble(path + "heightHitbox", 1.8));
                model.setWidthHitbox(config.getDouble(path + "widthHitbox", 0.6));

                brainrotRepo.addBrainrot(model);
            }
        }

        // === Загрузка лобби и домов ===
        if (config.contains("lobbies")) {
            for (String lobbyKey : config.getConfigurationSection("lobbies").getKeys(false)) {
                String lobbyPath = "lobbies." + lobbyKey + ".";
                Location teleportLoc = config.getLocation(lobbyPath + "teleportLoc");
                if (teleportLoc == null) continue;

                int num = Integer.parseInt(lobbyKey);
                Lobby lobby = new Lobby(teleportLoc, num);

                if (config.contains(lobbyPath + "houses")) {
                    for (String houseKey : config.getConfigurationSection(lobbyPath + "houses").getKeys(false)) {
                        String housePath = lobbyPath + "houses." + houseKey + ".";
                        Location plate = config.getLocation(housePath + "plateCloseDoor");
                        boolean right = config.getBoolean(housePath + "right", false);
                        if (plate != null) {
                            House house = new House(plate, Integer.parseInt(houseKey), right);
                            lobby.addHouse(house);
                        }
                    }
                }
                lobbyRepo.addLobby(lobby);
            }
        }

        plugin.getLogger().info("Конфиг и бреинроты загружены.");
    }

    // Загрузка данных конкретного игрока из его файла
    public void loadPlayerData(Player player) {
        String playerName = player.getName();
        File playerFile = new File(playersFolder, playerName + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(playerFile);

        PlayerData playerData = new PlayerData(player);

        // Load autoSell setting if present
        boolean autoSell = config.getBoolean("autoSell", false);
        playerData.setAutoSell(autoSell);

        int loaded = 0;
        // Загружаем бреинроты (слоты 0–9)
        if (config.contains("ownBreinrots")) {
            for (String slotStr : config.getConfigurationSection("ownBreinrots").getKeys(false)) {
                try {
                    String base = "ownBreinrots." + slotStr + ".";
                    String id = config.getString(base + "id");
                    String modStr = config.getString(base + "mod", "BRONZE");
                    double stored = config.getDouble(base + "stored", 0.0);

                    if (id == null) continue;

                    BrainrotModel model = brainrotRepo.getById(id);
                    if (model == null) {
                        plugin.getLogger().warning("Бреинрот с ID '" + id + "' не найден у игрока " + playerName + " (слот " + slotStr + ")");
                        continue;
                    }

                    Modificator modificator;
                    try{
                        modificator = Modificator.valueOf(modStr.toUpperCase());
                    } catch (Exception e) {
                        modificator = Modificator.BRONZE;
                        plugin.getLogger().warning("Неверный модификатор '" + modStr + "' у игрока " + playerName + " в слоте " + slotStr + ". Использован BRONZE.");
                    }
                    int slot = Integer.parseInt(slotStr);

                    if (slot >= 0 && slot < 10) {
                        playerData.addBrainrot(slot, model, modificator);
                        // restore stored amount if present
                        if (stored > 0.0) playerData.setStoredAmount(slot, stored);
                        loaded++;
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Ошибка при загрузке слота бреинрота для игрока " + playerName + ": " + t.getMessage());
                }
            }
        }

        // Сохраняем в репозиторий
        dataRepo.addPlayerData(player, playerData);
        playerConfigs.put(playerName, config);

        plugin.getLogger().info("Данные игрока " + playerName + " загружены. Слотов: " + loaded + ".");
    }

    public void savePlayerData(Player player) {
        PlayerData playerData = dataRepo.getPlayerData(player);
        if (playerData == null) return;

        String playerName = player.getName();
        File playerFile = new File(playersFolder, playerName + ".yml");

        FileConfiguration config;
        if (playerConfigs.containsKey(playerName)) {
            config = playerConfigs.get(playerName);
        } else {
            if (!playerFile.exists()) {
                try {
                    if (playerFile.createNewFile()) {
                        plugin.getLogger().info("Создан новый файл игрока: " + playerName);
                    }
                } catch (IOException e) {
                    plugin.getLogger().severe("Не удалось создать файл для игрока " + playerName + ": " + e.getMessage());
                    return;
                }
            }
            config = YamlConfiguration.loadConfiguration(playerFile);
            playerConfigs.put(playerName, config);
        }

        // Очищаем старые данные
        config.set("ownBreinrots", null);

        // Записываем бреинроты
        for (int i = 0; i < playerData.getOwnBreinrots().size(); i++) {
            var pair = playerData.getOwnBreinrots().get(i);
            if (pair == null || pair.getLeft() == null) continue;

            BrainrotModel model = pair.getLeft();
            Modificator mod = pair.getRight() != null ? pair.getRight() : Modificator.BRONZE;

            String base = "ownBreinrots." + i + ".";
            config.set(base + "id", model.getId());
            config.set(base + "mod", mod.name());
            // save stored amount
            double stored = playerData.getStoredAmount(i);
            config.set(base + "stored", stored);
        }

        // Save autoSell setting
        config.set("autoSell", playerData.isAutoSell());

        config.set("lastSaved", System.currentTimeMillis());

        try {
            config.save(playerFile);
            playerData.setLastSaved(System.currentTimeMillis());
            plugin.getLogger().info("Данные игрока " + playerName + " успешно сохранены.");
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить данные игрока " + playerName + ": " + e.getMessage());
        }
    }

    // Сохранение всех данных игроков + основного config.yml
    public void saveConfigData() {
        // Сохраняем основной config.yml (бреинроты, лобби и т.д.)
        FileConfiguration mainConfig = plugin.getConfig();
        mainConfig.set("brainrots", null);
        for (BrainrotModel model : brainrotRepo.getBrainrots()) {
            String path = "brainrots." + model.getId() + ".";
            mainConfig.set(path + "displayName", model.getDisplayName());
            mainConfig.set(path + "cost", model.getCost());
            mainConfig.set(path + "earn", model.getEarn());
            mainConfig.set(path + "rarity", model.getRarity().name());
            mainConfig.set(path + "marginBottom", model.getMarginHologram());
            mainConfig.set(path + "heightHitbox", model.getHeightHitbox());
            mainConfig.set(path + "widthHitbox", model.getWidthHitbox());
        }

        mainConfig.set("lobbies", null);
        for (Lobby lobby : lobbyRepo.getLobbies()) {
            String path = "lobbies." + lobby.getNum() + ".";
            mainConfig.set(path + "teleportLoc", lobby.getTeleportLoc());
            for (House house : lobby.getHouses()) {
                String hPath = path + "houses." + house.getId() + ".";
                mainConfig.set(hPath + "plateCloseDoor", house.getPlateCloseDoor());
                mainConfig.set(hPath + "right", house.isRight());
            }
        }

        plugin.saveConfig();

        // Сохраняем всех онлайн-игроков
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            savePlayerData(onlinePlayer);
        }

        plugin.getLogger().info("Все данные сохранены (config.yml + игроки).");
    }

    // Вызывать при выходе игрока
    public void savePlayerOnQuit(Player player) {
        savePlayerData(player);
        playerConfigs.remove(player.getName());
        dataRepo.removePlayerData(player);
    }
}