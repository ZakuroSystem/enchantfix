package dev.grapelemon.enchantauditor;

import com.google.common.collect.Multimap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
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

    private static final double MULTIPLIER_LIMIT = 1.0D;
    private static final double MULTIPLIER_EPSILON = 1.0E-6D;
    private static final double ATTACK_DAMAGE_LIMIT = 10.0D;

    private ItemStack fixItem(Player player, ItemStack item, String area, int index, BackupManager.Snapshot snapshot) {
        if (item == null || item.getType() == Material.AIR) return item;
        ItemMeta meta = safeItemMeta(item, area, index);
        if (meta == null) return item;

        Map<Enchantment, Integer> enchants = new HashMap<>(meta.getEnchants());
        boolean changed = false;
        boolean snapshotSaved = false;
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
                if (!snapshotSaved) {
                    snapshot.offer(area, index, item);
                    snapshotSaved = true;
                }

                meta.removeEnchant(ench);
                meta.addEnchant(ench, newLevel, false);
                changed = true;

                changeLogs.add(ench.getKey().getKey() + " " + level + "->" + newLevel);
            }
        }

        List<String> attributeLogs = clampAttributeMultipliers(meta);
        if (!attributeLogs.isEmpty()) {
            if (!snapshotSaved) {
                snapshot.offer(area, index, item);
                snapshotSaved = true;
            }
            changeLogs.addAll(attributeLogs);
            changed = true;
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

    private ItemMeta safeItemMeta(ItemStack item, String area, int index) {
        try {
            return item.getItemMeta();
        } catch (IllegalArgumentException ex) {
            boolean stripped = stripInvalidAttributes(item, area, index, ex.getMessage());
            if (stripped) {
                try {
                    return item.getItemMeta();
                } catch (IllegalArgumentException retryEx) {
                    logMetaError(item, area, index, retryEx);
                    return null;
                }
            }
            logMetaError(item, area, index, ex);
            return null;
        }
    }

    private boolean stripInvalidAttributes(ItemStack item, String area, int index, String cause) {
        try {
            Bukkit.getUnsafe().modifyItemStack(item, "{AttributeModifiers:[]}");
            String infoMsg = String.format(
                    "Stripped invalid attribute modifiers from %s [%s:%d]: %s",
                    item.getType().name(), area, index, cause
            );
            plugin.getLogger().warning(infoMsg);
            pluginLogger.writeLine(infoMsg);
            return true;
        } catch (Throwable t) {
            String warnMsg = String.format(
                    "Failed to strip attribute modifiers from %s [%s:%d]: %s",
                    item.getType().name(), area, index, t.getMessage()
            );
            plugin.getLogger().warning(warnMsg);
            pluginLogger.writeLine(warnMsg);
            return false;
        }
    }

    private List<String> clampAttributeMultipliers(ItemMeta meta) {
        if (!meta.hasAttributeModifiers()) {
            return Collections.emptyList();
        }

        Multimap<Attribute, AttributeModifier> modifiers = meta.getAttributeModifiers();
        if (modifiers == null || modifiers.isEmpty()) {
            return Collections.emptyList();
        }

        record AttributeAdjustment(Attribute attribute, AttributeModifier original, double originalAmount) {}

        List<AttributeAdjustment> adjustments = new ArrayList<>();
        for (Map.Entry<Attribute, AttributeModifier> entry : modifiers.entries()) {
            AttributeModifier modifier = entry.getValue();
            AttributeModifier.Operation operation = modifier.getOperation();
            Attribute attribute = entry.getKey();

            if (isAdditiveOperation(operation)) {
                if (!isAttackDamageAttribute(attribute)) {
                    continue;
                }

                double amount = modifier.getAmount();
                if (!Double.isFinite(amount) || amount > ATTACK_DAMAGE_LIMIT + MULTIPLIER_EPSILON) {
                    adjustments.add(new AttributeAdjustment(attribute, modifier, amount));
                }
                continue;
            }

            if (!isMultiplierOperation(operation)) {
                continue;
            }

            double amount = modifier.getAmount();
            if (amount > MULTIPLIER_LIMIT + MULTIPLIER_EPSILON) {
                adjustments.add(new AttributeAdjustment(attribute, modifier, amount));
            }
        }

        if (adjustments.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> logs = new ArrayList<>();
        for (AttributeAdjustment adjustment : adjustments) {
            Attribute attribute = adjustment.attribute();
            AttributeModifier original = adjustment.original();
            meta.removeAttributeModifier(attribute, original);

            AttributeModifier replacement;
            String log;

            if (original.getOperation() == AttributeModifier.Operation.ADD_NUMBER && isAttackDamageAttribute(attribute)) {
                replacement = rebuildModifier(original, ATTACK_DAMAGE_LIMIT);
                log = formatAttackDamageLog(attribute, original, adjustment.originalAmount());
            } else {
                replacement = rebuildModifier(original, MULTIPLIER_LIMIT);
                long originalPercent = Math.round(adjustment.originalAmount() * 100.0D);
                log = String.format(
                        Locale.ROOT,
                        "attr %s %s %+d%%->+100%%",
                        attributeKey(attribute),
                        original.getName(),
                        originalPercent
                );
            }

            meta.addAttributeModifier(attribute, replacement);
            logs.add(log);
        }

        return logs;
    }

    private String formatAttackDamageLog(Attribute attribute, AttributeModifier original, double originalAmount) {
        String originalText;
        if (Double.isNaN(originalAmount)) {
            originalText = "NaN";
        } else if (Double.isInfinite(originalAmount)) {
            originalText = originalAmount > 0 ? "+∞" : "-∞";
        } else {
            originalText = String.format(Locale.ROOT, "%+,.1f", originalAmount);
        }

        return String.format(
                Locale.ROOT,
                "attr %s %s %s->+10.0",
                attributeKey(attribute),
                original.getName(),
                originalText
        );
    }

    private boolean isAttackDamageAttribute(Attribute attribute) {
        if (attribute == null) {
            return false;
        }

        try {
            NamespacedKey key = attribute.getKey();
            if (key != null && "generic.attack_damage".equals(key.getKey())) {
                return true;
            }
        } catch (NoSuchMethodError ignored) {
            // ignore
        }

        try {
            String name = attribute.name();
            if ("GENERIC_ATTACK_DAMAGE".equals(name) || "ATTACK_DAMAGE".equals(name)) {
                return true;
            }
        } catch (NoSuchMethodError ignored) {
            // ignore
        }

        return false;
    }

    private String attributeKey(Attribute attribute) {
        if (attribute == null) {
            return "unknown";
        }

        try {
            NamespacedKey key = attribute.getKey();
            if (key != null) {
                return key.toString();
            }
        } catch (NoSuchMethodError ignored) {
            // ignore
        }

        try {
            return attribute.name();
        } catch (NoSuchMethodError ignored) {
            return attribute.toString();
        }
    }

    private AttributeModifier rebuildModifier(AttributeModifier original, double amount) {
        AttributeModifier rebuilt = rebuildWithBuilder(original, amount);
        if (rebuilt != null) {
            return rebuilt;
        }

        EquipmentSlot slot = tryGetEquipmentSlot(original);
        if (slot != null) {
            return new AttributeModifier(original.getUniqueId(), original.getName(), amount, original.getOperation(), slot);
        }
        return new AttributeModifier(original.getUniqueId(), original.getName(), amount, original.getOperation());
    }

    private AttributeModifier rebuildWithBuilder(AttributeModifier original, double amount) {
        try {
            Method builderMethod = AttributeModifier.class.getMethod("builder");
            Object builder = builderMethod.invoke(null);

            invokeBuilder(builder, "id", UUID.class, original.getUniqueId());
            invokeBuilder(builder, "name", String.class, original.getName());
            invokeBuilder(builder, "amount", double.class, amount);
            invokeBuilder(builder, "operation", AttributeModifier.Operation.class, original.getOperation());

            try {
                Method getSlotGroup = AttributeModifier.class.getMethod("getSlotGroup");
                Object slotGroup = getSlotGroup.invoke(original);
                if (slotGroup != null) {
                    invokeBuilder(builder, "slotGroup", slotGroup.getClass(), slotGroup);
                }
            } catch (NoSuchMethodException ignored) {
                EquipmentSlot slot = tryGetEquipmentSlot(original);
                if (slot != null) {
                    invokeBuilder(builder, "slot", EquipmentSlot.class, slot);
                }
            }

            Method buildMethod = builder.getClass().getMethod("build");
            return (AttributeModifier) buildMethod.invoke(builder);
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private EquipmentSlot tryGetEquipmentSlot(AttributeModifier original) {
        try {
            Method getSlot = AttributeModifier.class.getMethod("getSlot");
            Object slot = getSlot.invoke(original);
            if (slot instanceof EquipmentSlot equipmentSlot) {
                return equipmentSlot;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    private void invokeBuilder(Object builder, String methodName, Class<?> parameterType, Object argument) throws ReflectiveOperationException {
        Method method = builder.getClass().getMethod(methodName, parameterType);
        method.invoke(builder, argument);
    }

    private static boolean isAdditiveOperation(AttributeModifier.Operation operation) {
        String name = operation.name();
        return name.equals("ADD_NUMBER") || name.equals("ADD_VALUE");
    }

    private static boolean isMultiplierOperation(AttributeModifier.Operation operation) {
        String name = operation.name();
        if (name.equals("ADD_SCALAR") || name.equals("MULTIPLY_SCALAR_1") || name.equals("ADD_MULTIPLIER")) {
            return true;
        }
        return name.contains("MULTIPLY");
    }

    private void logMetaError(ItemStack item, String area, int index, IllegalArgumentException ex) {
        String errorMsg = String.format(
                "Failed to read meta for %s [%s:%d]: %s",
                item.getType().name(), area, index, ex.getMessage()
        );
        plugin.getLogger().warning(errorMsg);
        pluginLogger.writeLine(errorMsg);
    }
}
