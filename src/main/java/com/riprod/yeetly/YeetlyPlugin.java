package com.riprod.yeetly;

import com.hypixel.hytale.server.core.asset.LoadAssetEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.riprod.patchly.PatchManager;
import com.riprod.yeetly.carry.ThrownBlockSystem;
import com.riprod.yeetly.carry.YeetlyLandInteraction;
import com.riprod.yeetly.pack.GeneratedPack;
import com.riprod.yeetly.pack.ThrowableBlockGenerator;

import javax.annotation.Nonnull;

public final class YeetlyPlugin extends JavaPlugin {

    private final PatchManager patchManager;

    public YeetlyPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        patchManager = new PatchManager(this);
    }

    @Override
    protected void setup() {
        patchManager.install();

        getCodecRegistry(Interaction.CODEC).register(YeetlyLandInteraction.ID, YeetlyLandInteraction.class, YeetlyLandInteraction.CODEC);
        getEntityStoreRegistry().registerSystem(new ThrownBlockSystem());

        var pack = new GeneratedPack(getManifest(), getLogger());
        getEventRegistry().register(LoadAssetEvent.PRIORITY_LOAD_LATE, LoadAssetEvent.class, event -> {
            new ThrowableBlockGenerator(pack, getLogger()).generate();
            pack.register();
        });
    }
}
