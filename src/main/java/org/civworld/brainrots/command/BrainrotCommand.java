package org.civworld.brainrots.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.civworld.brainrots.manager.BrainrotManager;
import org.jetbrains.annotations.NotNull;

import static org.civworld.brainrots.util.Utils.parse;

public class BrainrotCommand implements CommandExecutor {
    private final BrainrotManager brainrotManager;

    public BrainrotCommand(BrainrotManager brainrotManager){
        this.brainrotManager = brainrotManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if(args.length < 1){
            helpMessage(sender);
            return true;
        }

        switch(args[0].toLowerCase()){
            case "create" -> brainrotManager.createBrainrot(sender, args);
//            case "give" -> brainrotManager.giveBrainrot(sender, args);
            default -> helpMessage(sender);
        }


        return true;
    }

    private void helpMessage(CommandSender sender){
        sender.sendMessage(parse("<prefix>Использование:"));
        sender.sendMessage(parse("<prefix><blue>/br create <айди> <редкость> <стоимость> <мод> <прибыль> <название> <gray>- <white>Создать <gray>нового <green>бреинрота"));
        sender.sendMessage(parse("<prefix><blue>/br give <название> <модификатор> <gray>- <white>Выдать <gray>себе <green>бреинрота"));
    }
}