package com.hoshino.gregsteamexpansion.terminal;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Bounded, client-safe representation of one selected terminal project. */
public record UltimateTerminalSnapshot(
        int revision,
        List<TargetInfo> targets,
        int selectedTarget,
        ResourceLocation dimension,
        BlockPos controller,
        List<CellInfo> cells,
        List<MaterialInfo> selectedMaterials,
        List<MaterialInfo> batchMaterials,
        List<CandidateInfo> candidates,
        List<ChannelInfo> channels,
        StructureInfo structure,
        boolean targetOverride,
        boolean unlimitedMaterials,
        String error) {

    public static final int MAX_CELLS = 8192;
    public static final int MAX_CANDIDATES = 256;
    public static final int MAX_MATERIALS = 256;
    public static final int MAX_CHANNELS = UltimateTerminalConfig.MAX_SELECTION_CHANNELS;

    public record TargetInfo(ResourceLocation dimension, BlockPos pos, String name) {}
    public record CellInfo(BlockPos pos, ItemStack expected, UltimateStructurePlanner.CellStatus status) {}
    public record MaterialInfo(ItemStack stack, long required, long inventory, long network) {
        public long missing() { return Math.max(0, required - inventory - network); }
    }
    public record CandidateInfo(ItemStack stack, int requested, int present, int maximum,
                                boolean configurable) {}
    public record ChannelInfo(String id, int selected, List<ItemStack> options) {}
    public record StructureInfo(int selected, List<String> options) {
        public static StructureInfo empty() { return new StructureInfo(0, List.of()); }
    }

    public static UltimateTerminalSnapshot from(UltimateTerminalWorldData.PreviewData preview, int revision) {
        if (preview.targets().isEmpty()) {
            return new UltimateTerminalSnapshot(revision, List.of(), 0,
                    ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), BlockPos.ZERO,
                    List.of(), List.of(), List.of(), List.of(), List.of(), StructureInfo.empty(),
                    false, preview.unlimitedMaterials(), preview.error());
        }
        var target = preview.targets().get(Math.max(0,
                Math.min(preview.selectedTarget(), preview.targets().size() - 1)));
        List<TargetInfo> targets = preview.targets().stream()
                .limit(UltimateTerminalWorldData.MAX_TARGETS)
                .map(value -> new TargetInfo(value.dimension(), value.pos(), value.name()))
                .toList();
        List<CellInfo> cells = preview.plan().cells().stream().limit(MAX_CELLS)
                .map(value -> new CellInfo(value.pos(), value.expected().copyWithCount(1), value.status()))
                .toList();
        List<MaterialInfo> selectedMaterials = preview.selectedMaterials().stream().limit(MAX_MATERIALS)
                .map(value -> new MaterialInfo(value.stack().copy(), value.required(), value.inventory(), value.network()))
                .toList();
        List<MaterialInfo> batchMaterials = preview.batchMaterials().stream().limit(MAX_MATERIALS)
                .map(value -> new MaterialInfo(value.stack().copy(), value.required(), value.inventory(), value.network()))
                .toList();
        List<CandidateInfo> candidates = preview.plan().candidates().stream().limit(MAX_CANDIDATES)
                .map(value -> new CandidateInfo(value.stack().copyWithCount(1), value.requested(),
                        value.present(), value.maximum(), value.configurable()))
                .toList();
        List<ChannelInfo> channels = preview.plan().channels().stream().limit(MAX_CHANNELS)
                .map(value -> new ChannelInfo(value.id(), value.selected(), value.options().stream()
                        .limit(UltimateTerminalConfig.MAX_CHANNEL_OPTIONS)
                        .map(stack -> stack.copyWithCount(1)).toList()))
                .toList();
        StructureInfo structure = new StructureInfo(preview.plan().structure().selected(),
                preview.plan().structure().options());
        return new UltimateTerminalSnapshot(revision, targets, preview.selectedTarget(), target.dimension(),
                target.pos(), cells, selectedMaterials, batchMaterials, candidates,
                channels, structure, preview.targetOverride(), preview.unlimitedMaterials(),
                preview.error() == null ? "" : preview.error());
    }
}
