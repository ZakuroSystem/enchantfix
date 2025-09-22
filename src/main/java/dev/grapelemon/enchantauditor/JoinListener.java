package dev.grapelemon.enchantauditor;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {
    private final AuditService auditService;

    public JoinListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        auditService.auditPlayer(e.getPlayer());
    }
}
