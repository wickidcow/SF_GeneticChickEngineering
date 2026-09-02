package net.guizhanss.gcereborn.core.compat;

import java.util.Locale;

import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/**
 * Small compatibility boundary for the Paper server family.
 *
 * <p>The addon intentionally avoids server-implementation classes. Keeping platform detection and
 * scheduling in one place makes future Paper/Purpur/Leaf/Folia API changes easier to isolate.</p>
 */
public final class PlatformSupport {

    public enum Platform {
        PAPER,
        PURPUR,
        LEAF,
        FOLIA,
        UNKNOWN
    }

    private final Plugin plugin;
    private final Platform platform;

    public PlatformSupport(@Nonnull Plugin plugin) {
        this.plugin = plugin;
        this.platform = detectPlatform();
    }

    @Nonnull
    public Platform getPlatform() {
        return platform;
    }

    public boolean isFolia() {
        return platform == Platform.FOLIA;
    }

    /**
     * Returns whether this server exposes the Paper API family used by supported platforms.
     */
    public boolean isPaperFamily() {
        try {
            Class.forName("io.papermc.paper.configuration.GlobalConfiguration", false, plugin.getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    /**
     * Supported Minecraft/Paper generation floor is 1.21.11. Paper's 26.x versioning naturally
     * compares newer because its major component is greater than 1.
     */
    public boolean isSupportedMinecraftVersion() {
        return compareVersions(Bukkit.getMinecraftVersion(), "1.21.11") >= 0;
    }

    /**
     * Executes work on the owning region under Folia and on the primary Bukkit scheduler for the
     * traditional Paper family. Call this for world/location access rather than scheduling a global
     * task and touching a region from it.
     */
    public void runAt(@Nonnull Location location, @Nonnull Runnable task) {
        if (isFolia()) {
            Bukkit.getRegionScheduler().execute(plugin, location, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Executes non-region-specific plugin work safely on the appropriate scheduler.
     */
    public void runGlobal(@Nonnull Runnable task) {
        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @Nonnull
    private static Platform detectPlatform() {
        String identity = (Bukkit.getName() + " " + Bukkit.getVersion() + " " + Bukkit.getServer().getName())
            .toLowerCase(Locale.ROOT);

        if (classExists("io.papermc.paper.threadedregions.RegionizedServer") || identity.contains("folia")) {
            return Platform.FOLIA;
        }
        if (identity.contains("purpur")) {
            return Platform.PURPUR;
        }
        if (identity.contains("leaf")) {
            return Platform.LEAF;
        }
        if (identity.contains("paper")) {
            return Platform.PAPER;
        }
        return Platform.UNKNOWN;
    }

    private static boolean classExists(@Nonnull String className) {
        try {
            Class.forName(className, false, PlatformSupport.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static int compareVersions(@Nonnull String left, @Nonnull String right) {
        int[] a = numericParts(left);
        int[] b = numericParts(right);
        int length = Math.max(a.length, b.length);

        for (int i = 0; i < length; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) {
                return Integer.compare(av, bv);
            }
        }
        return 0;
    }

    @Nonnull
    private static int[] numericParts(@Nonnull String version) {
        String[] raw = version.split("[^0-9]+");
        int count = 0;
        for (String part : raw) {
            if (!part.isEmpty()) {
                count++;
            }
        }

        int[] result = new int[count];
        int index = 0;
        for (String part : raw) {
            if (!part.isEmpty()) {
                try {
                    result[index++] = Integer.parseInt(part);
                } catch (NumberFormatException ignored) {
                    result[index - 1] = 0;
                }
            }
        }
        return result;
    }
}
