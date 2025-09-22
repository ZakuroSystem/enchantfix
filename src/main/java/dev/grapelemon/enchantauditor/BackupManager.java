package dev.grapelemon.enchantauditor;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class BackupManager {

    private final EnchantAuditor plugin;
    private final SimpleDateFormat stamp = new SimpleDateFormat("yyyyMMdd-HHmmss");

    public BackupManager(EnchantAuditor plugin) {
        this.plugin = plugin;
    }

    public static class Snapshot {
        public final UUID uuid;
        public final String name;
        public final String createdAt;
        private final List<Map<String, Object>> entries = new ArrayList<>();

        public Snapshot(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
            this.createdAt = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        }

        public void offer(String area, int index, ItemStack before) {
            boolean exists = entries.stream().anyMatch(m ->
                    Objects.equals(m.get("area"), area) && Objects.equals(m.get("index"), index));
            if (exists) return;

            Map<String, Object> map = new HashMap<>();
            map.put("area", area);
            map.put("index", index);
            map.put("item", before.serialize());
            entries.add(map);
        }

        public List<Map<String, Object>> getEntries() {
            return entries;
        }
    }

    private File playerFolder(UUID uuid) {
        File f = new File(plugin.getDataFolder(), "backups/" + uuid.toString());
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public void saveSnapshot(Snapshot s) {
        try {
            File file = new File(playerFolder(s.uuid), s.createdAt + ".yml");
            YamlConfiguration yml = new YamlConfiguration();
            yml.set("player", s.name);
            yml.set("uuid", s.uuid.toString());
            yml.set("createdAt", s.createdAt);
            yml.set("entries", s.entries);
            yml.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<String> listBackups(UUID uuid) {
        File dir = playerFolder(uuid);
        String[] files = dir.list((d, name) -> name.endsWith(".yml"));
        if (files == null) return Collections.emptyList();
        List<String> stamps = new ArrayList<>();
        for (String f : files) {
            stamps.add(f.replace(".yml", ""));
        }
        stamps.sort(Comparator.naturalOrder());
        return stamps;
    }

    @SuppressWarnings("unchecked")
    public boolean restore(Player target, String stampOrLatest) {
        List<String> stamps = listBackups(target.getUniqueId());
        if (stamps.isEmpty()) return false;

        String stamp = stampOrLatest.equalsIgnoreCase("latest") ? stamps.get(stamps.size()-1) : stampOrLatest;
        File file = new File(playerFolder(target.getUniqueId()), stamp + ".yml");
        if (!file.exists()) return false;

        try {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            List<Map<?, ?>> entries = (List<Map<?, ?>>) yml.getList("entries", Collections.emptyList());

            for (Map<?, ?> raw : entries) {
                String area = String.valueOf(raw.get("area"));
                int index = (int) raw.get("index");
                Map<String, Object> ser = (Map<String, Object>) raw.get("item");
                ItemStack item = ItemStack.deserialize(ser);

                switch (area) {
                    case "STORAGE" -> {
                        var inv = target.getInventory();
                        ItemStack[] storage = inv.getStorageContents();
                        if (index >= 0 && index < storage.length) {
                            storage[index] = item;
                            inv.setStorageContents(storage);
                        }
                    }
                    case "ARMOR" -> {
                        var inv = target.getInventory();
                        ItemStack[] armor = inv.getArmorContents();
                        if (index >= 0 && index < armor.length) {
                            armor[index] = item;
                            inv.setArmorContents(armor);
                        }
                    }
                    case "OFFHAND" -> target.getInventory().setItemInOffHand(item);
                    case "ENDER" -> {
                        var ec = target.getEnderChest();
                        ItemStack[] cont = ec.getContents();
                        if (index >= 0 && index < cont.length) {
                            cont[index] = item;
                            ec.setContents(cont);
                        }
                    }
                }
            }
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }
}
