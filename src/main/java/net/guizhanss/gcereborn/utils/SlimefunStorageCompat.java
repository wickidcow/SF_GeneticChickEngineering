package net.guizhanss.gcereborn.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Location;
import org.bukkit.block.Block;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;

/**
 * Keeps Genetic ChickEngineering compatible with both the RC-37 API floor and
 * Slimefun Legacy's rewritten block-data controller.
 *
 * <p>On Slimefun Legacy this follows the same modern controller path used by
 * the deprecated BlockStorage facade, including synchronous data loading.
 * Upstream RC-37 does not expose that controller API, so only that legacy
 * runtime uses the isolated BlockStorage fallback.</p>
 */
public final class SlimefunStorageCompat {

    private static final MenuLookup MENU_LOOKUP = createMenuLookup();

    private SlimefunStorageCompat() {}

    @Nullable
    public static BlockMenu getBlockMenu(@Nonnull Block block) {
        return MENU_LOOKUP.get(block.getLocation());
    }

    @Nonnull
    private static MenuLookup createMenuLookup() {
        try {
            Method getDatabaseManager = Slimefun.class.getMethod("getDatabaseManager");
            Method getBlockDataController = getDatabaseManager.getReturnType().getMethod("getBlockDataController");
            Method getBlockData = getBlockDataController.getReturnType().getMethod("getBlockData", Location.class);
            Method getBlockMenu = getBlockData.getReturnType().getMethod("getBlockMenu");

            return location -> getModernBlockMenu(
                location,
                getDatabaseManager,
                getBlockDataController,
                getBlockData,
                getBlockMenu
            );
        } catch (NoSuchMethodException | LinkageError ignored) {
            // RC-37 predates the BlockDataController API.
            return LegacyBlockStorageAccess::getBlockMenu;
        }
    }

    @Nullable
    private static BlockMenu getModernBlockMenu(
        @Nonnull Location location,
        @Nonnull Method getDatabaseManager,
        @Nonnull Method getBlockDataController,
        @Nonnull Method getBlockData,
        @Nonnull Method getBlockMenu
    ) {
        try {
            Object databaseManager = getDatabaseManager.invoke(null);
            Object controller = getBlockDataController.invoke(databaseManager);
            Object blockData = getBlockData.invoke(controller, location);
            return blockData == null ? null : (BlockMenu) getBlockMenu.invoke(blockData);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not access Slimefun Legacy block-data API", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Slimefun Legacy block-data lookup failed", cause);
        }
    }

    @FunctionalInterface
    private interface MenuLookup {
        @Nullable
        BlockMenu get(@Nonnull Location location);
    }

    /**
     * Intentional compatibility boundary for upstream Slimefun RC-37.
     * Modern Slimefun Legacy never selects this path.
     */
    @SuppressWarnings("deprecation")
    private static final class LegacyBlockStorageAccess {

        private LegacyBlockStorageAccess() {}

        @Nullable
        private static BlockMenu getBlockMenu(@Nonnull Location location) {
            return me.mrCookieSlime.Slimefun.api.BlockStorage.getInventory(location);
        }
    }
}
