package com.hoshino.gregsteamexpansion.recipe;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Invalidates lazy server-side recipe caches after datapack reloads and server shutdown. */
@Mod.EventBusSubscriber(modid = GregSteamExpansion.MOD_ID)
public final class RecipeCacheLifecycle {
    private static final AtomicLong REVISION = new AtomicLong();

    private RecipeCacheLifecycle() {}

    public static long revision() {
        return REVISION.get();
    }

    public static void invalidate() {
        REVISION.incrementAndGet();
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener((PreparableReloadListener) (barrier, resources, preparations, executions,
                backgroundExecutor, gameExecutor) -> CompletableFuture.completedFuture(null)
                .thenCompose(barrier::wait)
                .thenRunAsync(RecipeCacheLifecycle::invalidate, gameExecutor));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        invalidate();
    }
}
