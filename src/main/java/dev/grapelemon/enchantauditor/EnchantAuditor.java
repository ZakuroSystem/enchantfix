package dev.grapelemon.enchantauditor;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class EnchantAuditor extends JavaPlugin {

    private AuditService auditService;
    private BackupManager backupManager;
    private PluginLogger pluginLogger;
    private int repeatingTaskId = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.pluginLogger = new PluginLogger(this);
        this.backupManager = new BackupManager(this);
        this.auditService = new AuditService(this, backupManager, pluginLogger);

        // リスナー登録（ログイン時監査）
        Bukkit.getPluginManager().registerEvents(new JoinListener(auditService), this);

        // コマンド登録
        EnchFixCommand cmd = new EnchFixCommand(this, auditService, backupManager, pluginLogger);
        if (getCommand("enchfix") != null) {
            getCommand("enchfix").setExecutor(cmd);
            getCommand("enchfix").setTabCompleter(cmd);
        }

        // 定期監査
        scheduleRepeatingAudit();
        getLogger().info("EnchantAuditor enabled.");
    }

    @Override
    public void onDisable() {
        if (repeatingTaskId != -1) {
            Bukkit.getScheduler().cancelTask(repeatingTaskId);
        }
        getLogger().info("EnchantAuditor disabled.");
    }

    public void scheduleRepeatingAudit() {
        int seconds = Math.max(5, getConfig().getInt("scan-interval-seconds", 30));
        long ticks = seconds * 20L;

        if (repeatingTaskId != -1) {
            Bukkit.getScheduler().cancelTask(repeatingTaskId);
        }
        repeatingTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            Bukkit.getOnlinePlayers().forEach(auditService::auditPlayer);
        }, ticks, ticks);
    }

    public void reloadPlugin() {
        reloadConfig();
        auditService.reloadConfig();
        pluginLogger.reloadPatterns();
        scheduleRepeatingAudit();
        getLogger().info("EnchantAuditor config reloaded.");
    }
}
