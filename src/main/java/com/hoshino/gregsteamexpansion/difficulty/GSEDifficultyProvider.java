package com.hoshino.gregsteamexpansion.difficulty;

import org.jetbrains.annotations.Nullable;

/**
 * Optional startup authority supplied by a modpack core.
 *
 * <p>The provider is registered during mod construction. It may return
 * {@code null} until its own Forge config has loaded, then asks
 * {@link GSEDifficultyAuthority#resolveExternalProvider()} to capture the
 * selection exactly once.</p>
 */
public interface GSEDifficultyProvider {
    /** Stable mod id used in diagnostics. */
    String ownerModId();

    /** The complete selected tier and profile, or {@code null} before config load. */
    @Nullable
    GSEDifficultySelection selection();
}
