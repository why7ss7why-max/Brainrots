package org.civworld.brainrots.manager;

import org.bukkit.command.CommandSender;
import org.civworld.brainrots.model.BrainrotModel;
import org.civworld.brainrots.repo.BrainrotRepo;
import org.civworld.brainrots.type.Modificator;
import org.civworld.brainrots.type.Rarity;

import static org.civworld.brainrots.util.Utils.parse;

public class BrainrotManager {
    private final BrainrotRepo brainrotRepo;

    public BrainrotManager(BrainrotRepo brainrotRepo){
        this.brainrotRepo = brainrotRepo;
    }

    public void createBrainrot(CommandSender sender, String[] args){
        if(args.length < 7){
            sender.sendMessage(parse("<prefix>Использование: <blue>/br create <айди> <редкость> <стоимость> <мод> <прибыль> <название>"));
            return;
        }

        Rarity rarity;
        try {
            rarity = Rarity.valueOf(args[1].toUpperCase());
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
            cost = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(parse("<prefix>Вы <gray>ввели <red>неверную <white>стоимость!"));
            return;
        }

        Modificator modificator;
        try{
            modificator = Modificator.valueOf(args[3].toUpperCase());
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
            earn = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(parse("<prefix>Вы <gray>ввели <red>неверную <white>прибыль!"));
            return;
        }

        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 5, args.length));

        String id = args[1].toLowerCase();

        BrainrotModel brainrot = new BrainrotModel(id, name, rarity, cost, modificator, earn);
        brainrotRepo.addBrainrot(brainrot);

        sender.sendMessage(parse("<prefix>Вы <green>успешно <white>создали нового <blue>бреинрота<white>!"));
        sender.sendMessage(parse("<prefix>Айди: <blue>" + id));
        sender.sendMessage(parse("<prefix>Имя: <blue>" + name));
        sender.sendMessage(parse("<prefix>Редкость: <blue>" + rarity));
        sender.sendMessage(parse("<prefix>Стоимость: <blue>" + cost));
        sender.sendMessage(parse("<prefix>Модификатор: <blue>" + modificator));
        sender.sendMessage(parse("<prefix>Прибыль: <blue>" + earn));
    }
}