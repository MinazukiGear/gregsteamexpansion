package com.hoshino.gregsteamexpansion.compat;

import java.lang.reflect.Method;

/**
 * Resolves the client-only Minecraft singleton without linking client classes
 * on a dedicated server.
 *
 * <p>This helper deliberately lives outside the configured Mixin package.
 * Runtime code merged into a target class may reference it safely, while a
 * nested helper compiled as {@code SomeMixin$Helper} is rejected by Mixin's
 * class loader when that path is first exercised.</p>
 */
public final class GTCEuClientInstanceLookup {
    private GTCEuClientInstanceLookup() {}

    public static boolean isMissing() {
        Method getInstance = Holder.GET_INSTANCE;
        if (getInstance == null) {
            return true;
        }
        try {
            return getInstance.invoke(null) == null;
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }

    /** Resolves the client-only method once, on first use after the dist check. */
    private static final class Holder {
        private static final Method GET_INSTANCE = resolveGetInstance();

        private static Method resolveGetInstance() {
            try {
                return Class.forName("net.minecraft.client.Minecraft").getMethod("getInstance");
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
    }
}
