package net.guizhanss.gcereborn;

import java.io.File;
import java.lang.reflect.Method;
import java.util.logging.Level;

import javax.annotation.Nonnull;

import com.google.common.base.Preconditions;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;

import io.github.thebusybiscuit.slimefun4.libraries.dough.updater.BlobBuildUpdater;

import net.guizhanss.gcereborn.core.commands.GCECommand;
import net.guizhanss.gcereborn.core.compat.PlatformSupport;
import net.guizhanss.gcereborn.core.services.ConfigurationService;
import net.guizhanss.gcereborn.core.services.IntegrationService;
import net.guizhanss.gcereborn.core.services.LocalizationService;
import net.guizhanss.gcereborn.setup.Items;
import net.guizhanss.gcereborn.setup.Researches;
import net.guizhanss.guizhanlib.slimefun.addon.AbstractAddon;
import net.guizhanss.guizhanlib.updater.GuizhanBuildsUpdater;

import org.bstats.bukkit.Metrics;

public class GeneticChickengineering extends AbstractAddon {

    private static final String DEFAULT_LANG = "en-US";

    private ConfigurationService configService;
    private LocalizationService localization;
    private IntegrationService integrationService;
    private PlatformSupport platformSupport;
    private boolean debugEnabled = false;

    public GeneticChickengineering() {
        super("wickidcow", "SF_GeneticChickEngineering", "master", "options.auto-update");
    }

    @Nonnull
    public static ConfigurationService getConfigService() {
        return inst().configService;
    }

    @Nonnull
    public static LocalizationService getLocalization() {
        return inst().localization;
    }

    @Nonnull
    public static IntegrationService getIntegrationService() {
        return inst().integrationService;
    }

    @Nonnull
    public static PlatformSupport getPlatformSupport() {
        return inst().platformSupport;
    }

    public static void debug(@Nonnull String message, @Nonnull Object... args) {
        Preconditions.checkNotNull(message, "message cannot be null");

        if (inst().debugEnabled) {
            inst().getLogger().log(Level.INFO, "[DEBUG] " + message, args);
        }
    }

    @Nonnull
    private static GeneticChickengineering inst() {
        return getInstance();
    }

    @Override
    public void enable() {
        platformSupport = new PlatformSupport(this);

        if (!platformSupport.isSupportedMinecraftVersion()) {
            log(
                Level.SEVERE,
                "Unsupported Minecraft/Paper version {0}. SF Genetic ChickEngineering requires 1.21.11 or newer.",
                Bukkit.getMinecraftVersion()
            );
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!platformSupport.isPaperFamily()) {
            log(
                Level.SEVERE,
                "Unsupported server software. Use Paper, Purpur, Leaf, Folia, or another compatible Paper-family server."
            );
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        log(
            Level.INFO,
            "Platform: {0} | Minecraft: {1} | Server: {2}",
            platformSupport.getPlatform(),
            Bukkit.getMinecraftVersion(),
            Bukkit.getVersion()
        );

        File datadir = this.getDataFolder();
        if (!datadir.exists() && !datadir.mkdirs()) {
            log(Level.WARNING, "Could not create plugin data directory: {0}", datadir.getAbsolutePath());
        }

        // config
        configService = new ConfigurationService(this);

        // debug
        debugEnabled = configService.isDebug();

        // localization
        log(Level.INFO, "Loading language...");
        String lang = configService.getLang();
        localization = new LocalizationService(this);
        localization.addLanguage(lang);
        if (!lang.equals(DEFAULT_LANG)) {
            localization.addLanguage(DEFAULT_LANG);
        }
        localization.setIdPrefix("GCE_");
        log(Level.INFO, localization.getString("console.load.language"), lang);

        // items
        log(Level.INFO, localization.getString("console.load.items"));
        Items.setup(this);

        // researches
        log(Level.INFO, localization.getString("console.load.researches"));
        Researches.setup();

        // commands
        if (configService.isCommandsEnabled()) {
            PluginCommand command = getCommand("geneticchickengineering");
            if (command == null) {
                log(Level.SEVERE, localization.getString("console.load.commands-fail"));
            } else {
                new GCECommand(command).register();
            }
        }

        // integrations
        log(Level.INFO, localization.getString("console.load.integrations"));
        integrationService = new IntegrationService(this);

        // metrics
        setupMetrics();
    }

    @Override
    public void disable() {
        // No addon-owned repeating tasks are retained here. Slimefun owns machine tick lifecycle.
    }

    private void setupMetrics() {
        new Metrics(this, 20243);
    }

    @Override
    protected void autoUpdate() {
        if (getPluginVersion().startsWith("Dev")) {
            new BlobBuildUpdater(this, getFile(), getGithubRepo()).start();
        } else if (getPluginVersion().startsWith("Build")) {
            try {
                Class<?> clazz = Class.forName("net.guizhanss.guizhanlibplugin.updater.GuizhanUpdater");
                Method updaterStart = clazz.getDeclaredMethod(
                    "start",
                    Plugin.class,
                    File.class,
                    String.class,
                    String.class,
                    String.class
                );
                updaterStart.invoke(null, this, getFile(), getGithubUser(), getGithubRepo(), getGithubBranch());
            } catch (Exception ignored) {
                new GuizhanBuildsUpdater(this, getFile(), getGithubUser(), getGithubRepo(), getGithubBranch()).start();
            }
        }
    }
}
