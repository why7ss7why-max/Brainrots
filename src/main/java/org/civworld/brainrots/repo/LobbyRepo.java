package org.civworld.brainrots.repo;

import lombok.Getter;
import org.civworld.brainrots.model.Lobby;

import java.util.HashSet;

public class LobbyRepo {
    @Getter private final HashSet<Lobby> lobbies = new HashSet<>();

    public void addLobby(Lobby lobby){
        lobbies.add(lobby);
    }
}