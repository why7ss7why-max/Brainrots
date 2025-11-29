package org.civworld.brainrots.manager;

import net.citizensnpcs.api.npc.NPC;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.civworld.brainrots.Brainrots;
import org.civworld.brainrots.data.PlayerData;
import org.civworld.brainrots.model.BrainrotModel;
import org.civworld.brainrots.model.House;
import org.civworld.brainrots.model.Lobby;
import org.civworld.brainrots.puller.Puller;
import org.civworld.brainrots.repo.BrainrotRepo;
import org.civworld.brainrots.repo.LobbyRepo;
import org.civworld.brainrots.type.Modificator;
import org.civworld.brainrots.type.Rarity;

import java.util.Arrays;
import java.util.Comparator;

import static org.civworld.brainrots.util.Utils.parse;

public class BrainrotManager {
    private final BrainrotRepo brainrotRepo;
    private final LobbyRepo lobbyRepo;
    private final Puller puller;

    public BrainrotManager(BrainrotRepo brainrotRepo, LobbyRepo lobbyRepo, Puller puller){
        this.brainrotRepo = brainrotRepo;
        this.lobbyRepo = lobbyRepo;
        this.puller = puller;
    }

    public void force(CommandSender sender, String[] args) {
        if(args.length < 4){
            sender.sendMessage(parse("<prefix>Использование: <blue>/bt force <айди> <модификатор> <лобби|all>"));
            return;
        }

        String id = args[1];
        if (!brainrotRepo.hasBrainrotById(id)) {
            sender.sendMessage(parse("<prefix>Бреинрот <red>не найден<white>!"));
            return;
        }

        BrainrotModel brainrotModel = brainrotRepo.getById(id);
        if (brainrotModel == null) {
            sender.sendMessage(parse("<prefix>Произошла <red>ошибка<white>. Свяжитесь с <green>администратором<white>!"));
            return;
        }

        Modificator modificator;
        try {
            modificator = Modificator.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(parse("<prefix>Вы <gray>ввели <red>неверный <white>модификатор!"));
            StringBuilder stringBuilder = new StringBuilder();
            for(Modificator m : Modificator.values()){
                stringBuilder.append(m).append(" ");
            }
            sender.sendMessage(parse("<prefix>Доступные: <blue>" + stringBuilder));
            return;
        }
        brainrotModel.setModificator(modificator);

        if(!args[3].equals("all")){
            int lobby;
            try{
                lobby = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(parse("<prefix>Вы <red>не ввели <white>лобби!"));
                return;
            }

            if(lobbyRepo.getByNumber(lobby) == null){
                sender.sendMessage(parse("<prefix>Лобби <red>не найдено<white>!"));
                return;
            }

            puller.forceNext(lobby, brainrotRepo.getById(id));
        } else {
            puller.forceNextAll(brainrotRepo.getById(id));
        }
    }

    public void createBrainrot(CommandSender sender, String[] args){
        if(args.length < 6){
            sender.sendMessage(parse("<prefix>Использование: <blue>/bt create <айди> <редкость> <стоимость> <прибыль> <название>"));
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

        int earn;
        try{
            earn = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(parse("<prefix>Вы <gray>ввели <red>неверную <white>прибыль!"));
            return;
        }

        String name = String.join(" ", Arrays.copyOfRange(args, 5, args.length));

        BrainrotModel brainrot = new BrainrotModel(id, name, rarity, cost, earn);
        brainrotRepo.addBrainrot(brainrot);

        sender.sendMessage(parse("<prefix>Вы <green>успешно <white>создали нового <blue>бреинрота<white>!"));
        sender.sendMessage(parse("<prefix>Айди: <blue>" + id));
        sender.sendMessage(parse("<prefix>Имя: <blue>" + name));
        sender.sendMessage(parse("<prefix>Редкость: <blue>" + rarity));
        sender.sendMessage(parse("<prefix>Стоимость: <blue>" + cost + "$"));
        sender.sendMessage(parse("<prefix>Прибыль: <blue>" + earn + "/с $"));
    }

    public void deleteBrainrot(CommandSender sender, String[] args){
        if(args.length < 2){
            sender.sendMessage(parse("<prefix>Использование: <blue>/bt delete <айди>"));
            return;
        }

        String id = args[1];
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

    public void handleHouseCommand(CommandSender sender, String[] args){
        if(args.length < 2){
            helpHouseCommand(sender);
            return;
        }

        switch(args[1].toLowerCase()){
            case "create" -> {
                if(!(sender instanceof Player player)){
                    sender.sendMessage(parse("<prefix>Вы <red>не игрок<white>!"));
                    return;
                }

                if(args.length < 5){
                    player.sendMessage(parse("<prefix>Использование: <blue>/bt house create <айди> <лобби> <правый>"));
                    return;
                }

                int id;
                try{
                    id = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage(parse("<prefix>Вы <red>не ввели <white>айди!"));
                    return;
                }

                int lobby;
                try{
                    lobby = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    player.sendMessage(parse("<prefix>Вы <red>не ввели <white>лобби!"));
                    return;
                }
                Lobby lobbyModel = lobbyRepo.getByNumber(lobby);
                if(lobbyModel == null){
                    player.sendMessage(parse("<prefix>Лобби <red>не найдено<white>!"));
                    return;
                }

                for(House house : lobbyModel.getHouses()){
                    if(house.getId() == id){
                        player.sendMessage(parse("<prefix>Дом с айди <gold>" + id + "<white> уже <red>существует<white>!"));
                        return;
                    }
                }

                boolean right;
                try{
                    right = Boolean.parseBoolean(args[4]);
                } catch (Exception e) {
                    player.sendMessage(parse("<prefix>Доступные <yellow>значения <white>последнего аргумента: <blue>true/false"));
                    return;
                }

                House house = new House(player.getLocation(), id, right);
                lobbyModel.getHouses().add(house);
                player.sendMessage(parse("<prefix>Вы <green>успешно <white>создали <gold>дом<white>!"));
            }
            case "delete" -> {
                if(args.length < 4){
                    sender.sendMessage(parse("<prefix>Использование: <blue>/bt house delete <лобби> <айди>"));
                    return;
                }

                int id;
                try{
                    id = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(parse("<prefix>Вы <red>не ввели <white>айди!"));
                    return;
                }


                int lobby;
                try{
                    lobby = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(parse("<prefix>Вы <red>не ввели <white>лобби!"));
                    return;
                }

                Lobby lobbyModel = lobbyRepo.getByNumber(lobby);
                if(lobbyModel == null) {
                    sender.sendMessage(parse("<prefix>Лобби <red>не найдено<white>!"));
                    return;
                }

                House house = lobbyModel.getHouses().stream().filter(h -> h.getId() == id).findFirst().orElse(null);
                if(house == null) {
                    sender.sendMessage(parse("<prefix>Дом с айди <gold>" + id + "<white> не <red>найден<white>!"));
                    return;
                }

                lobbyModel.getHouses().remove(house);
                sender.sendMessage(parse("<prefix>Вы <green>успешно <white>удалили <gold>дом<white>!"));
            }
            case "list" -> {
                if(args.length < 3){
                    sender.sendMessage(parse("<prefix>Использование: <blue>/bt house list <лобби>"));
                    return;
                }

                if(lobbyRepo.getLobbies().isEmpty()){
                    sender.sendMessage(parse("<prefix>Лобби-список <red>пуст<white>!"));
                    return;
                }

                int lobby;
                try{
                    lobby = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(parse("<prefix>Вы <red>не ввели <white>лобби!"));
                    return;
                }

                Lobby lobbyModel = lobbyRepo.getByNumber(lobby);
                if(lobbyModel == null) {
                    sender.sendMessage(parse("<prefix>Лобби <red>не найдено<white>!"));
                    return;
                }

                StringBuilder stringBuilder = new StringBuilder();

                if (lobbyModel.getHouses().isEmpty()) {
                    stringBuilder.append("<red>пусто");
                } else {
                    lobbyModel.getHouses().stream()
                            .sorted(Comparator.comparingInt(House::getId))
                            .forEach(house -> {
                                Location loc = house.getPlateCloseDoor();

                                stringBuilder.append("\n<gray>- <white>Дом <blue>")
                                        .append(house.getId())
                                        .append(" <dark_gray>(<gray>правый: ")
                                        .append(house.isRight())
                                        .append("<dark_gray>) ")
                                        .append("<hover:show_text:'<white>Нажмите <gray>[<blue>ЛКМ<gray>]<white>, чтобы <blue>телепортироваться'>")
                                        .append("<click:run_command:'/minecraft:tp ")
                                        .append(sender.getName()).append(" ")
                                        .append(loc.getBlockX()).append(" ")
                                        .append(loc.getBlockY()).append(" ")
                                        .append(loc.getBlockZ()).append(" ")
                                        .append(loc.getYaw()).append(" ")
                                        .append(loc.getPitch())
                                        .append("'>")
                                        .append("<blue>[ТП]")
                                        .append("</click></hover>");
                            });
                }

                sender.sendMessage(parse("<prefix>Список <gold>домов<white> в лобби <yellow>" + lobby + " <gray>: <blue>" + stringBuilder));
            }
            default -> helpHouseCommand(sender);
        }
    }

    private void helpHouseCommand(CommandSender sender){
        sender.sendMessage(parse("<prefix>Использование:"));
        sender.sendMessage(parse("<prefix><blue>/bt house create <айди> <лобби> <правый> <gray>- <white>Создать <gold>дом"));
        sender.sendMessage(parse("<prefix><blue>/bt house delete <айди> <лобби> <gray>- <white>Удалить <gold>дом"));
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

                if(lobbyRepo.hasByNumber(id)){
                    player.sendMessage(parse("<prefix>Лобби с ID <blue>" + id + " <white>уже <red>существует<white>!"));
                    return;
                }

                Location loc = player.getLocation();

                lobbyRepo.addLobby(new Lobby(loc, id));
                sender.sendMessage(parse("<prefix>Вы <green>успешно <white>создали <gray>новое <white>лобби!"));
            }
            case "delete" -> {
                if(!(sender instanceof Player player)){
                    sender.sendMessage(parse("<prefix>Создать <gray>лобби <white>можно только <red>от игрока<white>!"));
                    return;
                }

                if(args.length < 3){
                    player.sendMessage(parse("<prefix>Использование: <blue>/bt lobby delete <айди>"));
                    return;
                }

                int id;
                try{
                    id = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage(parse("<prefix>Вы ввели <red>неверный <white>ID!"));
                    return;
                }

                if(!lobbyRepo.hasByNumber(id)){
                    player.sendMessage(parse("<prefix>Лобби <red>не найдено<white>!"));
                    return;
                }

                Lobby lobby = lobbyRepo.getByNumber(id);
                if(lobby == null){
                    player.sendMessage(parse("<prefix>Произошла <red>ошибка<white>! Свяжитесь с <green>администратором<white>."));
                    return;
                }

                lobbyRepo.getLobbies().remove(lobby);

                sender.sendMessage(parse("<prefix>Вы <green>успешно <white>удалили <gray>лобби <white>с ID: <blue>" + id + "<white>!"));
            }
            case "list" -> {
                if(lobbyRepo.getLobbies().isEmpty()){
                    sender.sendMessage(parse("<prefix>Лобби-список <red>пуст<white>!"));
                    return;
                }

                StringBuilder stringBuilder = new StringBuilder();

                for(Lobby lobby : lobbyRepo.getLobbies()){
                    stringBuilder.append(lobby.getNum()).append(" ");
                }

                sender.sendMessage(parse("<prefix>Список лобби: <blue>" + stringBuilder + "<gray>(<blue>" + lobbyRepo.getLobbies().size() + "<gray>)"));
            }
            default -> helpLobbyCommand(sender);
        }
    }

    public void hitboxBrainrot(CommandSender sender, String[] args){
        if(args.length < 4){
            sender.sendMessage(parse("<prefix>Использование: <blue>/bt hitbox <айди> <ширина> <высота>"));
            return;
        }

        String id = args[1];
        if(!brainrotRepo.hasBrainrotById(id)){
            sender.sendMessage(parse("<prefix>Бреинрот <red>не найден<white>."));
            sender.sendMessage(parse("<prefix>Используйте: <blue>/bt list <white>для <green>просмотра <white>списка <blue>бреинротов<white>."));
            return;
        }

        double width;
        try{
            width = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(parse("<prefix>Вы ввели <red>неверную <white>ширину!"));
            return;
        }

        double height;
        try{
            height = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(parse("<prefix>Вы ввели <red>неверную <white>высоту!"));
            return;
        }

        BrainrotModel brainrotModel = brainrotRepo.getById(id);
        if(brainrotModel == null){
            sender.sendMessage(parse("<prefix>Произошла <red>ошибка <white>с получением <blue>бреинрота<white>."));
            sender.sendMessage(parse("<prefix>Свяжитесь с <green>администратором<white>."));
            return;
        }

        brainrotModel.setWidthHitbox(width);
        brainrotModel.setHeightHitbox(height);
        sender.sendMessage(parse("<prefix>Вы <green>успешно <white>установили <gray>хитбокс <white>бреинрота!"));
        sender.sendMessage(parse("<prefix>Ширина: <yellow>" + width));
        sender.sendMessage(parse("<prefix>Высота: <yellow>" + height));

        for(NPC npc : puller.getWalkingNpc().keySet()){
            Pair<BrainrotModel, Modificator> pair = puller.getWalkingNpc().get(npc);
            BrainrotModel brainrot = pair.getKey();

            if(brainrot.getId().equals(brainrotModel.getId())){
                CommandSender console = Bukkit.getConsoleSender();
                Bukkit.dispatchCommand(console, "npc select " + npc.getId());
                Bukkit.dispatchCommand(console, "npc hitbox --width " + brainrot.getWidthHitbox() + " --height " + brainrot.getHeightHitbox());
            }
        }
    }

    public void marginBottomBrainrot(CommandSender sender, String[] args){
        if(args.length < 3){
            sender.sendMessage(parse("<prefix>Использование: <blue>/bt marginbottom <айди> <высота>"));
            return;
        }

        String id = args[1];
        if(!brainrotRepo.hasBrainrotById(id)){
            sender.sendMessage(parse("<prefix>Бреинрот <red>не найден<white>."));
            sender.sendMessage(parse("<prefix>Используйте: <blue>/bt list <white>для <green>просмотра <white>списка <blue>бреинротов<white>."));
            return;
        }

        double margin;
        try{
            margin = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(parse("<prefix>Вы ввели <red>неверную <white>высоту!"));
            return;
        }

        BrainrotModel brainrotModel = brainrotRepo.getById(id);
        if(brainrotModel == null){
            sender.sendMessage(parse("<prefix>Произошла <red>ошибка <white>с получением <blue>бреинрота<white>."));
            sender.sendMessage(parse("<prefix>Свяжитесь с <green>администратором<white>."));
            return;
        }

        brainrotModel.setMarginHologram(margin);
        sender.sendMessage(parse("<prefix>Вы <green>успешно <white>установили <gray>margin-bottom <white>бреинрота!"));
        sender.sendMessage(parse("<prefix>Высота: <yellow>" + margin));

        for(NPC npc : puller.getWalkingNpc().keySet()){
            Pair<BrainrotModel, Modificator> pair = puller.getWalkingNpc().get(npc);
            BrainrotModel brainrot = pair.getKey();

            if(brainrot.getId().equals(brainrotModel.getId())){
                CommandSender console = Bukkit.getConsoleSender();
                Bukkit.dispatchCommand(console, "npc select " + npc.getId());
                Bukkit.dispatchCommand(console, "npc hologram marginbottom 0 " + brainrot.getMarginHologram());
            }
        }
    }

    public void helpLobbyCommand(CommandSender sender){
        sender.sendMessage(parse("<prefix>Использование:"));
        sender.sendMessage(parse("<prefix><blue>/bt lobby create <айди> <gray>- <white>Создать <gray>новое <blue>лобби"));
        sender.sendMessage(parse("<prefix><blue>/bt lobby delete <айди> <gray>- <white>Удалить <red>лобби"));
        sender.sendMessage(parse("<prefix><blue>/bt lobby list <gray>- <white>Список <yellow>лобби"));
    }

    public void confirmRemove(CommandSender sender, String[] args){
        if(!(sender instanceof Player player)){
            sender.sendMessage(parse("<prefix>Команду можно использовать только игроку."));
            return;
        }

        if(args.length < 2){
            player.sendMessage(parse("<prefix>Использование: <blue>/brainrot confirmremove <слот>"));
            return;
        }

        int slot;
        try{
            slot = Integer.parseInt(args[1]);
        } catch (NumberFormatException e){
            player.sendMessage(parse("<prefix>Вы ввели <red>неверный <white>номер слота!"));
            return;
        }

        House house = null;
        PlayerData pd = null;
        for(Lobby l : lobbyRepo.getLobbies()){
            for(House h : l.getHouses()){
                if(h.getPlayerData() == null) continue;
                if(h.getPlayerData().getPlayer().equals(player)){
                    house = h;
                    pd = h.getPlayerData();
                    break;
                }
            }
            if(house != null) break;
        }

        if(house == null || pd == null){
            player.sendMessage(parse("<prefix>Вы <red>не состоите<white> в <blue>лобби<white>!"));
            return;
        }

        if(slot < 0 || slot >= pd.getOwnBreinrots().size() || pd.getOwnBreinrots().get(slot) == null || pd.getOwnBreinrots().get(slot).getLeft() == null){
            player.sendMessage(parse("<prefix>В этом слоте нет бреинрота."));
            return;
        }

        BrainrotModel model = pd.getOwnBreinrots().get(slot).getLeft();
        String display = model != null ? model.getDisplayName() : "<unknown>";

        pd.removeBrainrot(slot);
        try { puller.updateHomeBrainrots(house); } catch (Throwable ignored) {}

        try {
            var pl = Bukkit.getPluginManager().getPlugin("Brainrots");
            if (pl instanceof Brainrots) {
                ((Brainrots) pl).getConfigManager().savePlayerData(player);
            }
        } catch (Throwable ignored) {}

        player.sendMessage(parse("<prefix>Вы <green>удалили<white> бреинрота: <yellow>" + display));
    }
}