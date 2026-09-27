package net.guizhanss.gcereborn.utils;

import javax.annotation.Nonnull;

/**
 * Compatibility handlers for the RC-37 chest-menu ABI.
 *
 * <p>RC-37's MenuClickHandler signature contains the deprecated ClickAction
 * type even when an addon does not use that argument. Keep that unavoidable
 * compatibility detail here instead of spreading deprecated menu types through
 * machine implementations.</p>
 */
@SuppressWarnings("deprecation")
public final class SlimefunMenuCompat {

    private SlimefunMenuCompat() {}

    /**
     * Preserves Genetic ChickEngineering's historical output-slot behavior:
     * clicking an occupied output slot is allowed, while an empty output slot
     * remains protected.
     */
    @Nonnull
    public static me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu.MenuClickHandler getLegacyOutputSlotHandler() {
        return (player, slot, item, action) -> item != null && !item.getType().isAir();
    }
}
