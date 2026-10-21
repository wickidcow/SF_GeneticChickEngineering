package net.guizhanss.gcereborn.core.compat;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.plugin.Plugin;

/**
 * Owns only GuizhanLib's addon-global integer tick counter, never a machine,
 * entity or block callback. The general GuizhanLib Scheduler is unchanged.
 */
public final class AddonTickCounter {

    private AddonTickCounter() {
    }

    /**
     * Starts global bookkeeping with the original one-tick initial delay and
     * clamped Slimefun tick interval. Returns an idempotent shutdown action.
     */
    public static Runnable start(Plugin plugin, int intervalTicks, Runnable counter) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(counter, "counter");
        AtomicBoolean active = new AtomicBoolean(true);
        Runnable guardedCounter = () -> {
            if (active.get()) {
                counter.run();
            }
        };
        long interval = Math.max(1, intervalTicks);
        Runnable cancel;

        if (new PlatformSupport(plugin).isFolia()) {
            var task = Objects.requireNonNull(plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin, ignored -> guardedCounter.run(), 1L, interval
            ), "global bookkeeping task");
            cancel = task::cancel;
        } else {
            var task = Objects.requireNonNull(plugin.getServer().getScheduler().runTaskTimer(
                plugin, guardedCounter, 1L, interval
            ), "bookkeeping task");
            cancel = task::cancel;
        }

        return () -> {
            if (active.getAndSet(false)) {
                cancel.run();
            }
        };
    }
}
