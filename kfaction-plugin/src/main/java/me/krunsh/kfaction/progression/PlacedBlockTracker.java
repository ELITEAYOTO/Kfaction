package me.krunsh.kfaction.progression;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

import me.krunsh.kfaction.Kfaction;

/**
 * Journal persistant et regroupé des blocs joueurs pertinents. L'état mémoire
 * est immédiat; les écritures disque sont batchées pour éviter une I/O par bloc.
 */
public final class PlacedBlockTracker {
    private static final long COMPACT_AFTER_BYTES = 16L * 1024L * 1024L;

    private final Kfaction plugin;
    private final Set<String> placed =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final ConcurrentLinkedQueue<String> journal =
            new ConcurrentLinkedQueue<String>();
    private final Object ioLock = new Object();
    private final File file;
    private BukkitTask flushTask;

    public PlacedBlockTracker(Kfaction plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/placed-blocks-v1.log");
    }

    public void initialize() {
        load();
        flushTask = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, this::flush, 100L, 100L);
    }

    public void shutdown() {
        if (flushTask != null) flushTask.cancel();
        flushTask = null;
        flush();
    }

    public void record(Block block) {
        String key = key(block);
        if (key != null && placed.add(key)) journal.add("+" + key);
    }

    /** Retire le marqueur seulement après un BlockBreak non annulé. */
    public boolean consume(Block block) {
        String key = key(block);
        if (key == null || !placed.remove(key)) return false;
        journal.add("-" + key);
        return true;
    }

    public int size() { return placed.size(); }

    private void load() {
        if (!file.isFile()) return;
        int invalid = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() < 2) {
                    invalid++;
                } else if (line.charAt(0) == '+') {
                    placed.add(line.substring(1));
                } else if (line.charAt(0) == '-') {
                    placed.remove(line.substring(1));
                } else invalid++;
            }
        } catch (Exception ex) {
            plugin.getLogger().severe("Lecture placed-blocks impossible: "
                    + ex.getMessage());
        }
        if (invalid > 0) {
            plugin.getLogger().warning(invalid
                    + " lignes invalides ignorées dans placed-blocks-v1.log.");
        }
        plugin.getLogger().info("Protection blocs placés: " + placed.size()
                + " positions restaurées.");
    }

    private void flush() {
        List<String> batch = new ArrayList<String>();
        String operation;
        while ((operation = journal.poll()) != null) batch.add(operation);
        if (batch.isEmpty()) return;
        synchronized (ioLock) {
            try {
                File parent = file.getParentFile();
                if (!parent.exists() && !parent.mkdirs()) {
                    throw new java.io.IOException("création du dossier impossible");
                }
                try (BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(file, true),
                                StandardCharsets.UTF_8))) {
                    for (String line : batch) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
                if (file.length() >= COMPACT_AFTER_BYTES) compact();
            } catch (Exception ex) {
                for (String line : batch) journal.add(line);
                plugin.getLogger().severe("Écriture placed-blocks impossible: "
                        + ex.getMessage());
            }
        }
    }

    private void compact() throws Exception {
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(temporary), StandardCharsets.UTF_8))) {
            for (String key : placed) {
                writer.write("+" + key);
                writer.newLine();
            }
        }
        try {
            Files.move(temporary.toPath(), file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String key(Block block) {
        if (block == null) return null;
        return key(block.getLocation());
    }

    static String key(Location location) {
        if (location == null || location.getWorld() == null) return null;
        String world = Base64.getUrlEncoder().withoutPadding().encodeToString(
                location.getWorld().getName().getBytes(StandardCharsets.UTF_8));
        return world + ";" + location.getBlockX() + ";" + location.getBlockY()
                + ";" + location.getBlockZ();
    }
}
