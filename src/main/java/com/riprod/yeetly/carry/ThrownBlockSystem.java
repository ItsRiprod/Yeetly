package com.riprod.yeetly.carry;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.modules.interaction.components.CarriedBlock;
import com.hypixel.hytale.server.core.modules.projectile.event.ProjectileLaunchEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public final class ThrownBlockSystem extends EntityEventSystem<EntityStore, ProjectileLaunchEvent> {

    public ThrownBlockSystem() {
        super(ProjectileLaunchEvent.class);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return CarriedBlock.getComponentType();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull ProjectileLaunchEvent event) {
        var carried = archetypeChunk.getComponent(index, CarriedBlock.getComponentType());
        if (carried == null || carried.getBlockTypeKey() == null) return;

        var payload = (CarriedBlock) carried.clone();
        var projectileRef = event.getProjectileRef();
        commandBuffer.run(deferredStore -> {
            if (!projectileRef.isValid()) return;
            deferredStore.putComponent(projectileRef, CarriedBlock.getComponentType(), payload);
        });
    }
}
