package org.civworld.brainrots.manager;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.civworld.brainrots.config.Config;
import org.civworld.brainrots.data.DataRepo;
import org.civworld.brainrots.data.PlayerData;
import org.civworld.brainrots.model.House;
import org.civworld.brainrots.model.Lobby;
import org.civworld.brainrots.puller.Puller;
import org.civworld.brainrots.repo.LobbyRepo;

import static org.civworld.brainrots.util.Utils.*;

public class CbManager {
    private final LobbyRepo lobbyRepo;
    private final DataRepo dataRepo;
    private final Puller puller;
    private final Config config;

    public CbManager(LobbyRepo lobbyRepo, DataRepo dataRepo, Puller puller, Config config){
        this.lobbyRepo = lobbyRepo;
        this.dataRepo = dataRepo;
        this.puller = puller;
        this.config = config;
    }

    // Удалить бреинрота игрока в указанном слоте: найти дом, убрать слот, обновить домашние NPC и сохранить данные
    public boolean removePlayerBrainrot(Player player, int slot) {
        if (player == null) return false;
        Lobby foundLobby = null;
        House foundHouse = null;
        for (Lobby l : lobbyRepo.getLobbies()){
            for (House h : l.getHouses()){
                if (h.getPlayerData() != null && h.getPlayerData().getPlayer() != null && h.getPlayerData().getPlayer().equals(player)){
                    foundLobby = l;
                    foundHouse = h;
                    break;
                }
            }
            if (foundHouse != null) break;
        }

        if (foundHouse == null) return false;

        PlayerData pd = foundHouse.getPlayerData();
        if (slot < 0 || slot >= pd.getOwnBreinrots().size()) return false;
        var pair = pd.getOwnBreinrots().get(slot);
        if (pair == null || pair.getLeft() == null) return false;

        pd.removeBrainrot(slot);
        puller.updateHomeBrainrots(foundHouse);
        try { config.savePlayerData(player); } catch (Throwable ignored) {}
        return true;
    }

    public void handleMainCmd(CommandSender sender, String[] args){
        if(args.length < 2){
            sender.sendMessage(parse("<prefix>Использование:"));
            sender.sendMessage(parse("<prefix><blue>/bt commandblock lobbyjoin <игрок> <лобби>"));
            sender.sendMessage(parse("<prefix><blue>/bt commandblock lobbyleave <игрок>"));
            return;
        }

        switch(args[1].toLowerCase()){
            case "lobbyjoin" -> {
                if(args.length < 4){
                    sender.sendMessage(parse("<prefix>Использование: <blue>/bt commandblock lobbyjoin <игрок> <лобби>"));
                    return;
                }

                Player player = Bukkit.getPlayer(args[2]);
                if(player == null || !player.isOnline()){
                    sender.sendMessage(parse("<prefix>Игрок <red>не найден<white>!"));
                    return;
                }

                int lobby;
                try{
                    lobby = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(parse("<prefix>Вы <red>не ввели <white>лобби!"));
                    return;
                }
                if(!lobbyRepo.hasByNumber(lobby)){
                    sender.sendMessage(parse("<prefix>Лобби <red>не найдено<white>!"));
                    return;
                }
                Lobby lobbyModel = lobbyRepo.getByNumber(lobby);

                for(House h : lobbyModel.getHouses()){
                    if(h.getPlayerData() != null) {
                        if(h.getPlayerData().getPlayer().equals(player)){
                            player.sendMessage(parse("<prefix>Вы <red>уже <white>взяли <gold>дом<white>!"));
                            return;
                        }
                    }
                }

                House house = null;
                for(House h : lobbyModel.getHouses()){
                    if(h.getPlayerData() == null){
                        house = h;
                    }
                }
                if(house == null){
                    sender.sendMessage(parse("<prefix>Произошла <red>ошибка<white>! Все <yellow>слоты <red>заняты<white>."));
                    return;
                }

                // Обеспечиваем загрузку данных игрока с диска перед выдачей дома
                try {
                    config.loadPlayerData(player);
                } catch (Throwable ignored) {}

                PlayerData playerData = dataRepo.getPlayerData(player);
                house.setPlayerData(playerData);

                puller.updateHomeBrainrots(house);

                Location forHologramLoc = house.isRight() ? house.getPlateCloseDoor().clone().add(0, 9, 25) : house.getPlateCloseDoor().clone().add(0, 9, -25);

                Hologram hologram = createHologram(forHologramLoc, lobby + "_" + house.getId() + "_owner");
                DHAPI.addHologramLine(hologram, "Дом игрока: &9" + player.getName());

                Hologram holoPlate = createHologram(house.getPlateCloseDoor().clone().add(0, 1, 0), lobby + "_" + house.getId() + "_plate");
                DHAPI.addHologramLine(holoPlate, "Дверь &aоткрыта");

                player.sendMessage(parse("<prefix>Вы <green>взяли <white>дом!"));
                player.teleport(house.isRight() ? house.getPlateCloseDoor().clone().add(0, 0, 1) : house.getPlateCloseDoor().clone().add(0, 0, -1));
            }
            case "lobbyleave" -> {
                if(args.length < 3){
                    sender.sendMessage(parse("<prefix>Использование: <blue>/bt commandblock lobbyleave <игрок>"));
                    return;
                }

                Player player = Bukkit.getPlayer(args[2]);
                if(player == null || !player.isOnline()){
                    sender.sendMessage(parse("<prefix>Игрок <red>не найден<white>!"));
                    return;
                }

                Lobby lobby = null;
                for(Lobby l : lobbyRepo.getLobbies()){
                    for(House h : l.getHouses()){
                        if(h.getPlayerData() != null) {
                            if(h.getPlayerData().getPlayer().equals(player)){
                                lobby = l;
                            }
                        }
                    }
                }
                if(lobby == null){
                    player.sendMessage(parse("<prefix>Вы <red>не телепортировались <white>в лобби."));
                    return;
                }

                House house = null;
                for(House h : lobby.getHouses()){
                    if(h.getPlayerData() != null) {
                        if(h.getPlayerData().getPlayer().equals(player)){
                            house = h;
                        }
                    }
                }
                if(house == null){
                    player.sendMessage(parse("<prefix>Вы <red>не взяли <white>дом."));
                    return;
                }

                try { puller.removeHomeNpcs(player.getName()); } catch (Throwable ignored) {}
                try { config.savePlayerData(player); } catch (Throwable ignored) {}

                house.setPlayerData(null);
                house.setClosed(false);

                deleteHologram(lobby, house, "owner");
                deleteHologram(lobby, house, "plate");
                // delete slot holograms
                for (int si = 0; si < 10; si++) {
                    try { deleteHologram(lobby, house, "slot_" + si); } catch (Throwable ignored) {}
                }

                player.sendMessage(parse("<prefix>Вы <green>покинули <white>дом!"));
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:tp " + player.getName() + " 581 42 -634 0 0");
            }
        }
    }

    public void toggleAutoSell(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(parse("<prefix>Команду может выполнять только игрок."));
            return;
        }

        PlayerData pd = dataRepo.getPlayerData(player);
        if (pd == null) pd = dataRepo.getPlayerData(player);

        boolean newVal = pd.toggleAutoSell();
        try { config.savePlayerData(player); } catch (Throwable ignored) {}

        String box = newVal ? "✓" : " ";

        sender.sendMessage(parse("<hover:show_text:'<white>Переключить автопродажу'><click:run_command:'/brainrot autosell'>" +
                "[" + box + "] Авто продажа</click></hover>"));
        if (newVal) {
            sender.sendMessage(parse("<prefix>Автопродажа <green>включена<white>!"));
        }
        else{
            sender.sendMessage(parse("<prefix>Автопродажа <red>выключена<white>!"));
        }
    }
}