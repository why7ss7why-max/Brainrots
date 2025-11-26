package org.civworld.brainrots.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.civworld.brainrots.data.DataRepo;
import org.civworld.brainrots.data.PlayerData;
import org.civworld.brainrots.model.House;
import org.civworld.brainrots.model.Lobby;
import org.civworld.brainrots.repo.LobbyRepo;

import static org.civworld.brainrots.util.Utils.parse;

public class CbManager {
    private final LobbyRepo lobbyRepo;
    private final DataRepo dataRepo;

    public CbManager(LobbyRepo lobbyRepo, DataRepo dataRepo){
        this.lobbyRepo = lobbyRepo;
        this.dataRepo = dataRepo;
    }

    public void handleMainCmd(CommandSender sender, String[] args){
        if(args.length < 2){
            sender.sendMessage(parse("<prefix>Использование:"));
            sender.sendMessage(parse("<prefix><blue>/bt commandblock lobbyjoin <игрок> <лобби>"));
            sender.sendMessage(parse("<prefix><blue>/bt commandblock brainrotuse <игрок> <лобби> <дом> <айди>"));
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
                            sender.sendMessage(parse("<prefix>Игрок <red>уже <white>взял <gold>дом<white>!"));
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

                PlayerData playerData = dataRepo.getPlayerData(player);
                house.setPlayerData(playerData);

                player.sendMessage(parse("<prefix>Вы <green>взяли <white>дом!"));
                player.teleport(house.isRight() ? house.getPlateCloseDoor().clone().add(0, 0, 1) : house.getPlateCloseDoor().clone().add(0, 0, -1));
            }
            case "brainrotuse" -> {

            }
        }
    }
}