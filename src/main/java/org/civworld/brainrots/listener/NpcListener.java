package org.civworld.brainrots.listener;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.CitizensEnableEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.milkbowl.vault.economy.Economy;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.civworld.brainrots.config.Config;
import org.civworld.brainrots.data.PlayerData;
import org.civworld.brainrots.model.BrainrotModel;
import org.civworld.brainrots.model.House;
import org.civworld.brainrots.model.Lobby;
import org.civworld.brainrots.puller.Puller;
import org.civworld.brainrots.repo.LobbyRepo;
import org.civworld.brainrots.type.Modificator;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.civworld.brainrots.util.Utils.*;

public class NpcListener implements Listener {
    private final Economy economy;
    private final Puller puller;
    private final LobbyRepo lobbyRepo;
    private final Config config;

    private final Map<NPC, Long> npcSoundCooldowns = new ConcurrentHashMap<>();
    private static final long SOUND_COOLDOWN = 1000;

    public NpcListener(Economy economy, Puller puller, LobbyRepo lobbyRepo, Config config){
        this.economy = economy;
        this.puller = puller;
        this.lobbyRepo = lobbyRepo;
        this.config = config;
    }

    @EventHandler
    public void onLoad(CitizensEnableEvent event){
        for(NPC npc : CitizensAPI.getNPCRegistry()){
            if(npc.getName().equals("putevoditel") || npc.getName().equals("quests")) continue;
            npc.despawn();
            npc.destroy();
            CitizensAPI.getNPCRegistry().deregister(npc);
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "citizens save");
    }

    @EventHandler
    public void onClickDelete(NPCRightClickEvent event){
        Player clicker = event.getClicker();
        NPC npc = event.getNPC();

        House house = null;
        PlayerData playerData = null;

        for (Lobby l : lobbyRepo.getLobbies()) {
            for (House h : l.getHouses()) {
                PlayerData pd = h.getPlayerData();
                if (pd == null) continue;
                if (pd.getPlayer().equals(clicker)) {
                    playerData = pd;
                    house = h;
                    break;
                }
            }
            if (house != null) break;
        }

        if (playerData == null) return;

        try {
            if (!npc.data().has("home") || !npc.data().get("home").equals(playerData.getPlayer().getName())) {
                return;
            }
        } catch (Throwable ignored) { return; }

        int slot = -1;
        for (int i = 0; i < 10; i++){
            Location expected = puller.getLocationByIdBrainrot(i, house.getPlateCloseDoor(), house.isRight());
            try{
                if (expected == null) continue;
                Location cur = npc.getStoredLocation();
                if (cur == null) continue;
                if (cur.getWorld() == null || !cur.getWorld().equals(expected.getWorld())) continue;
                if (cur.distance(expected) <= 1.5) { slot = i; break; }
            } catch (Throwable ignored){}
        }

        if (slot == -1) {
            clicker.sendMessage(parse("<prefix>Не удалось определить слот для продажи."));
            return;
        }

        Pair<BrainrotModel, Modificator> pair = null;
        if (slot < playerData.getOwnBreinrots().size()) pair = playerData.getOwnBreinrots().get(slot);
        if (pair == null || pair.getKey() == null) {
            clicker.sendMessage(parse("<prefix>В этом слоте нет бреинрота."));
            return;
        }

        BrainrotModel model = pair.getKey();

        if (clicker.isSneaking()) {
            try {
                String display = model.getDisplayName();
                boolean auto = playerData.isAutoSell();

                if (auto) {
                    try { Bukkit.dispatchCommand(clicker, "brainrot confirmremove " + slot); } catch (Throwable ignored) {}
                }

                String confirmMsg = "<prefix>Вы уверены, что хотите удалить бреинрота: <yellow>" + capitalizeFirst(display) + "? ";
                clicker.sendMessage(parse(confirmMsg));

                try {
                    String displayEsc = capitalizeFirst(display).replace("'","\\'");
                    String box = auto ? "✓" : " ";

                    clicker.sendMessage(parse("<hover:show_text:'<white>" +
                            "Удалить бреинрота " + displayEsc + "'><click:run_command:'/brainrot confirmremove " + slot + "'>" +
                            "<gray>[<green>Подтвердить<gray>]</click></hover> " + "<hover:show_text:'<white>Переключить автопродажу'><click:run_command:'/brainrot autosell'>" +
                            "[" + box + "] Авто продажа</click></hover>"));
                } catch (Throwable ignored) {
                    clicker.sendMessage(parse("<prefix>Используйте команду: /brainrot confirmremove " + slot));
                }
            } catch (Throwable ignored) {
                clicker.sendMessage(parse("<prefix>Используйте команду: /brainrot confirmremove " + slot));
            }
            return;
        }

        double stored = playerData.getStoredAmount(slot);
        if (stored <= 0.0) {
            return;
        }

        int lobbyNum = -1;
        for (Lobby lb : lobbyRepo.getLobbies()){
            for (House h : lb.getHouses()){
                if (h.getId() == house.getId()) { lobbyNum = lb.getNum(); break; }
            }
            if (lobbyNum != -1) break;
        }
        if (lobbyNum == -1) return;

        economy.depositPlayer(clicker, stored);
        playerData.setStoredAmount(slot, 0.0);
        try { config.savePlayerData(clicker); } catch (Throwable ignored) {}

        puller.updateBalanceHologram(house, slot);

        clicker.sendActionBar(parse("<green>+" + formatWithCommas(stored) + "$"));
    }

    public static String formatWithCommas(double number) {
        DecimalFormat df = new DecimalFormat("#,###.########");
        return df.format(number);
    }

    @EventHandler
    public void onClick(NPCRightClickEvent event) {
        Player clicker = event.getClicker();
        NPC npc = event.getNPC();

        House house = null;
        PlayerData playerData = null;

        for (Lobby l : lobbyRepo.getLobbies()) {
            for (House h : l.getHouses()) {
                PlayerData pd = h.getPlayerData();
                if (pd == null) continue;
                if (pd.getPlayer().equals(clicker)) {
                    playerData = pd;
                    house = h;
                    break;
                }
            }
            if (house != null) break;
        }

        if (playerData == null) {
            clicker.sendMessage(parse("<prefix>Вы <red>не состоите<white> в <blue>лобби<white>!"));
            return;
        }

        Pair<BrainrotModel, Modificator> pair = puller.getWalkingNpc().getOrDefault(npc, null);
        if (pair == null) return;

        BrainrotModel brainrotModel = pair.getKey();
        if (brainrotModel == null) return;

        Modificator modificator = pair.getValue();
        double cost = modificator == Modificator.BRONZE ? brainrotModel.getCost() : brainrotModel.getCost() * modificator.getValue();
        String costFormatted = formatDouble(cost);

        if (economy.getBalance(clicker) < cost) {
            clicker.sendMessage(parse("<prefix>Недостаточно <red>монет<white>!"));
            return;
        }

        if(puller.isNpcGoingToHouse(npc, house.getId())) return;

        PlayerData pd = house.getPlayerData();
        int freeSlot = -1;
        for (int i = 0; i < 10; i++) {
            boolean empty = (i >= pd.getOwnBreinrots().size() || pd.getOwnBreinrots().get(i) == null);
            boolean reserved = puller.isSlotReserved(clicker, i, house.getId());
            if (empty && !reserved) { freeSlot = i; break; }
        }
        if (freeSlot == -1) {
            clicker.sendMessage(parse("<prefix>У вас <red>недостаточно <white>места!"));
            return;
        }

        clicker.sendMessage(parse("<prefix>Вам <green>хватает <white>монет: <blue>" + costFormatted));

        long now = System.currentTimeMillis();
        Long last = npcSoundCooldowns.get(npc);

        if (last == null || now - last >= SOUND_COOLDOWN) {
            for (Player p : npc.getEntity().getWorld().getPlayers()) {
                if (p.getLocation().distance(npc.getStoredLocation()) <= 16) {
                    p.playSound(
                            npc.getStoredLocation(),
                            "brainrot:sound." + brainrotModel.getId(),
                            SoundCategory.BLOCKS,
                            1.0f,
                            1.0f
                    );
                }
            }
            npcSoundCooldowns.put(npc, now);
        }

        Location newLoc = house.getPlateCloseDoor().clone().add(0, -1, house.isRight() ? 26 : -26);
        // Резервируем слот до того, как NPC пойдёт, чтобы учесть его при последующих покупках
        puller.reserveSlot(npc, clicker, house.getId(), freeSlot);
        economy.withdrawPlayer(clicker, cost);
        clicker.sendMessage(parse("<prefix>С вас <gold>" + costFormatted + "<white> монет <green>списано<white>!"));
        clicker.sendMessage(parse("<prefix>Нпс идёт по координатам " + newLoc.getX() + " " + newLoc.getY() + " " + newLoc.getZ()));
        puller.moveNpcSegmented(npc, brainrotModel, modificator, newLoc, house.getId(), freeSlot, clicker);
    }

    public static String formatDouble(double value) {
        java.text.DecimalFormat df = new java.text.DecimalFormat("#.################");
        df.setGroupingUsed(false);
        return df.format(value);
    }
}
