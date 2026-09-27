package net.guizhanss.gcereborn.core.services;

import java.text.MessageFormat;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Preconditions;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import net.guizhanss.gcereborn.GeneticChickengineering;
import net.guizhanss.guizhanlib.minecraft.utils.ChatUtil;
import net.guizhanss.guizhanlib.slimefun.addon.SlimefunLocalization;

@SuppressWarnings("ConstantConditions")
public final class LocalizationService extends SlimefunLocalization {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    public LocalizationService(GeneticChickengineering plugin) {
        super(plugin);
    }

    @ParametersAreNonnullByDefault
    @Nonnull
    public String getString(String key, Object... args) {
        return MessageFormat.format(getString(key), args);
    }

    @ParametersAreNonnullByDefault
    public void sendMessage(CommandSender sender, String messageKey, Object... args) {
        Preconditions.checkArgument(sender != null, "CommandSender cannot be null");
        Preconditions.checkArgument(messageKey != null, "Message key cannot be null");

        ChatUtil.send(sender, MessageFormat.format(getString("messages." + messageKey), args));
    }

    @ParametersAreNonnullByDefault
    public void sendActionbarMessage(Player p, String messageKey, Object... args) {
        Preconditions.checkArgument(p != null, "Player cannot be null");
        Preconditions.checkArgument(messageKey != null, "Message key cannot be null");

        String message = MessageFormat.format(getString("messages." + messageKey), args);

        p.sendActionBar(LEGACY_SERIALIZER.deserialize(ChatUtil.color(message)));
    }
}
