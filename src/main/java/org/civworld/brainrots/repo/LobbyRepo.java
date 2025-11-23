package org.civworld.brainrots.repo;

import lombok.Getter;
import org.civworld.brainrots.model.Lobby;

import java.util.HashSet;

public class LobbyRepo {
    @Getter private final HashSet<Lobby> lobbies = new HashSet<>();

    public void addLobby(Lobby lobby){
        lobbies.add(lobby);
    }

    public boolean hasByNumber(int id){
        for(Lobby lobby : lobbies){
            if(lobby.getNum() == id) return true;
        }
        return false;
    }

    public Lobby getByNumber(int id){
        for(Lobby lobby : lobbies){
            if(lobby.getNum() == id) return lobby;
        }
        return null;
    }
}