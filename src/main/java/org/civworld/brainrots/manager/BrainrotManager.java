package org.civworld.brainrots.manager;

import io.lumine.mythic.api.adapters.AbstractLocation;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.civworld.brainrots.model.BrainrotModel;
import org.civworld.brainrots.model.Lobby;
import org.civworld.brainrots.repo.BrainrotRepo;
import org.civworld.brainrots.repo.LobbyRepo;
import org.civworld.brainrots.type.Modificator;
import org.civworld.brainrots.type.Rarity;

import static org.civworld.brainrots.util.Utils.parse;

public class BrainrotManager {
    private final BrainrotRepo brainrotRepo;
    private final LobbyRepo lobbyRepo;
    private final Plugin plugin;

    public BrainrotManager(BrainrotRepo brainrotRepo, LobbyRepo lobbyRepo, Plugin plugin){
        this.brainrotRepo = brainrotRepo;
        this.lobbyRepo = lobbyRepo;
        this.plugin = plugin;
    }

    public void createBrainrot(CommandSender sender, String[] args){
        if(args.length < 6){
            sender.sendMessage(parse("<prefix>Использование: <blue>/bt create <айди> <редкость> <стоимость> <мод> <прибыль> <название>"));
            return;
        }

        String id = args[1];
        if(brainrotRepo.hasBrainrotById(id)){
            sender.sendMessage(parse("<prefix>Бреинрот с <gray>таким <blue>ID <white>уже <red>существует<white>!"));
            return;
        }

        Rarity rarity;
        try {
            rarity = Rarity.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(parse("<prefix>Вы <gray>ввели <red>неверную <white>редкость!"));
            StringBuilder stringBuilder = new StringBuilder();
            for(Rarity r : Rarity.values()){
                stringBuilder.append(r).append(" ");
            }
            sender.sendMessage(parse("<prefix>Доступные: <blue>" + stringBuilder));
            return;
        }

        int cost;
        try{
            cost = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(parse("<prefix>Вы <gray>ввели <red>неверную <white>стоимость!"));
            return;
        }

        Modificator modificator;
        try{
            modificator = Modificator.valueOf(args[4].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(parse("<prefix>Вы <gray>ввели <red>неверный <white>модификатор!"));
            StringBuilder stringBuilder = new StringBuilder();
            for(Modificator m : Modificator.values()){
                stringBuilder.append(m).append(" ");
            }
            sender.sendMessage(parse("<prefix>Доступные: <blue>" + stringBuilder));
            return;
        }

        int earn;
        try{
            earn = Integer.parseInt(args[5]);
        } catch (NumberFormatException e) {
            sender.sendMessage(parse("<prefix>Вы <gray>ввели <red>неверную <white>прибыль!"));
            return;
        }

        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 6, args.length));

        BrainrotModel brainrot = new BrainrotModel(id, name, rarity, cost, modificator, earn);
        brainrotRepo.addBrainrot(brainrot);

        sender.sendMessage(parse("<prefix>Вы <green>успешно <white>создали нового <blue>бреинрота<white>!"));
        sender.sendMessage(parse("<prefix>Айди: <blue>" + id));
        sender.sendMessage(parse("<prefix>Имя: <blue>" + name));
        sender.sendMessage(parse("<prefix>Редкость: <blue>" + rarity));
        sender.sendMessage(parse("<prefix>Стоимость: <blue>" + cost + "$"));
        sender.sendMessage(parse("<prefix>Модификатор: <blue>" + modificator));
        sender.sendMessage(parse("<prefix>Прибыль: <blue>" + earn + "/с $"));
    }

    public void deleteBrainrot(CommandSender sender, String[] args){
        if(args.length < 2){
            sender.sendMessage(parse("<prefix>Использование: <blue>/bt delete <айди>"));
            return;
        }

        String id = args[1].toLowerCase();
        if(!brainrotRepo.hasBrainrotById(id)){
            sender.sendMessage(parse("<prefix>Такого <blue>бреинрота <white>не <red>существует<white>!"));
            return;
        }

        brainrotRepo.removeBrainrot(id);
        sender.sendMessage(parse("<prefix>Бреинрот с айди <blue>" + id + " <white>был <green>успешно <red>удалён<white>!"));
    }

    public void listBrainrots(CommandSender sender){
        if(brainrotRepo.getBrainrots().isEmpty()){
            sender.sendMessage(parse("<prefix>Бреинрот-список <red>пуст<white>!"));
            return;
        }

        StringBuilder stringBuilder = new StringBuilder();

        for(BrainrotModel brainrotModel : brainrotRepo.getBrainrots()){
            stringBuilder.append(brainrotModel.getId()).append(" ");
        }

        sender.sendMessage(parse("<prefix>Список бреинротов: <blue>" + stringBuilder + "<gray>(<blue>" + brainrotRepo.getBrainrots().size() + "<gray>)"));
    }

    public void infoBrainrots(CommandSender sender, String[] args){
        if(brainrotRepo.getBrainrots().isEmpty()){
            sender.sendMessage(parse("<prefix>Бреинрот-список <red>пуст<white>!"));
            return;
        }

        String id = args[1].toLowerCase();
        if(!brainrotRepo.hasBrainrotById(id)){
            sender.sendMessage(parse("<prefix>Такого <blue>бреинрота <white>не <red>существует<white>!"));
            return;
        }

        BrainrotModel brainrotModel = brainrotRepo.getById(id);
        if(brainrotModel == null){
            sender.sendMessage(parse("<prefix>Произошла <red>ошибка <white>с получением <blue>бреинрота<white>."));
            sender.sendMessage(parse("<prefix>Свяжитесь с <green>администратором<white>."));
            return;
        }

        sender.sendMessage(parse("<prefix>Информация о бреинроте: <blue>" + id));
        sender.sendMessage(parse("<prefix>Название: <yellow>" + brainrotModel.getDisplayName()));
        sender.sendMessage(parse("<prefix>Редкость: <yellow>" + brainrotModel.getRarity()));
        sender.sendMessage(parse("<prefix>Стоимость: <yellow>" + brainrotModel.getCost() + "$"));
        sender.sendMessage(parse("<prefix>Модификатор: <yellow>" + brainrotModel.getModificator()));
        sender.sendMessage(parse("<prefix>Прибыль: <yellow>" + brainrotModel.getEarn() + "/с $"));
    }

    public void handleLobbyCommand(CommandSender sender, String[] args){
        if(args.length < 2){
            helpLobbyCommand(sender);
            return;
        }

        switch(args[1].toLowerCase()){
            case "create" -> {
                if(!(sender instanceof Player player)){
                    sender.sendMessage(parse("<prefix>Создать <gray>лобби <white>можно только <red>от игрока<white>!"));
                    return;
                }

                if(args.length < 3){
                    player.sendMessage(parse("<prefix>Использование: <blue>/bt lobby create <айди>"));
                    return;
                }

                int id;
                try{
                    id = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage(parse("<prefix>Вы ввели <red>неверный <white>ID!"));
                    return;
                }

                Location loc = player.getLocation();

                lobbyRepo.addLobby(new Lobby(loc, id));
            }
            default -> helpLobbyCommand(sender);
        }
    }

    public void helpLobbyCommand(CommandSender sender){
        sender.sendMessage(parse("<prefix>Использование:"));
        sender.sendMessage(parse("<prefix><blue>/bt lobby create <айди> <gray>- <white>Создать <gray>новое <blue>лобби"));
        sender.sendMessage(parse("<prefix><blue>/bt lobby delete <айди> <gray>- <white>Удалить <red>лобби"));
        sender.sendMessage(parse("<prefix><blue>/bt lobby list <gray>- <white>Список <red>лобби"));
    }
}