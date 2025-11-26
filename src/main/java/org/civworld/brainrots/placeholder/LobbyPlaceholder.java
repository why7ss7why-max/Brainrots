package org.civworld.brainrots.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.civworld.brainrots.data.PlayerData;
import org.civworld.brainrots.model.House;
import org.civworld.brainrots.model.Lobby;
import org.civworld.brainrots.repo.LobbyRepo;

public class LobbyPlaceholder extends PlaceholderExpansion {
    private final LobbyRepo lobbyRepo;

    public LobbyPlaceholder(LobbyRepo lobbyRepo) {
        this.lobbyRepo = lobbyRepo;
    }

    @Override
    public String getAuthor() {
        return "uuun";
    }

    @Override
    public String getIdentifier() {
        return "brainrots";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params.startsWith("lobbyonline_")) {
            String numberPart = params.substring("lobbyonline_".length());
            int numLobby;
            try {
                numLobby = Integer.parseInt(numberPart);
            } catch (NumberFormatException e) {
                return null;
            }

            Lobby lobby = lobbyRepo.getByNumber(numLobby);
            if(lobby == null){
                return "Лобби не существует";
            }

            int sum = 0;
            for(House house : lobby.getHouses()){
                PlayerData playerData = house.getPlayerData();
                if(playerData == null) continue;
                Player houseOwner = playerData.getPlayer();
                if(houseOwner != null) sum++;
            }

            if(sum > 9){
                return "&c" + sum;
            } else {
                return "&a" + sum;
            }
        }
        else if (params.equals("playerlobby")){
            if(player == null) return null;

            for (Lobby l : lobbyRepo.getLobbies()){
                for (House h : l.getHouses()){
                    PlayerData pd = h.getPlayerData();
                    if (pd == null) continue;

                    Player housePlayer = pd.getPlayer();
                    if (housePlayer == null) continue;

                    if (housePlayer.getName().equals(player.getName())){
                        return l.getNum() + "";
                    }
                }
            }

            return "&cнет";
        }
        return null;
    }
}