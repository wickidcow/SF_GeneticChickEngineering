package net.guizhanss.gcereborn.utils;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Preconditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.bukkit.Material;
import org.bukkit.entity.Chicken;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;

import net.guizhanss.gcereborn.GeneticChickengineering;
import net.guizhanss.gcereborn.core.genetics.DNA;
import net.guizhanss.gcereborn.items.GCEItems;
import net.guizhanss.gcereborn.items.chicken.ChickenTypes;
import net.guizhanss.gcereborn.items.chicken.PocketChicken;
import net.guizhanss.gcereborn.setup.Groups;
import net.guizhanss.gcereborn.setup.RecipeTypes;
import net.guizhanss.guizhanlib.minecraft.utils.MinecraftVersionUtil;

import lombok.experimental.UtilityClass;

/**
 * Utility class for {@link PocketChicken}.
 */
@UtilityClass
public final class ChickenUtils {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    /**
     * Determine whether an {@link ItemStack} is a {@link PocketChicken}.
     *
     * @param item The {@link ItemStack} to check.
     * @return Whether the {@link ItemStack} is a {@link PocketChicken}.
     */
    public boolean isPocketChicken(@Nullable ItemStack item) {
        return item != null && !item.getType().isAir() && item.hasItemMeta() && PersistentDataAPI.hasIntArray(item.getItemMeta(), Keys.POCKET_CHICKEN_DNA);
    }

    /**
     * Get a json object representing a chicken.
     *
     * @return A json object representing a baby chicken.
     */
    @Nonnull
    public static JsonObject getChickenJson(boolean isBaby) {
        JsonObject json = new JsonObject();
        json.addProperty("_type", "CHICKEN");
        json.addProperty("_health", 4.0);
        json.addProperty("_absorption", 0.0);
        json.addProperty("_removeWhenFarAway", false);
        json.addProperty("_customName", (String) null);
        json.addProperty("_customNameVisible", false);
        json.addProperty("_ai", true);
        json.addProperty("_silent", false);
        json.addProperty("_glowing", false);
        json.addProperty("_invulnerable", false);
        json.addProperty("_collidable", true);
        json.addProperty("_gravity", true);
        json.addProperty("_fireTicks", 0);
        json.addProperty("baby", isBaby);
        json.addProperty("_age", isBaby ? -24000 : 6000);
        json.addProperty("_ageLock", false);
        json.addProperty("_breedable", false);
        json.addProperty("_loveModeTicks", 0);
        json.add("_attributes", new JsonObject());
        json.add("_effects", new JsonObject());
        json.add("_scoreboardTags", new JsonArray());
        return json;
    }

    /**
     * Captures a {@link Chicken} and returns a pocket chicken item.
     *
     * @param chicken The {@link Chicken} to capture.
     * @return The pocket chicken item.
     */
    @Nonnull
    public static ItemStack capture(@Nonnull Chicken chicken) {
        GeneticChickengineering.getIntegrationService().captureChicken(chicken);
        JsonObject json = PocketChicken.ADAPTER.saveData(chicken);
        ItemStack item = GCEItems.POCKET_CHICKEN.clone();

        DNA dna;
        String uuid = chicken.getUniqueId().toString();

        if (PersistentDataAPI.hasString(chicken, Keys.CHICKEN_DNA)) {
            String dnaStr = PersistentDataAPI.getString(chicken, Keys.CHICKEN_DNA);
            GeneticChickengineering.debug("captured chicken has data in pdc: {0}", dnaStr);
            dna = new DNA(dnaStr);
        } else {
            GeneticChickengineering.debug("captured chicken has no DNA information");
            dna = new DNA();
        }

        if (GeneticChickengineering.getConfigService().isDisplayResources() && json.get("_customNameVisible").getAsBoolean() && dna.isKnown()) {
            String name;
            if (!json.get("_customName").isJsonNull()) {
                name = json.get("_customName").getAsString();
            } else {
                name = "";
            }
            String replace = "(" + ChickenTypes.getDisplayName(dna.getTyping()) + ")";
            name = name.replace(replace, "");
            if (name.isEmpty()) {
                json.addProperty("_customName", (String) null);
                json.addProperty("_customNameVisible", false);
            } else {
                json.addProperty("_customName", name);
            }
        }

        setPocketChicken(item, json, dna);
        return item;
    }

    /**
     * Try to breed two chicken.
     *
     * @param chick1 The first chicken.
     * @param chick2 The second chicken.
     * @return The resulting baby chicken, or null if breeding failed.
     */
    @Nullable
    @ParametersAreNonnullByDefault
    public static ItemStack breed(ItemStack chick1, ItemStack chick2) {
        Preconditions.checkArgument(chick1 != null, "chick1 cannot be null");
        Preconditions.checkArgument(chick2 != null, "chick2 cannot be null");

        ItemMeta c1m = chick1.getItemMeta();
        ItemMeta c2m = chick2.getItemMeta();
        if (PersistentDataAPI.hasIntArray(c1m, Keys.POCKET_CHICKEN_DNA) && PersistentDataAPI.hasIntArray(c2m, Keys.POCKET_CHICKEN_DNA)) {
            DNA c1d = new DNA(PersistentDataAPI.getIntArray(c1m, Keys.POCKET_CHICKEN_DNA));
            DNA c2d = new DNA(PersistentDataAPI.getIntArray(c2m, Keys.POCKET_CHICKEN_DNA));
            return fromDNA(new DNA(c1d.split(), c2d.split()), true);
        }
        return null;
    }

    /**
     * Creates a display item for the given product in the dictionary.
     *
     * @param typing The type of chicken.
     */
    public static void createProductDisplay(int typing) {
        ItemStack fake = GCEItems.POCKET_CHICKEN.clone();
        DNA dna = new DNA(typing);
        String productRawName = ChickenTypes.getName(typing);
        setPocketChicken(fake, null, dna);

        // Use the chicken's resource as the icon
        String itemIDType = productRawName.replace(" ", "_").toUpperCase();
        SlimefunItemStack displayItem = new SlimefunItemStack("GCE_" + itemIDType + "_CHICKEN_ICON", ChickenTypes.getProduct(typing));
        // Since these will be "Pocket Chickens", they will spawn chickens when cheated into a player's inventory
        // We set the DNA on the icon so that it will spawn a chicken of the correct type
        ItemMeta meta = displayItem.getItemMeta();
        PersistentDataAPI.setIntArray(meta, Keys.POCKET_CHICKEN_DNA, dna.getState());
        displayItem.setItemMeta(meta);

        // Register the display
        // @formatter:off
        new PocketChicken(
            Groups.DICTIONARY,
            displayItem,
            RecipeTypes.FROM_CHICKEN,
            new ItemStack[] {
                null, null, null,
                null, fake, null,
                null, null, null
            }
        ).register(GeneticChickengineering.getInstance());
        // @formatter:on
    }

    /**
     * Create a fresh new chicken based on the DNA.
     *
     * @param dna    The DNA to use.
     * @param isBaby Whether the chicken is a baby.
     * @return The new chicken item.
     */
    @Nonnull
    public static ItemStack fromDNA(@Nonnull DNA dna, boolean isBaby) {
        JsonObject json = getChickenJson(isBaby);

        ItemStack item = GCEItems.POCKET_CHICKEN.clone();
        setPocketChicken(item, json, dna);
        return item;
    }

    @Nonnull
    public static DNA getDNA(@Nonnull ItemStack chicken) {
        ItemMeta meta = chicken.getItemMeta();
        return new DNA(PersistentDataAPI.getIntArray(meta, Keys.POCKET_CHICKEN_DNA));
    }

    /**
     * Returns a number which reflects the number of homozygous dominant alleles in a chicken.
     * This is used to give a boosted rate to resource production from chickens which are "pure".
     *
     * @param chicken The chicken {@link ItemStack}.
     * @return The DNA strength.
     */
    public static int getDNAStrength(@Nonnull ItemStack chicken) {
        DNA dna = getDNA(chicken);
        int[] state = dna.getState();
        int str = 6 - dna.getTier();
        for (int i = 0; i < 6; i++) {
            if (state[i] == 1) {
                str--;
            }
        }
        return str;
    }

    @Nonnull
    private static List<String> getLore(@Nullable JsonObject json, @Nonnull DNA dna) {
        List<String> lore = new LinkedList<>();
        var localization = GeneticChickengineering.getLocalization();
        if (json != null) {
            lore = PocketChicken.ADAPTER.getLore(json);
            if (GeneticChickengineering.getConfigService().isPainEnabled()) {
                double health = json.get("_health").getAsDouble();
                String status;
                if (health > 2.0) {
                    status = localization.getString("lores.chicken.status.healthy");
                } else if (health <= 0.50) {
                    status = localization.getString("lores.chicken.status.exhausted");
                } else {
                    status = localization.getString("lores.chicken.status.fatigued");
                }
                lore.add(localization.getString("lores.chicken.status.line", status));
            }
        }
        if (dna.isKnown()) {
            lore.add(localization.getString("lores.chicken.dna", dna));
            lore.add(localization.getString("lores.chicken.type", ChickenTypes.getDisplayName(dna.getTyping())));
        }
        return lore;
    }

    public static void setPocketChicken(@Nonnull ItemStack item, @Nullable JsonObject json, @Nonnull DNA dna) {
        ItemMeta meta = item.getItemMeta();
        PersistentDataAPI.setIntArray(meta, Keys.POCKET_CHICKEN_DNA, dna.getState());
        if (json != null) {
            PersistentDataAPI.set(meta, Keys.POCKET_CHICKEN_ADAPTER, PocketChicken.ADAPTER, json);
        }
        meta.lore(getLore(json, dna).stream().map(LEGACY_SERIALIZER::deserialize).toList());

        item.setItemMeta(meta);
    }

    public double getHealth(@Nullable ItemStack chicken) {
        if (chicken == null || chicken.getType().isAir()) {
            return 0d;
        }
        ItemMeta meta = chicken.getItemMeta();
        JsonObject json = PersistentDataAPI.get(meta, Keys.POCKET_CHICKEN_ADAPTER, PocketChicken.ADAPTER);
        if (json != null) {
            return json.get("_health").getAsDouble();
        }
        return 0d;
    }

    public boolean survivesPain(@Nullable ItemStack chicken) {
        return getHealth(chicken) > 0.25;
    }

    public boolean harm(@Nullable ItemStack chicken) {
        return harm(chicken, 0.25);
    }

    public boolean harm(@Nullable ItemStack chicken, double damage) {
        if (chicken == null || chicken.getType().isAir()) {
            return false;
        }
        ItemMeta meta = chicken.getItemMeta();
        JsonObject json = PersistentDataAPI.get(meta, Keys.POCKET_CHICKEN_ADAPTER, PocketChicken.ADAPTER);
        if (json != null) {
            double oldHealth = json.get("_health").getAsDouble();
            double newHealth = Math.max(0d, Math.min(oldHealth - damage, 4d));
            json.addProperty("_health", newHealth);
            setPocketChicken(chicken, json, getDNA(chicken));
            return true;
        }
        return false;
    }

    public boolean heal(@Nullable ItemStack chicken, double amount) {
        if (amount > 0) {
            amount *= -1;
        }
        return harm(chicken, amount);
    }

    public void possiblyHarm(@Nullable ItemStack chicken) {
        if (ThreadLocalRandom.current().nextInt(100) < GeneticChickengineering.getConfigService().getPainChance()) {
            harm(chicken);
        }
    }

    @Nonnull
    public ItemStack getResource(@Nonnull ItemStack chicken) {
        DNA dna = getDNA(chicken);
        return ChickenTypes.getProduct(dna.getTyping());
    }

    /**
     * Returns the number of homozygous recessive genes in the chicken
     * which represents the difficulty of obtaining this chicken
     *
     * @param chicken The chicken {@link ItemStack}.
     * @return The DNA tier.
     */
    public int getResourceTier(@Nonnull ItemStack chicken) {
        DNA dna = getDNA(chicken);
        return dna.getTier();
    }

    public boolean isFood(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir() || item.hasItemMeta()) {
            return false;
        }
        Material type = item.getType();
        if (type == Material.WHEAT_SEEDS || type == Material.BEETROOT_SEEDS || type == Material.MELON_SEEDS || type == Material.PUMPKIN_SEEDS) {
            return true;
        }

        if (MinecraftVersionUtil.isAtLeast(19, 4) && type == Material.TORCHFLOWER_SEEDS) {
            return true;
        }

        if (MinecraftVersionUtil.isAtLeast(20) && type == Material.PITCHER_POD) {
            return true;
        }

        return false;
    }

    /**
     * Determines whether the chicken is an adult.
     *
     * @param chicken The chicken {@link ItemStack}.
     * @return Whether the chicken is an adult.
     */
    public boolean isAdult(@Nonnull ItemStack chicken) {
        JsonObject json = PersistentDataAPI.get(chicken.getItemMeta(), Keys.POCKET_CHICKEN_ADAPTER, PocketChicken.ADAPTER);
        if (json != null) {
            return !json.get("baby").getAsBoolean();
        }
        return false;
    }

    /**
     * Determines whether the DNA of chicken is known.
     *
     * @param chicken The chicken {@link ItemStack}.
     * @return Whether the DNA is known.
     */
    public boolean isLearned(@Nonnull ItemStack chicken) {
        DNA dna = getDNA(chicken);
        return dna.isKnown();
    }

    /**
     * Learn the DNA of a chicken. This returns a new {@link ItemStack} with known DNA.
     *
     * @param chicken The chicken {@link ItemStack}.
     * @return The new chicken {@link ItemStack}.
     */
    @Nonnull
    public ItemStack learnDNA(@Nonnull ItemStack chicken) {
        ItemStack item = chicken.clone();
        ItemMeta meta = item.getItemMeta();

        if (PersistentDataAPI.hasIntArray(meta, Keys.POCKET_CHICKEN_DNA)) {
            DNA dna = new DNA(PersistentDataAPI.getIntArray(meta, Keys.POCKET_CHICKEN_DNA));
            dna.learn();
            JsonObject json = PersistentDataAPI.get(meta, Keys.POCKET_CHICKEN_ADAPTER, PocketChicken.ADAPTER);
            setPocketChicken(item, json, dna);
        }

        return item;
    }
}
