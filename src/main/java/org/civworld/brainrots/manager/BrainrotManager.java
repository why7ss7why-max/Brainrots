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

        String id = args[1].toLowerCase();
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
            sender.sendMessage(parse("<prefix>Использование: <blue>/br delete <айди>"));
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
}