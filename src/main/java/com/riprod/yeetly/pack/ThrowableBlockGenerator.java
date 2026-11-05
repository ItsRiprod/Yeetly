package com.riprod.yeetly.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.DrawType;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.CustomModelTexture;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class ThrowableBlockGenerator {

    private static final String BASE_MODEL_ID = "Yeetly_Projectile_Base";
    private static final String BASE_PROJECTILE_ID = "Yeetly_Projectile";
    private static final String CARRY_INTERACTION_ID = "Yeetly_Carry";
    private static final String ASSET_PREFIX = "Yeetly_";
    private static final String USE_COPY_PREFIX = "Yeetly_Use_";

    private static final String ITEM_PATCH_DIR = "Server/Item/Items/Yeetly/";
    private static final String MODEL_DIR = "Server/Models/Yeetly/Generated/";
    private static final String PROJECTILE_DIR = "Server/ProjectileConfigs/Yeetly/Generated/";
    private static final String USE_COPY_DIR = "Server/Item/RootInteractions/Yeetly/Generated/";
    private static final String COMMON_DIR = "Common/";
    private static final String COMMON_MODEL_DIR = "Items/Yeetly/";
    private static final String TEMPLATE_DIR = "/templates/";
    private static final List<String> TEMPLATES = List.of(
        "Server/Models/Yeetly/" + BASE_MODEL_ID + ".json",
        "Server/ProjectileConfigs/Yeetly/" + BASE_PROJECTILE_ID + ".json"
    );

    private static final String THROW_ROOT = "Root_Throw_Carryable_Block";
    private static final String PLACE_ROOT = "Root_Carry_Place_Block";
    private static final String THROW_HINT = "client.hud.inputBindings.throw";
    private static final String PLACE_HINT = "client.hud.inputBindings.place";
    private static final String THROW_SOUND = "SFX_Hatchet_T1_Swing_RL_Local";
    private static final String CARRY_PROJECTILE_VAR = "Carry_Projectile";

    private final GeneratedPack pack;
    private final HytaleLogger logger;

    public ThrowableBlockGenerator(@Nonnull GeneratedPack pack, @Nonnull HytaleLogger logger) {
        this.pack = pack;
        this.logger = logger;
    }

    public void generate() {
        for (var template : TEMPLATES) {
            pack.writeBytesIfAbsent(template, () -> templateBytes(template));
        }

        var generated = 0;
        for (var entry : Item.getAssetMap().getAssetMap().entrySet()) {
            if (generate(entry.getKey(), entry.getValue())) generated++;
        }
        if (generated > 0) logger.atInfo().log("Generated throwable assets for %d blocks", generated);
    }

    private boolean generate(@Nonnull String itemKey, @Nonnull Item item) {
        if (!item.hasBlockType() || !item.getCarryInteractions().isEmpty()) return false;

        var blockType = BlockType.getAssetMap().getAsset(item.getBlockId());
        if (blockType == null || blockType.getDrawType() != DrawType.Model) return false;

        var modelPath = blockType.getCustomModel();
        var texturePath = heaviestTexture(blockType.getCustomModelTexture());
        if (modelPath == null || texturePath == null) return false;
        if (CommonAssetRegistry.getByName(modelPath) == null || CommonAssetRegistry.getByName(texturePath) == null) return false;

        var chain = rawChain(itemKey);
        if (chain.isEmpty() || !inlineBlockType(chain)) return false;

        var baseUse = firstUse(chain);
        var baseWrapper = wrapUse(itemKey, "", baseUse);
        if (baseUse != null && baseWrapper == null) return false;

        var written = false;
        var assetId = ASSET_PREFIX + itemKey;
        var commonModel = COMMON_MODEL_DIR + itemKey + "/" + fileName(modelPath);
        var commonTexture = COMMON_MODEL_DIR + itemKey + "/" + fileName(texturePath);

        written |= pack.writeBytesIfAbsent(COMMON_DIR + commonModel, () -> commonBytes(modelPath));
        written |= pack.writeBytesIfAbsent(COMMON_DIR + commonTexture, () -> commonBytes(texturePath));
        written |= pack.writeJsonIfAbsent(MODEL_DIR + assetId + ".json", () -> modelAsset(commonModel, commonTexture));
        written |= pack.writeJsonIfAbsent(PROJECTILE_DIR + assetId + ".json", () -> projectileConfig(assetId));
        written |= pack.writeJsonIfAbsent(ITEM_PATCH_DIR + itemKey + ".patch", () -> itemPatch(itemKey, assetId, chain.getFirst(), baseWrapper));
        return written;
    }

    @Nonnull
    private JsonObject itemPatch(@Nonnull String itemKey, @Nonnull String assetId, @Nonnull JsonObject ownRaw, @Nonnull JsonObject baseWrapper) {
        var carryInteractions = new JsonObject();
        carryInteractions.addProperty("Primary", THROW_ROOT);
        carryInteractions.addProperty("Secondary", PLACE_ROOT);

        var hudBindings = new JsonObject();
        hudBindings.addProperty("Primary", THROW_HINT);
        hudBindings.addProperty("Secondary", PLACE_HINT);

        var effects = new JsonObject();
        effects.addProperty("LocalSoundEventId", THROW_SOUND);
        var projectile = new JsonObject();
        projectile.addProperty("Type", "Projectile");
        projectile.addProperty("Config", assetId);
        projectile.add("Effects", effects);
        var carryProjectile = new JsonObject();
        carryProjectile.add("Interactions", array(projectile));
        var interactionVars = new JsonObject();
        interactionVars.add(CARRY_PROJECTILE_VAR, carryProjectile);

        var blockType = new JsonObject();
        blockType.add("Interactions", useInteractions(baseWrapper));
        var stateDefinitions = wrappedStateDefinitions(itemKey, ownRaw);
        if (!stateDefinitions.isEmpty()) {
            var state = new JsonObject();
            state.add("Definitions", stateDefinitions);
            blockType.add("State", state);
        }

        var patch = new JsonObject();
        patch.add("CarryInteractions", carryInteractions);
        patch.add("CarryHudInputBindings", hudBindings);
        patch.add("InteractionVars", interactionVars);
        patch.add("BlockType", blockType);
        return patch;
    }

    @Nonnull
    private JsonObject wrappedStateDefinitions(@Nonnull String itemKey, @Nonnull JsonObject ownRaw) {
        var wrapped = new JsonObject();
        if (!(child(ownRaw, "BlockType", "State", "Definitions") instanceof JsonObject definitions)) return wrapped;

        for (var state : definitions.entrySet()) {
            if (!(state.getValue() instanceof JsonObject definition)) continue;
            var use = child(definition, "Interactions", "Use");
            if (use == null) continue;
            var wrapper = wrapUse(itemKey, "_" + state.getKey(), use);
            if (wrapper == null) continue;

            var stateDefinition = new JsonObject();
            stateDefinition.add("Interactions", useInteractions(wrapper));
            wrapped.add(state.getKey(), stateDefinition);
        }
        return wrapped;
    }

    @Nullable
    private JsonObject wrapUse(@Nonnull String itemKey, @Nonnull String suffix, @Nullable JsonElement original) {
        var condition = new JsonObject();
        condition.addProperty("Type", "Condition");
        condition.addProperty("Crouching", true);
        condition.addProperty("Next", CARRY_INTERACTION_ID);

        var wrapper = new JsonObject();
        if (original != null) {
            String target;
            JsonElement rules;
            if (original.isJsonPrimitive()) {
                target = original.getAsString();
                var rootRaw = readRaw(RootInteraction.getAssetMap().getPath(target));
                rules = rootRaw == null ? null : rootRaw.get("Rules");
            } else if (original instanceof JsonObject inline && hasInteractions(inline)) {
                target = USE_COPY_PREFIX + itemKey + suffix;
                pack.writeJsonIfAbsent(USE_COPY_DIR + target + ".json", inline::deepCopy);
                rules = inline.get("Rules");
            } else {
                return null;
            }

            var run = new JsonObject();
            run.addProperty("Type", "RunRootInteraction");
            run.addProperty("RootInteraction", target);
            condition.add("Failed", run);
            if (rules != null) wrapper.add("Rules", rules.deepCopy());
        }

        wrapper.add("Interactions", array(condition));
        return wrapper;
    }

    @Nonnull
    private static JsonObject useInteractions(@Nonnull JsonObject wrapper) {
        var interactions = new JsonObject();
        interactions.add("Use", wrapper);
        return interactions;
    }

    @Nonnull
    private static JsonObject modelAsset(@Nonnull String model, @Nonnull String texture) {
        var json = new JsonObject();
        json.addProperty("Parent", BASE_MODEL_ID);
        json.addProperty("Model", model);
        json.addProperty("Texture", texture);
        return json;
    }

    @Nonnull
    private static JsonObject projectileConfig(@Nonnull String modelId) {
        var json = new JsonObject();
        json.addProperty("Parent", BASE_PROJECTILE_ID);
        json.addProperty("Model", modelId);
        return json;
    }

    @Nonnull
    private static List<JsonObject> rawChain(@Nonnull String itemKey) {
        var chain = new ArrayList<JsonObject>();
        var visited = new HashSet<String>();
        var key = itemKey;
        while (key != null && visited.add(key)) {
            var raw = readRaw(Item.getAssetMap().getPath(key));
            if (raw == null) break;
            chain.add(raw);
            key = raw.get("Parent") instanceof JsonElement parent && parent.isJsonPrimitive() ? parent.getAsString() : null;
        }
        return chain;
    }

    private static boolean inlineBlockType(@Nonnull List<JsonObject> chain) {
        for (var raw : chain) {
            var blockType = raw.get("BlockType");
            if (blockType != null) return blockType.isJsonObject();
        }
        return false;
    }

    @Nullable
    private static JsonElement firstUse(@Nonnull List<JsonObject> chain) {
        for (var raw : chain) {
            var use = child(raw, "BlockType", "Interactions", "Use");
            if (use != null) return use;
        }
        return null;
    }

    private static boolean hasInteractions(@Nonnull JsonObject root) {
        return root.get("Interactions") instanceof JsonArray interactions && !interactions.isEmpty();
    }

    @Nullable
    private static JsonElement child(@Nonnull JsonObject root, @Nonnull String... keys) {
        JsonElement current = root;
        for (var key : keys) {
            if (!(current instanceof JsonObject object)) return null;
            current = object.get(key);
        }
        return current;
    }

    @Nullable
    private static JsonObject readRaw(@Nullable Path path) {
        if (path == null) return null;
        try {
            return JsonParser.parseString(Files.readString(path)) instanceof JsonObject object ? object : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static String heaviestTexture(@Nullable CustomModelTexture[] textures) {
        if (textures == null) return null;
        CustomModelTexture heaviest = null;
        for (var texture : textures) {
            if (heaviest == null || texture.getWeight() > heaviest.getWeight()) heaviest = texture;
        }
        return heaviest == null ? null : heaviest.getTexture();
    }

    @Nullable
    private byte[] templateBytes(@Nonnull String relativePath) {
        try (var stream = ThrowableBlockGenerator.class.getResourceAsStream(TEMPLATE_DIR + relativePath)) {
            if (stream != null) return stream.readAllBytes();
            logger.atWarning().log("Missing bundled template %s", relativePath);
            return null;
        } catch (IOException e) {
            logger.atWarning().withCause(e).log("Failed to read bundled template %s", relativePath);
            return null;
        }
    }

    @Nullable
    private static byte[] commonBytes(@Nonnull String name) {
        var asset = CommonAssetRegistry.getByName(name);
        return asset == null ? null : asset.getBlob().join();
    }

    @Nonnull
    private static String fileName(@Nonnull String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    @Nonnull
    private static JsonArray array(@Nonnull JsonElement element) {
        var array = new JsonArray();
        array.add(element);
        return array;
    }
}
