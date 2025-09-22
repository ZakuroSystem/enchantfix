package dev.grapelemon.enchantauditor;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PluginLogger {

    private final EnchantAuditor plugin;
    private SimpleDateFormat dateFmt;
    private SimpleDateFormat timeFmt;

    public PluginLogger(EnchantAuditor plugin) {
        this.plugin = plugin;
        reloadPatterns();
    }

    public void reloadPatterns() {
        String dp = plugin.getConfig().getString("log.date-pattern", "yyyy-MM-dd");
        String tp = plugin.getConfig().getString("log.time-pattern", "HH:mm:ss");
        dateFmt = new SimpleDateFormat(dp);
        timeFmt = new SimpleDateFormat(tp);
    }

    public synchronized void writeLine(String message) {
        try {
            File folder = new File(plugin.getDataFolder(), "logs");
            if (!folder.exists()) folder.mkdirs();
            File log = new File(folder, dateFmt.format(new Date()) + ".log");

            try (FileWriter fw = new FileWriter(log, true);
                 BufferedWriter bw = new BufferedWriter(fw)) {
                bw.write("[" + timeFmt.format(new Date()) + "] " + message);
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
