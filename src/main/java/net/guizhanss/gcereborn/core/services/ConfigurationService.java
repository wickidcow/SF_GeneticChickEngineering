package net.guizhanss.gcereborn.core.services;

import javax.annotation.Nonnull;

import net.guizhanss.gcereborn.GeneticChickengineering;
import net.guizhanss.guizhanlib.slimefun.addon.AddonConfig;

import lombok.AccessLevel;
import lombok.Getter;

@Getter
public final class ConfigurationService {

    @Getter(AccessLevel.NONE)
    private final AddonConfig config;

    private boolean autoUpdate;
    private boolean debug;
    private boolean test;
    private String lang;
    private boolean displayResources;
    private int maxMutation;
    private int mutationRate;
    private int resourceFailRate;
    private int resourceBaseTime;
    private boolean allowStackedChickens;
    private boolean painEnabled;
    private double painChance;
    private boolean painDeathEnabled;
    private int healRate;
    private boolean netherWaterEnabled;
    private boolean growthChamberEnabled;
    private int growthChamberTime;
    private boolean commandsEnabled;

    public ConfigurationService(GeneticChickengineering plugin) {
        config = new AddonConfig(plugin, "config.yml");
        reload();
    }

    public void reload() {
        config.reload();

        autoUpdate = config.getBoolean("options.auto-update", true);
        debug = config.getBoolean("options.debug", false);
        test = config.getBoolean("options.test", false);
        lang = config.getString("options.lang", "en-US");
        displayResources = config.getBoolean("options.display-resource-in-name", true);
        maxMutation = config.getInt("options.max-mutation", 1, 2, 6);
        mutationRate = config.getInt("options.mutation-rate", 1, 30, 100);
        resourceFailRate = config.getInt("options.resource-fail-rate", 0, 0, 100);
        resourceBaseTime = config.getInt("options.resource-base-time", 14, 14, 100);
        allowStackedChickens = config.getBoolean("options.allow-stacked-chickens", false);
        painEnabled = config.getBoolean("options.enable-pain", false);
        painChance = config.getDouble("options.pain-chance", 0d, 2d, 100d);
        painDeathEnabled = config.getBoolean("options.pain-kills", false);
        healRate = config.getInt("options.heal-rate", 1, 2, 120);
        netherWaterEnabled = config.getBoolean("options.allow-nether-water", false);
        growthChamberEnabled = config.getBoolean("options.enable-growth-chamber", false);
        growthChamberTime = config.getInt("options.growth-chamber-time", 1, 60, 600);
        commandsEnabled = config.getBoolean("commands.enabled", true);

        config.save();
    }

    public boolean isSubCommandEnabled(@Nonnull String subCommand) {
        return config.getBoolean("commands.subcommands." + subCommand + ".enabled", true);
    }
}
