package net.guizhanss.gcereborn.core.adapters;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import net.guizhanss.gcereborn.GeneticChickengineering;
import net.guizhanss.gcereborn.core.services.LocalizationService;

/**
 * Entity data adapter used by Pocket Chickens.
 *
 * <p>The original implementation stored attribute modifiers by UUID. Bukkit/Paper migrated
 * attribute modifiers to namespaced keys in 1.21, and the compatibility UUID accessor can throw
 * for vanilla keyed modifiers. This implementation stores modern keys and can still read the old
 * JSON shape so existing Pocket Chickens remain usable.</p>
 */
public interface MobAdapter<T extends LivingEntity> extends PersistentDataType<String, JsonObject> {

    String LEGACY_MODIFIER_NAMESPACE = "geneticchickengineering";

    Class<T> getEntityClass();

    default List<String> getLore(JsonObject json) {
        List<String> lore = new LinkedList<>();
        LocalizationService localization = GeneticChickengineering.getLocalization();

        lore.add("");
        lore.add(localization.getString("lores.chicken.health", json.get("_health").getAsDouble()));

        if (!json.get("_customName").isJsonNull()) {
            lore.add(localization.getString("lores.chicken.name", json.get("_customName").getAsString()));
        }

        int fireTicks = json.get("_fireTicks").getAsInt();
        if (fireTicks > 0) {
            lore.add(localization.getString("lores.chicken.on-fire"));
        }

        return lore;
    }

    @Override
    default Class<String> getPrimitiveType() {
        return String.class;
    }

    @Override
    default Class<JsonObject> getComplexType() {
        return JsonObject.class;
    }

    @Override
    default String toPrimitive(JsonObject json, PersistentDataAdapterContext context) {
        return json.toString();
    }

    @Override
    default JsonObject fromPrimitive(String primitive, PersistentDataAdapterContext context) {
        return JsonParser.parseString(primitive).getAsJsonObject();
    }

    default void apply(T entity, JsonObject json) {
        JsonObject attributes = json.getAsJsonObject("_attributes");

        if (attributes != null) {
            for (Map.Entry<String, JsonElement> entry : attributes.entrySet()) {
                Attribute attributeType = resolveAttribute(entry.getKey());
                if (attributeType == null) {
                    continue;
                }

                AttributeInstance instance = entity.getAttribute(attributeType);
                if (instance == null) {
                    continue;
                }

                for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
                    instance.removeModifier(modifier);
                }

                JsonObject attribute = entry.getValue().getAsJsonObject();
                if (attribute.has("base")) {
                    instance.setBaseValue(attribute.get("base").getAsDouble());
                }

                JsonArray modifiers = attribute.getAsJsonArray("modifiers");
                if (modifiers == null) {
                    continue;
                }

                for (JsonElement modifier : modifiers) {
                    JsonObject obj = modifier.getAsJsonObject();
                    NamespacedKey key = readModifierKey(obj);
                    if (key == null) {
                        continue;
                    }

                    double amount = obj.get("amount").getAsDouble();
                    int operationIndex = obj.get("operation").getAsInt();
                    Operation[] operations = Operation.values();
                    if (operationIndex < 0 || operationIndex >= operations.length) {
                        continue;
                    }

                    try {
                        instance.addModifier(new AttributeModifier(key, amount, operations[operationIndex]));
                    } catch (IllegalArgumentException ignored) {
                        // A malformed/duplicate legacy modifier must not make a Pocket Chicken unusable.
                    }
                }
            }
        }

        double health = json.get("_health").getAsDouble();
        AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            health = Math.min(health, maxHealth.getValue());
        }
        entity.setHealth(Math.max(0.01D, health));
        entity.setAbsorptionAmount(json.get("_absorption").getAsDouble());
        entity.setRemoveWhenFarAway(json.get("_removeWhenFarAway").getAsBoolean());

        if (!json.get("_customName").isJsonNull()) {
            entity.setCustomName(json.get("_customName").getAsString());
        }

        entity.setCustomNameVisible(json.get("_customNameVisible").getAsBoolean());
        entity.setAI(json.get("_ai").getAsBoolean());
        entity.setSilent(json.get("_silent").getAsBoolean());
        entity.setGlowing(json.get("_glowing").getAsBoolean());
        entity.setInvulnerable(json.get("_invulnerable").getAsBoolean());
        entity.setCollidable(json.get("_collidable").getAsBoolean());
        entity.setGravity(json.get("_gravity").getAsBoolean());
        entity.setFireTicks(json.get("_fireTicks").getAsInt());

        JsonObject effects = json.getAsJsonObject("_effects");
        if (effects != null) {
            for (Map.Entry<String, JsonElement> entry : effects.entrySet()) {
                PotionEffectType type = PotionEffectType.getByName(entry.getKey());
                if (type == null) {
                    continue;
                }

                JsonObject obj = entry.getValue().getAsJsonObject();
                entity.addPotionEffect(new PotionEffect(
                    type,
                    obj.get("duration").getAsInt(),
                    obj.get("amplifier").getAsInt(),
                    obj.get("ambient").getAsBoolean(),
                    obj.get("particles").getAsBoolean(),
                    obj.get("icon").getAsBoolean()
                ));
            }
        }

        JsonArray tags = json.getAsJsonArray("_scoreboardTags");
        if (tags != null) {
            for (JsonElement tag : tags) {
                entity.addScoreboardTag(tag.getAsString());
            }
        }
    }

    default JsonObject saveData(T entity) {
        JsonObject json = new JsonObject();

        json.addProperty("_type", entity.getType().toString());
        json.addProperty("_health", entity.getHealth());
        json.addProperty("_absorption", entity.getAbsorptionAmount());
        json.addProperty("_removeWhenFarAway", entity.getRemoveWhenFarAway());
        json.addProperty("_customName", entity.getCustomName());
        json.addProperty("_customNameVisible", entity.isCustomNameVisible());
        json.addProperty("_ai", entity.hasAI());
        json.addProperty("_silent", entity.isSilent());
        json.addProperty("_glowing", entity.isGlowing());
        json.addProperty("_invulnerable", entity.isInvulnerable());
        json.addProperty("_collidable", entity.isCollidable());
        json.addProperty("_gravity", entity.hasGravity());
        json.addProperty("_fireTicks", entity.getFireTicks());

        JsonObject attributes = new JsonObject();

        for (Attribute attribute : Registry.ATTRIBUTE) {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance == null) {
                continue;
            }

            JsonObject obj = new JsonObject();
            obj.addProperty("base", instance.getBaseValue());

            JsonArray modifiers = new JsonArray();
            for (AttributeModifier modifier : instance.getModifiers()) {
                JsonObject mod = new JsonObject();
                mod.addProperty("key", modifier.getKey().toString());
                mod.addProperty("operation", modifier.getOperation().ordinal());
                mod.addProperty("amount", modifier.getAmount());
                modifiers.add(mod);
            }

            obj.add("modifiers", modifiers);
            attributes.add(attribute.getKey().toString(), obj);
        }

        json.add("_attributes", attributes);

        JsonObject effects = new JsonObject();
        for (PotionEffect effect : entity.getActivePotionEffects()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("duration", effect.getDuration());
            obj.addProperty("amplifier", effect.getAmplifier());
            obj.addProperty("ambient", effect.isAmbient());
            obj.addProperty("particles", effect.hasParticles());
            obj.addProperty("icon", effect.hasIcon());
            effects.add(effect.getType().getName(), obj);
        }
        json.add("_effects", effects);

        JsonArray tags = new JsonArray();
        for (String tag : entity.getScoreboardTags()) {
            tags.add(tag);
        }
        json.add("_scoreboardTags", tags);

        return json;
    }

    private static Attribute resolveAttribute(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            return null;
        }

        NamespacedKey direct = NamespacedKey.fromString(storedName.toLowerCase(Locale.ROOT));
        if (direct != null) {
            Attribute attribute = Registry.ATTRIBUTE.get(direct);
            if (attribute != null) {
                return attribute;
            }
        }

        // Legacy Bukkit enum names were stored as strings such as GENERIC_MAX_HEALTH.
        String candidate = storedName.toLowerCase(Locale.ROOT).replace('.', '_');
        while (!candidate.isBlank()) {
            Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(candidate));
            if (attribute != null) {
                return attribute;
            }

            int separator = candidate.indexOf('_');
            if (separator < 0 || separator + 1 >= candidate.length()) {
                break;
            }
            candidate = candidate.substring(separator + 1);
        }

        return null;
    }

    private static NamespacedKey readModifierKey(JsonObject obj) {
        if (obj.has("key") && !obj.get("key").isJsonNull()) {
            return NamespacedKey.fromString(obj.get("key").getAsString());
        }

        // Backward compatibility for old Pocket Chickens that stored UUID/name fields.
        String legacy = null;
        if (obj.has("uuid") && !obj.get("uuid").isJsonNull()) {
            legacy = obj.get("uuid").getAsString();
        } else if (obj.has("name") && !obj.get("name").isJsonNull()) {
            legacy = obj.get("name").getAsString();
        }

        if (legacy == null || legacy.isBlank()) {
            return null;
        }

        String safe = legacy.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._/-]", "_");
        return NamespacedKey.fromString(LEGACY_MODIFIER_NAMESPACE + ":legacy_modifier/" + safe);
    }
}
