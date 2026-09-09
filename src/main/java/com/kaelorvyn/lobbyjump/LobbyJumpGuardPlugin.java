package com.kaelorvyn.lobbyjump;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LobbyJumpGuardPlugin extends JavaPlugin implements Listener {
    private final Map<UUID, Long> lastGroundedAt = new ConcurrentHashMap<>();
    private final Set<UUID> airborne = ConcurrentHashMap.newKeySet();
    private final Set<UUID> jumpedThisAir = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Vector> blockedVelocities = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Airborne double-jump toggles blocked");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        lastGroundedAt.put(event.getPlayer().getUniqueId(), System.nanoTime());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastGroundedAt.remove(id);
        airborne.remove(id);
        jumpedThisAir.remove(id);
        blockedVelocities.remove(id);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (player.isOnGround()) {
            lastGroundedAt.put(id, System.nanoTime());
            if (airborne.remove(id)) {
                jumpedThisAir.remove(id);
            }
        } else {
            airborne.add(id);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void guardToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return;
        }

        UUID id = player.getUniqueId();
        if (jumpedThisAir.contains(id) || !player.isOnGround()) {
            event.setCancelled(true);
            blockedVelocities.put(id, player.getVelocity().clone());
            player.setAllowFlight(false);
            return;
        }
        jumpedThisAir.add(id);
    }

    // DoubleJumpZ does not require a cancelled event to be respected, so restore the exact pre-toggle velocity.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void finishBlockedToggle(PlayerToggleFlightEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Vector originalVelocity = blockedVelocities.remove(id);
        if (originalVelocity == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        player.setAllowFlight(false);
        player.setVelocity(originalVelocity);
    }
}
