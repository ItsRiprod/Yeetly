package com.riprod.yeetly.carry;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.BlockRotation;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Rotation;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockPlaceUtils;
import com.hypixel.hytale.server.core.modules.interaction.components.CarriedBlock;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import org.joml.Vector3d;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.List;

public final class YeetlyLandInteraction extends SimpleInstantInteraction {

    public static final String ID = "YeetlyLand";

    public static final BuilderCodec<YeetlyLandInteraction> CODEC = BuilderCodec.builder(YeetlyLandInteraction.class, YeetlyLandInteraction::new, SimpleInstantInteraction.CODEC)
        .documentation("Places the block carried by this projectile at its hit location, or drops it as an item when it does not fit.")
        .build();

    private static final Vector3i PLACEMENT_NORMAL = new Vector3i(0, 1, 0);

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        var commandBuffer = context.getCommandBuffer();
        assert commandBuffer != null;

        var projectileRef = context.getEntity();
        var throwerRef = context.getOwningEntity();
        var carried = commandBuffer.getComponent(projectileRef, CarriedBlock.getComponentType());
        var hitLocation = context.getMetaStore().getIfPresentMetaObject(Interaction.HIT_LOCATION);
        if (carried == null || hitLocation == null || throwerRef == null || !throwerRef.isValid()) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        var blockTypeKey = carried.getBlockTypeKey();
        var itemStack = carried.toItemStack();
        commandBuffer.tryRemoveComponent(projectileRef, CarriedBlock.getComponentType());
        if (blockTypeKey == null || itemStack == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        var cell = new Vector3i(MathUtil.floor(hitLocation.x), MathUtil.floor(hitLocation.y), MathUtil.floor(hitLocation.z));
        var chunkStore = commandBuffer.getExternalData().getWorld().getChunkStore();
        var sectionRef = chunkStore.getChunkSectionReferenceAtBlock(cell.x, cell.y, cell.z);

        var placed = sectionRef != null && sectionRef.isValid() && BlockPlaceUtils.placeBlock(
            throwerRef,
            itemStack,
            blockTypeKey,
            new SimpleItemContainer((short) 1),
            PLACEMENT_NORMAL,
            cell,
            new BlockRotation(Rotation.None, Rotation.None, Rotation.None),
            (byte) 0,
            false,
            sectionRef,
            chunkStore.getStore(),
            commandBuffer,
            false,
            false,
            false);

        if (placed) return;

        var dropPosition = new Vector3d(hitLocation.x, hitLocation.y, hitLocation.z);
        for (var holder : ItemComponent.generateItemDrops(commandBuffer, List.of(itemStack), dropPosition, Rotation3f.IDENTITY)) {
            commandBuffer.addEntity(holder, AddReason.SPAWN);
        }
    }

    @Override
    protected void simulateFirstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
    }
}
