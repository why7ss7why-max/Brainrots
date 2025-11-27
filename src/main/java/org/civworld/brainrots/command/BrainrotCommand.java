package org.civworld.brainrots.command;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.civworld.brainrots.manager.BrainrotManager;
import org.civworld.brainrots.manager.CbManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static org.civworld.brainrots.util.Utils.parse;

public class BrainrotCommand implements CommandExecutor {
    private final BrainrotManager brainrotManager;
    private final CbManager cbManager;
    private final Plugin plugin;

    public BrainrotCommand(BrainrotManager brainrotManager, CbManager cbManager, Plugin plugin){
        this.brainrotManager = brainrotManager;
        this.cbManager = cbManager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if(!sender.hasPermission("brainrots.admin")){
            sender.sendMessage(parse("<prefix>Вы <red>не администратор<white>."));
            return true;
        }

        if(args.length < 1){
            helpMessage(sender);
            return true;
        }

        switch(args[0].toLowerCase()){
            case "create" -> brainrotManager.createBrainrot(sender, args);
            case "hitbox" -> brainrotManager.hitboxBrainrot(sender, args);
            case "marginbottom" -> brainrotManager.marginBottomBrainrot(sender, args);
            case "delete" -> brainrotManager.deleteBrainrot(sender, args);
            case "list" -> brainrotManager.listBrainrots(sender);
            case "info" -> brainrotManager.infoBrainrots(sender, args);
            case "lobby" -> brainrotManager.handleLobbyCommand(sender, args);
            case "force" -> brainrotManager.force(sender, args);
            case "commandblock" -> cbManager.handleMainCmd(sender, args);
            case "deletenontickingentity" -> removeNonTickingEntitiesBatched(sender, plugin);
            case "house" -> brainrotManager.handleHouseCommand(sender, args);
//            case "give" -> brainrotManager.giveBrainrot(sender, args);
            default -> helpMessage(sender);
        }

        return true;
    }


    public void removeNonTickingEntitiesBatched(CommandSender sender, Plugin plugin) {
        List<Entity> allEntities = new ArrayList<>();

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof Player) continue;
                    if (!entity.isTicking()) allEntities.add(entity);
                }
            }

            sender.sendMessage(parse("<prefix>Найдено <green>" + allEntities.size() + "<white> non-ticking сущностей. Начинаем удаление..."));

            final int batchSize = 50;
            for (int i = 0; i < allEntities.size(); i += batchSize) {
                int start = i;
                int end = Math.min(i + batchSize, allEntities.size());
                List<Entity> batch = allEntities.subList(start, end);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (Entity e : batch) e.remove();
                }, (i / batchSize));
            }

            Bukkit.getScheduler().runTaskLater(plugin, () ->
                            sender.sendMessage(parse("<prefix>Удалено <green>" + allEntities.size() + "<white> non-ticking сущностей!")),
                    (allEntities.size() / batchSize) + 1
            );
        });
    }

    private void helpMessage(CommandSender sender){
        sender.sendMessage(parse("<prefix>Использование:"));
        sender.sendMessage(parse("<prefix><blue>/bt create <айди> <редкость> <стоимость> <прибыль> <название> <gray>- <white>Создать <gray>нового <green>бреинрота"));
        sender.sendMessage(parse("<prefix><blue>/bt hitbox <айди> <ширина> <высота> <gray>- <white>Изменить <gray>хитбокс <green>бреинрота"));
        sender.sendMessage(parse("<prefix><blue>/bt marginbottom <айди> <высота> <gray>- <white>Изменить <gray>длину <white>голограммы <green>бреинрота"));
        sender.sendMessage(parse("<prefix><blue>/bt force <айди> <модификатор> <лобби|all> <gray>- <white>Заспавнить <gray>принудительно <green>бреинрота"));
        sender.sendMessage(parse("<prefix><blue>/bt delete <айди> <gray>- <white>Удалить <red>бреинрот"));
        sender.sendMessage(parse("<prefix><blue>/bt info <айди> <gray>- <white>Узнать <gray>информацию <white>о <yellow>бреинроте"));
        sender.sendMessage(parse("<prefix><blue>/bt list <gray>- <white>Посмотреть <gray>все <green>бреинроты"));
        sender.sendMessage(parse("<prefix><blue>/bt house create <айди> <лобби> <правый> <gray>- <white>Создать <gray>новый <green>дом"));
        sender.sendMessage(parse("<prefix><blue>/bt house delete <лобби> <айди> <gray>- <white>Удалить <red>дом"));
        sender.sendMessage(parse("<prefix><blue>/bt house list <лобби> <gray>- <white>Список <gold>домов"));
        sender.sendMessage(parse("<prefix><blue>/bt lobby create <айди> <gray>- <white>Создать <gray>новое <g>лобби"));
        sender.sendMessage(parse("<prefix><blue>/bt lobby delete <айди> <gray>- <white>Удалить <red>лобби"));
    }
}