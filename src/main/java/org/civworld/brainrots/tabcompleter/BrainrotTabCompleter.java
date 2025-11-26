package org.civworld.brainrots.tabcompleter;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.civworld.brainrots.model.BrainrotModel;
import org.civworld.brainrots.model.Lobby;
import org.civworld.brainrots.repo.BrainrotRepo;
import org.civworld.brainrots.repo.LobbyRepo;
import org.civworld.brainrots.type.Modificator;
import org.civworld.brainrots.type.Rarity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class BrainrotTabCompleter implements TabCompleter {
    private final BrainrotRepo brainrotRepo;
    private final LobbyRepo lobbyRepo;

    public BrainrotTabCompleter(BrainrotRepo brainrotRepo, LobbyRepo lobbyRepo){
        this.brainrotRepo = brainrotRepo;
        this.lobbyRepo = lobbyRepo;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            suggestions.addAll(List.of("create", "hitbox", "marginbottom", "delete", "info", "list", "house", "lobby", "force"));
            return filter(args[0], suggestions);
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create" -> {
                if (args.length == 2) return List.of("[" + args[1] + "]");
                if (args.length == 3) {
                    suggestions.addAll(
                            java.util.Arrays.stream(Rarity.values())
                                    .map(Rarity::name)
                                    .toList()
                    );
                }
                if (args.length == 4) {
                    int num;
                    try {
                        num = Integer.parseInt(args[3]);
                    } catch (NumberFormatException e) {
                        return List.of("[не число]");
                    }

                    String formatted = NumberFormat.getNumberInstance(Locale.US).format(num);
                    return List.of("[" + formatted + "]");
                }

                if (args.length == 5) {
                    int num;
                    try {
                        num = Integer.parseInt(args[4]);
                    } catch (NumberFormatException e) {
                        return List.of("[не число]");
                    }

                    String formatted = NumberFormat.getNumberInstance(Locale.US).format(num);
                    return List.of("[" + formatted + "]");
                }
                if (args.length > 6) return List.of("[" + String.join(" ", Arrays.copyOfRange(args, 5, args.length)) + "]");
            }
            case "hitbox" -> {
                if(args.length == 2) for(BrainrotModel brainrotModel : brainrotRepo.getBrainrots()) suggestions.add(brainrotModel.getId());
                if(args.length == 3 || args.length == 4){
                    double num;
                    try {
                        num = Double.parseDouble(args[2]);
                    } catch (NumberFormatException e) {
                        return List.of("[не число]");
                    }

                    return List.of("[" + num + "]");
                }
            }
            case "marginbottom" -> {
                if(args.length == 2) for(BrainrotModel brainrotModel : brainrotRepo.getBrainrots()) suggestions.add(brainrotModel.getId());
                if(args.length == 3){
                    double num;
                    try {
                        num = Double.parseDouble(args[2]);
                    } catch (NumberFormatException e) {
                        return List.of("[не число]");
                    }

                    return List.of("[" + num + "]");
                }
            }
            case "delete", "info" -> {
                if(args.length == 2) for(BrainrotModel brainrotModel : brainrotRepo.getBrainrots()) suggestions.add(brainrotModel.getId());
            }
            case "lobby" -> {
                if(args.length == 2){
                    suggestions.addAll(List.of("create", "delete"));
                }
                if(args.length == 3){
                    switch(args[1].toLowerCase()){
                        case "create" -> {
                            int num;
                            try {
                                num = Integer.parseInt(args[2]);
                            } catch (NumberFormatException e) {
                                return List.of("[не число]");
                            }

                            return List.of("[" + num + "]");
                        }
                        case "delete" -> {
                            for(Lobby lobby : lobbyRepo.getLobbies()) suggestions.add(lobby.getNum() + "");
                        }
                    }
                }
            }
            case "force" -> {
                if(args.length == 2) for(BrainrotModel brainrotModel : brainrotRepo.getBrainrots()) suggestions.add(brainrotModel.getId());
                if(args.length == 3) suggestions.addAll(
                            java.util.Arrays.stream(Modificator.values())
                                    .map(Modificator::name)
                                    .toList());
                if(args.length == 4) {
                    for(Lobby lobby : lobbyRepo.getLobbies()){
                        suggestions.add(lobby.getNum() + "");
                    }
                    suggestions.add("all");
                }
            }
        }

        return filter(args[args.length - 1], suggestions);
    }

    private List<String> filter(String input, List<String> base) {
        String lower = input.toLowerCase();
        return base.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .sorted()
                .collect(Collectors.toList());
    }
}