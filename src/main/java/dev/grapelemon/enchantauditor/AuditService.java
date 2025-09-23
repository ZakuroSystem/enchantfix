package dev.grapelemon.enchantauditor;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class AuditService {

    private final EnchantAuditor plugin;
    private final BackupManager backupManager;
    private final PluginLogger pluginLogger;

    private int cap255To;
    private int capMin;
    private int capMax;
    private int capRangeTo;
    private boolean includeEnder;

    public AuditService(EnchantAuditor plugin, BackupManager backupManager, PluginLogger pluginLogger) {
        this.plugin = plugin;
        this.backupManager = backupManager;
        this.pluginLogger = pluginLogger;
        loadConfig();
    }

    public void reloadConfig() {
        loadConfig();
    }

    private void loadConfig() {
        this.cap255To = plugin.getConfig().getInt("rules.cap255-to", 10);
        this.capRangeTo = plugin.getConfig().getInt("rules.cap20to99-to", 20);
        this.capMin = plugin.getConfig().getInt("rules.cap20to99-min", 20);
        this.capMax = plugin.getConfig().getInt("rules.cap20to99-max", 99);
        this.includeEnder = plugin.getConfig().getBoolean("include-ender-chest", false);
    }

    public void auditPlayer(Player player) {
        BackupManager.Snapshot snapshot = new BackupManager.Snapshot(player.getUniqueId(), player.getName());

        PlayerInventory inv = player.getInventory();

        // メインストレージ
        ItemStack[] storage = inv.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            storage[slot] = fixItem(player, storage[slot], "STORAGE", slot, snapshot);
        }
        inv.setStorageContents(storage);

        // 防具
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            armor[i] = fixItem(player, armor[i], "ARMOR", i, snapshot);
        }
        inv.setArmorContents(armor);

        // オフハンド
        inv.setItemInOffHand(fixItem(player, inv.getItemInOffHand(), "OFFHAND", 0, snapshot));

        // エンダーチェスト
        if (includeEnder) {
            Inventory ec = player.getEnderChest();
            ItemStack[] ecCont = ec.getContents();
            for (int i = 0; i < ecCont.length; i++) {
                ecCont[i] = fixItem(player, ecCont[i], "ENDER", i, snapshot);
            }
            ec.setContents(ecCont);
        }

        if (!snapshot.getEntries().isEmpty()) {
            backupManager.saveSnapshot(snapshot);
        }
    }

    private ItemStack fixItem(Player player, ItemStack item, String area, int index, BackupManager.Snapshot snapshot) {
        if (item == null || item.getType() == Material.AIR) return item;
        ItemMeta meta;
        try {
            meta = item.getItemMeta();
        } catch (IllegalArgumentException ex) {
            String errorMsg = String.format(
                    "Failed to read meta for %s [%s:%d]: %s",
                    item.getType().name(), area, index, ex.getMessage()
            );
            plugin.getLogger().warning(errorMsg);
            pluginLogger.writeLine(errorMsg);
            return item;
        }
        if (meta == null) return item;

        Map<Enchantment, Integer> enchants = new HashMap<>(meta.getEnchants());
        boolean changed = false;
        List<String> changeLogs = new ArrayList<>();

        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            Enchantment ench = e.getKey();
            int level = e.getValue();
            int newLevel = level;

            if (level == 255) {
                newLevel = cap255To;
            } else if (level >= capMin && level <= capMax) {
                newLevel = capRangeTo;
            }

            if (newLevel != level) {
                // バックアップ（このスロット未記録なら保存）
                snapshot.offer(area, index, item);

                meta.removeEnchant(ench);
                meta.addEnchant(ench, newLevel, false);
                changed = true;

                changeLogs.add(ench.getKey().getKey() + " " + level + "->" + newLevel);
            }
        }

        if (changed) {
            item.setItemMeta(meta);
            String msg = String.format("%s: %s [%s:%d] %s",
                    player.getName(), item.getType().name(), area, index, String.join(", ", changeLogs));
            plugin.getLogger().info(msg);
            pluginLogger.writeLine(msg);
        }
        return item;
    }
}
