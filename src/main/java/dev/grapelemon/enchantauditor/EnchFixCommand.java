package dev.grapelemon.enchantauditor;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EnchFixCommand implements CommandExecutor, TabCompleter {

    private final EnchantAuditor plugin;
    private final AuditService auditService;
    private final BackupManager backupManager;
    private final PluginLogger pluginLogger;

    public EnchFixCommand(EnchantAuditor plugin, AuditService auditService, BackupManager backupManager, PluginLogger pluginLogger) {
        this.plugin = plugin;
        this.auditService = auditService;
        this.backupManager = backupManager;
        this.pluginLogger = pluginLogger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("enchfix.admin")) {
            sender.sendMessage("You don't have permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Usage: /enchfix <rescan|list|restore|reload> ...");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage("Reloaded.");
            }
            case "rescan" -> {
                if (args.length == 1 || args[1].equalsIgnoreCase("all")) {
                    Bukkit.getOnlinePlayers().forEach(auditService::auditPlayer);
                    sender.sendMessage("Rescanned all online players.");
                } else {
                    Player p = Bukkit.getPlayer(args[1]);
                    if (p == null) {
                        sender.sendMessage("Player not online: " + args[1]);
                        return true;
                    }
                    auditService.auditPlayer(p);
                    sender.sendMessage("Rescanned: " + p.getName());
                }
            }
            case "list" -> {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /enchfix list <player>");
                    return true;
                }
                UUID uuid = getUUID(args[1]);
                if (uuid == null) {
                    sender.sendMessage("Player not found: " + args[1]);
                    return true;
                }
                var list = backupManager.listBackups(uuid);
                if (list.isEmpty()) {
                    sender.sendMessage("No backups.");
                } else {
                    sender.sendMessage("Backups for " + args[1] + ": " + String.join(", ", list));
                }
            }
            case "restore" -> {
                if (args.length < 3) {
                    sender.sendMessage("Usage: /enchfix restore <player> <timestamp|latest>");
                    return true;
                }
                Player p = Bukkit.getPlayer(args[1]);
                if (p == null) {
                    sender.sendMessage("Player must be online for restore.");
                    return true;
                }
                boolean ok = backupManager.restore(p, args[2]);
                sender.sendMessage(ok ? "Restored." : "Restore failed.");
            }
            default -> sender.sendMessage("Usage: /enchfix <rescan|list|restore|reload> ...");
        }
        return true;
    }

    private UUID getUUID(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p.getUniqueId();
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        return op != null ? op.getUniqueId() : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("enchfix.admin")) return out;
        if (args.length == 1) {
            out.add("rescan");
            out.add("list");
            out.add("restore");
            out.add("reload");
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "rescan", "list", "restore" -> {
                    Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                    out.add("all");
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("restore")) {
            out.add("latest");
        }
        return out;
    }
}
