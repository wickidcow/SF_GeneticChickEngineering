package net.guizhanss.gcereborn.items.machines;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineProcessor;
import io.github.thebusybiscuit.slimefun4.implementation.operations.CraftingOperation;
import io.github.thebusybiscuit.slimefun4.libraries.dough.inventory.InvUtils;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.ItemUtils;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;

import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;

import net.guizhanss.gcereborn.GeneticChickengineering;
import net.guizhanss.gcereborn.items.GCEItems;
import net.guizhanss.gcereborn.utils.ChickenUtils;
import net.guizhanss.gcereborn.utils.GuiItems;

public class ExcitationChamber extends AbstractMachine {

    private static final int[] BACKGROUND = new int[] {
        0, 1, 2, 6, 7, 8,
        9, 10, 11, 15, 16, 17,
        18, 19, 20, 21, 23, 24,
        25, 26
    };
    private static final int[] INPUT_BORDER = new int[] {3, 5, 12, 13, 14};
    private static final int[] OUTPUT_BORDER = new int[] {27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 44};
    private static final int[] INPUT_SLOTS = new int[] {4};
    private static final int[] OUTPUT_SLOTS = new int[] {37, 38, 39, 40, 41, 42, 43};

    public ExcitationChamber(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    @Nonnull
    public ItemStack getProgressBar() {
        return GCEItems.POCKET_CHICKEN.clone();
    }

    @Override
    public int[] getInputSlots() {
        return INPUT_SLOTS;
    }

    @Override
    public int[] getOutputSlots() {
        return OUTPUT_SLOTS;
    }

    @Override
    protected void constructMenu(@Nonnull BlockMenuPreset preset) {
        for (int i : BACKGROUND) {
            preset.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }

        for (int i : INPUT_BORDER) {
            preset.addItem(i, ChestMenuUtils.getInputSlotTexture(), ChestMenuUtils.getEmptyClickHandler());
        }

        for (int i : OUTPUT_BORDER) {
            preset.addItem(i, ChestMenuUtils.getOutputSlotTexture(), ChestMenuUtils.getEmptyClickHandler());
        }

        preset.addItem(INFO_SLOT, GuiItems.BLACK_PANE, ChestMenuUtils.getEmptyClickHandler());

        for (int i : getOutputSlots()) {
            preset.addMenuClickHandler(i, (p, slot, cursor, action) -> cursor != null && !cursor.getType().isAir());
        }
    }

    @Override
    protected void tick(@Nonnull Block b) {
        super.tick(b);
        BlockMenu inv = BlockStorage.getInventory(b);
        MachineProcessor<CraftingOperation> processor = getMachineProcessor();
        if (processor.getOperation(b) != null && findNextRecipe(inv) == null) {
            processor.endOperation(b);
            inv.replaceExistingItem(INFO_SLOT, GuiItems.BLACK_PANE);
        }
    }

    @Override
    @Nullable
    protected MachineRecipe findNextRecipe(@Nonnull BlockMenu menu) {
        var config = GeneticChickengineering.getConfigService();
        for (int slot : getInputSlots()) {
            ItemStack chicken = menu.getItemInSlot(slot);

            if (!ChickenUtils.isPocketChicken(chicken) || !ChickenUtils.isAdult(chicken)) {
                continue;
            }

            ItemStack resourceIcon = ChickenUtils.getResource(chicken);
            int multiplier = getStackMultiplier(chicken, config.isAllowStackedChickens(), config.isPainEnabled());
            ItemStack[] outputs = rollOutputs(resourceIcon, multiplier, config.getResourceFailRate());

            int rawSpeed = config.getResourceBaseTime()
                + ChickenUtils.getResourceTier(chicken)
                - 2 * ChickenUtils.getDNAStrength(chicken);
            int speed = Math.max(1, rawSpeed / Math.max(1, getSpeed()));

            ItemStack recipeChicken = chicken.clone();
            recipeChicken.setAmount(1);

            MachineRecipe recipe = new MachineRecipe(
                config.isTest() ? 1 : speed,
                new ItemStack[] {recipeChicken},
                outputs
            );
            if (!InvUtils.fitAll(menu.toInventory(), recipe.getOutput(), getOutputSlots())) {
                continue;
            }

            if (config.isPainEnabled()) {
                if (!ChickenUtils.survivesPain(chicken) && !config.isPainDeathEnabled()) {
                    continue;
                }
                ChickenUtils.possiblyHarm(chicken);
                if (ChickenUtils.getHealth(chicken) <= 0d) {
                    ItemUtils.consumeItem(chicken, false);
                    GeneticChickengineering.getPlatformSupport().runAt(
                        menu.getLocation(),
                        () -> menu.getLocation().getWorld().playSound(
                            menu.getLocation(),
                            Sound.ENTITY_CHICKEN_DEATH,
                            1f,
                            1f
                        )
                    );
                    continue;
                }
            }

            return recipe;
        }

        return null;
    }

    private int getStackMultiplier(@Nonnull ItemStack chicken, boolean stackingEnabled, boolean painEnabled) {
        if (!stackingEnabled || painEnabled) {
            return 1;
        }

        return Math.max(1, Math.min(chicken.getAmount(), chicken.getMaxStackSize()));
    }

    @Nonnull
    private ItemStack[] rollOutputs(@Nonnull ItemStack resource, int multiplier, int failRate) {
        int eggRolls = 0;
        int resourceRolls = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < multiplier; i++) {
            if (random.nextInt(100) < failRate) {
                eggRolls++;
            } else {
                resourceRolls++;
            }
        }

        List<ItemStack> outputs = new ArrayList<>();
        appendOutput(outputs, resource, (long) resource.getAmount() * resourceRolls);
        appendOutput(outputs, new ItemStack(Material.EGG), eggRolls);
        return outputs.toArray(new ItemStack[0]);
    }

    private void appendOutput(@Nonnull List<ItemStack> outputs, @Nonnull ItemStack template, long totalAmount) {
        if (totalAmount <= 0) {
            return;
        }

        int maxStackSize = Math.max(1, template.getMaxStackSize());
        long remaining = totalAmount;

        while (remaining > 0) {
            ItemStack output = template.clone();
            int amount = (int) Math.min(remaining, maxStackSize);
            output.setAmount(amount);
            outputs.add(output);
            remaining -= amount;
        }
    }
}
