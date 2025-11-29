package org.civworld.brainrots.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.civworld.brainrots.config.Config;
import org.civworld.brainrots.model.House;
import org.civworld.brainrots.model.Lobby;
import org.civworld.brainrots.repo.LobbyRepo;

import static org.civworld.brainrots.util.Utils.deleteHologram;

public class PlayerListener implements Listener {
    private final LobbyRepo lobbyRepo;
    private final Config config;

    public PlayerListener(LobbyRepo lobbyRepo, Config config){
        this.lobbyRepo = lobbyRepo;
        this.config = config;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event){
        config.loadPlayerData(event.getPlayer());
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event){
        Player player = event.getPlayer();
        for(Lobby lobby : lobbyRepo.getLobbies()){
            for(House house : lobby.getHouses()){
                if(house.getPlayerData() != null && house.getPlayerData().getPlayer() != null
                        && house.getPlayerData().getPlayer().equals(player)) {
                    house.setPlayerData(null);
                    deleteHologram(lobby, house, "owner");
                    deleteHologram(lobby, house, "plate");
                }
            }
        }

        config.savePlayerOnQuit(player);
    }
}