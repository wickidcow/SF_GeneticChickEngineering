package net.guizhanss.gcereborn.items.chicken;

import java.util.Optional;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.gson.JsonObject;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Chicken;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.DistinctiveItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.NotPlaceable;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.ItemUtils;

import net.guizhanss.gcereborn.GeneticChickengineering;
import net.guizhanss.gcereborn.core.adapters.AnimalsAdapter;
import net.guizhanss.gcereborn.core.genetics.DNA;
import net.guizhanss.gcereborn.utils.ChickenUtils;
import net.guizhanss.gcereborn.utils.Keys;

public class PocketChicken extends SimpleSlimefunItem<ItemUseHandler> implements NotPlaceable, DistinctiveItem {

    public static final AnimalsAdapter<Chicken> ADAPTER = new AnimalsAdapter<>(Chicken.class);

    public PocketChicken(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    @Nonnull
    public ItemUseHandler getItemHandler() {
        return e -> {
            e.cancel();

            Optional<Block> block = e.getClickedBlock();
            if (block.isEmpty()) {
                return;
            }

            ItemStack pocketChicken = e.getItem();
            ItemMeta meta = pocketChicken.getItemMeta();

            JsonObject json = PersistentDataAPI.get(meta, Keys.POCKET_CHICKEN_ADAPTER, ADAPTER);
            if (json == null) {
                // Old/guide-created Pocket Chickens may only contain DNA. Restore them as a safe adult chicken.
                json = ChickenUtils.getChickenJson(false);
            }

            int[] dnaState = PersistentDataAPI.getIntArray(meta, Keys.POCKET_CHICKEN_DNA);
            DNA dna = dnaState != null ? new DNA(dnaState) : new DNA();

            Block b = block.get();
            Location location = b.getRelative(e.getClickedFace()).getLocation().toCenterLocation();
            Chicken entity = null;

            try {
                // Treat release like a transaction: the item is only consumed after the entity is fully restored.
                entity = b.getWorld().spawn(location, Chicken.class);
                ADAPTER.apply(entity, json);

                PersistentDataAPI.setString(entity, Keys.CHICKEN_DNA, dna.getStateString());

                if (GeneticChickengineering.getConfigService().isDisplayResources() && dna.isKnown()) {
                    String name = ChatColor.WHITE + "(" + ChickenTypes.getDisplayName(dna.getTyping()) + ")";
                    if (!json.get("_customName").isJsonNull()) {
                        name = json.get("_customName").getAsString() + " " + name;
                    }
                    entity.setCustomName(name);
                    entity.setCustomNameVisible(true);
                }

                if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
                    ItemUtils.consumeItem(pocketChicken, false);
                }
            } catch (RuntimeException | LinkageError ex) {
                // Critical anti-duplication rollback: never leave a spawned chicken behind when restoration fails.
                if (entity != null && entity.isValid()) {
                    entity.remove();
                }

                GeneticChickengineering.getInstance().getLogger().log(
                    Level.SEVERE,
                    "Failed to release a Pocket Chicken. Spawn was rolled back and the item was not consumed.",
                    ex
                );
                e.getPlayer().sendMessage(
                    ChatColor.RED + "Could not release this Pocket Chicken safely. The item was not consumed."
                );
            }
        };
    }

    @Override
    @ParametersAreNonnullByDefault
    public boolean canStack(ItemMeta meta1, ItemMeta meta2) {
        return meta1.getPersistentDataContainer().equals(meta2.getPersistentDataContainer());
    }
}
