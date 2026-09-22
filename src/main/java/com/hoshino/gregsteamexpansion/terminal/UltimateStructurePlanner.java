package com.hoshino.gregsteamexpansion.terminal;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.hoshino.gregsteamexpansion.difficulty.GSEDifficultyConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Resolves a deterministic, material-backed blueprint from a GTCEu pattern. */
public final class UltimateStructurePlanner {
    private static final Set<String> UNIFORM_CANDIDATE_ERRORS = Set.of(
            "gtceu.multiblock.pattern.error.coils",
            "gtceu.multiblock.pattern.error.filters",
            "gtceu.multiblock.pattern.error.batteries");
    private static final Field BLOCK_MATCHES;
    private static final Field CENTER_OFFSET;

    static {
        try {
            BLOCK_MATCHES = BlockPattern.class.getDeclaredField("blockMatches");
            CENTER_OFFSET = BlockPattern.class.getDeclaredField("centerOffset");
            BLOCK_MATCHES.setAccessible(true);
            CENTER_OFFSET.setAccessible(true);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private UltimateStructurePlanner() {}

    public record Placement(BlockPos pos, ItemStack stack, ItemStack replaced) {
        public Placement(BlockPos pos, ItemStack stack) {
            this(pos, stack, ItemStack.EMPTY);
        }
    }

    public enum CellStatus {
        SATISFIED,
        MISSING,
        CONFLICT,
        UPGRADE,
        REMOVE,
        UNLOADED
    }

    public record Cell(BlockPos pos, ItemStack expected, CellStatus status) {}

    public record CandidateChoice(ItemStack stack, int requested, int present, int maximum,
                                  boolean configurable) {}

    public record ChannelChoice(String id, int selected, List<ItemStack> options) {}

    public record StructureChoice(int selected, List<String> options) {
        static StructureChoice empty() {
            return new StructureChoice(UltimateTerminalConfig.AUTO_CHANNEL_SELECTION, List.of());
        }
    }

    public record ModuleChoice(int selected, List<String> options) {
        static ModuleChoice empty() {
            return new ModuleChoice(UltimateTerminalConfig.AUTO_CHANNEL_SELECTION, List.of());
        }
    }

    public record Plan(List<Placement> placements, List<Cell> cells,
                       List<CandidateChoice> candidates, List<ChannelChoice> channels,
                       StructureChoice structure, ModuleChoice module, String error) {
        public boolean valid() {
            return error == null;
        }

        static Plan failed(String error) {
            return new Plan(List.of(), List.of(), List.of(), List.of(), StructureChoice.empty(),
                    ModuleChoice.empty(), error);
        }
    }

    public static Plan plan(ServerLevel level, BlockPos controllerPos, int requestedRepeats,
                            int requestedCoilTier, boolean buildHatches, boolean upgrade) {
        return plan(level, controllerPos,
                new TerminalBuildProfile(requestedRepeats, requestedCoilTier, buildHatches), upgrade);
    }

    public static Plan plan(ServerLevel level, BlockPos controllerPos,
                             TerminalBuildProfile profile, boolean upgrade) {
        return plan(level, controllerPos, profile, upgrade, null, null);
    }

    public static Plan planDismantle(ServerLevel level, BlockPos controllerPos,
                                     TerminalBuildProfile profile) {
        MetaMachine machine = MetaMachine.getMachine(level, controllerPos);
        if (!(machine instanceof IMultiController controller)) return Plan.failed("not_controller");
        PatternSelection formed = formedPattern(controller);
        Plan current;
        if (formed == null) {
            // A partially built or damaged structure has no formed-pattern state. In that case the
            // terminal's selected size/repetition profile is the blueprint, and only cells which
            // still contain a legal candidate are eligible for removal.
            current = plan(level, controllerPos, profile, false, null, null);
        } else {
            int[] repetitions = formed.pattern().getFormedRepetitionCount().clone();
            current = plan(level, controllerPos, profile, false, repetitions, formed);
        }
        if (current.cells().isEmpty() && current.error() != null) return current;
        List<Placement> removals = new ArrayList<>();
        List<Cell> cells = new ArrayList<>();
        for (Cell cell : current.cells()) {
            if (cell.status() != CellStatus.SATISFIED || cell.pos().equals(controllerPos)
                    || level.getBlockEntity(cell.pos()) != null || level.isEmptyBlock(cell.pos())) continue;
            ItemStack stack = level.getBlockState(cell.pos()).getBlock().asItem().getDefaultInstance();
            if (stack.isEmpty()) continue;
            removals.add(new Placement(cell.pos(), stack.copyWithCount(1)));
            cells.add(new Cell(cell.pos(), stack.copyWithCount(1), CellStatus.REMOVE));
        }
        return new Plan(List.copyOf(removals), List.copyOf(cells), List.of(), current.channels(),
                current.structure(), current.module(), null);
    }

    private static Plan plan(ServerLevel level, BlockPos controllerPos,
                             TerminalBuildProfile profile, boolean upgrade, int[] formedRepetitions,
                             PatternSelection forcedPattern) {
        MetaMachine machine = MetaMachine.getMachine(level, controllerPos);
        if (!(machine instanceof IMultiController controller)) {
            return Plan.failed("not_controller");
        }
        try {
            PatternSelection selectedPattern = forcedPattern == null
                    ? selectedPattern(controller, profile) : forcedPattern;
            BlockPattern pattern = selectedPattern.pattern();
            TraceabilityPredicate[][][] matches =
                    (TraceabilityPredicate[][][]) BLOCK_MATCHES.get(pattern);
            int[] center = (int[]) CENTER_OFFSET.get(pattern);
            Direction front = controller.self().getFrontFacing();
            Direction up = controller.self().getUpwardsFacing();
            boolean flipped = controller.self().isFlipped();
            RelativeDirection[] dirs = pattern.structureDir;

            Map<SimplePredicate, Integer> global = new IdentityHashMap<>();
            List<Placement> placements = new ArrayList<>();
            List<Cell> cells = new ArrayList<>();
            Map<ResourceLocation, Integer> remainingRequests = new TreeMap<>(profile.partTargets());
            Map<ResourceLocation, CandidateAccumulator> candidateStats = new TreeMap<>();
            String firstError = null;
            int z = -center[4];
            for (int aisle = 0; aisle < matches.length; aisle++) {
                int min = pattern.aisleRepetitions[aisle][0];
                int max = pattern.aisleRepetitions[aisle][1];
                int formed = formedRepetitions != null && aisle < formedRepetitions.length
                        ? formedRepetitions[aisle] : 0;
                int repetitions = formed > 0 ? Math.max(min, Math.min(max, formed))
                        : min == max ? min : Math.max(min,
                                Math.min(max, profile.repeatCount() > 0 ? profile.repeatCount() : min));
                for (int repetition = 0; repetition < repetitions; repetition++, z++) {
                    Map<SimplePredicate, Integer> layer = new IdentityHashMap<>();
                    for (int yIndex = 0, y = -center[1]; yIndex < matches[aisle].length; yIndex++, y++) {
                        for (int xIndex = 0, x = -center[0];
                             xIndex < matches[aisle][yIndex].length; xIndex++, x++) {
                            TraceabilityPredicate predicate = matches[aisle][yIndex][xIndex];
                            BlockPos relative = relativeOffset(x, y, z, front, up, flipped, dirs);
                            BlockPos pos = controllerPos.offset(relative);
                            if (predicate.isController || pos.equals(controllerPos) || predicate.isAir() || predicate.isAny()) {
                                continue;
                            }
                            collectCandidateStats(predicate, candidateStats);
                            Candidate existing = level.hasChunkAt(pos)
                                    ? matchingCandidate(level, pos, predicate) : null;
                            Candidate desiredCoil = selectedCoil(predicate, profile);
                            ChannelMatch desiredChannel = closestChannel(predicate, profile);
                            boolean replaceCoil = existing != null && desiredCoil != null && upgrade
                                    && existing.stack().getItem() != desiredCoil.stack().getItem();
                            boolean replaceChannel = existing != null && desiredChannel != null && upgrade
                                    && existing.stack().getItem() != desiredChannel.candidate().stack().getItem()
                                    && channelContains(desiredChannel.id(), existing.stack());
                            if (existing != null && !replaceCoil && !replaceChannel) {
                                increment(global, existing.predicate);
                                increment(layer, existing.predicate);
                                consumeRequest(remainingRequests, existing.stack());
                                markPresent(candidateStats, existing.stack());
                                cells.add(new Cell(pos.immutable(), existing.stack().copyWithCount(1),
                                        CellStatus.SATISFIED));
                                continue;
                            }
                            Candidate selected = replaceCoil ? desiredCoil
                                    : replaceChannel ? desiredChannel.candidate()
                                    : selectCandidate(predicate, global, layer, profile, remainingRequests);
                            if (selected == null || selected.stack.isEmpty()
                                    || !(selected.stack.getItem() instanceof BlockItem)) {
                                if (hasOnlyMachineCandidates(predicate)) continue;
                                if (firstError == null) firstError = "no_candidate@" + pos.toShortString();
                                continue;
                            }
                            if (!level.hasChunkAt(pos)) {
                                cells.add(new Cell(pos.immutable(), selected.stack.copyWithCount(1),
                                        CellStatus.UNLOADED));
                                if (firstError == null) firstError = "chunk_unloaded@" + pos.toShortString();
                                increment(global, selected.predicate);
                                increment(layer, selected.predicate);
                                consumeRequest(remainingRequests, selected.stack());
                                continue;
                            }
                            ItemStack replaced = ItemStack.EMPTY;
                            CellStatus status = CellStatus.MISSING;
                            if (!level.isEmptyBlock(pos) && !level.getBlockState(pos).canBeReplaced()) {
                                Block current = level.getBlockState(pos).getBlock();
                                Block desired = ((BlockItem) selected.stack.getItem()).getBlock();
                                if (!upgrade || !(current instanceof CoilBlock) || !(desired instanceof CoilBlock)
                                        || level.getBlockEntity(pos) != null) {
                                    cells.add(new Cell(pos.immutable(), selected.stack.copyWithCount(1),
                                            CellStatus.CONFLICT));
                                    if (firstError == null) firstError = "collision@" + pos.toShortString();
                                    increment(global, selected.predicate);
                                    increment(layer, selected.predicate);
                                    consumeRequest(remainingRequests, selected.stack());
                                    continue;
                                }
                                replaced = current.asItem().getDefaultInstance();
                                if (replaced.isEmpty()) {
                                    cells.add(new Cell(pos.immutable(), selected.stack.copyWithCount(1),
                                            CellStatus.CONFLICT));
                                    if (firstError == null) firstError = "unreplaceable@" + pos.toShortString();
                                    continue;
                                }
                                status = CellStatus.UPGRADE;
                            }
                            placements.add(new Placement(pos.immutable(), selected.stack.copyWithCount(1), replaced));
                            cells.add(new Cell(pos.immutable(), selected.stack.copyWithCount(1), status));
                            increment(global, selected.predicate);
                            increment(layer, selected.predicate);
                            consumeRequest(remainingRequests, selected.stack());
                        }
                    }
                }
            }
            remainingRequests.entrySet().removeIf(entry -> {
                CandidateAccumulator accumulator = candidateStats.get(entry.getKey());
                return accumulator != null && !accumulator.configurable;
            });
            ModuleSelection module = selectedModule(machine, profile);
            String moduleError = appendModule(level, module.selected(), placements, cells);
            if (firstError == null) firstError = moduleError;
            for (var entry : remainingRequests.entrySet()) {
                if (entry.getValue() > 0 && firstError == null) {
                    firstError = "requested_part_unavailable:" + entry.getKey() + ":" + entry.getValue();
                }
            }
            List<CandidateChoice> candidates = candidateStats.entrySet().stream()
                    .map(entry -> new CandidateChoice(entry.getValue().stack.copyWithCount(1),
                            profile.partTargets().getOrDefault(entry.getKey(), 0), entry.getValue().present,
                            entry.getValue().maximum, entry.getValue().configurable))
                    .sorted(Comparator.comparing(choice -> ForgeRegistries.ITEMS.getKey(choice.stack().getItem()).toString()))
                    .toList();
            return new Plan(List.copyOf(placements), List.copyOf(cells), candidates,
                    channelChoices(profile, candidateStats), selectedPattern.choice(), module.choice(), firstError);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return Plan.failed("pattern_error:" + exception.getClass().getSimpleName());
        }
    }

    private static PatternSelection selectedPattern(IMultiController controller, TerminalBuildProfile profile) {
        if (controller instanceof UltimateTerminalStructureVariants provider) {
            List<UltimateTerminalStructureVariants.Variant> variants = provider.terminalStructureVariants();
            if (!variants.isEmpty()) {
                int configured = profile.channelSelection(UltimateTerminalStructureVariants.CHANNEL_ID);
                int index = configured < 0 ? variants.size() - 1 : Math.min(configured, variants.size() - 1);
                return new PatternSelection(variants.get(index).pattern(),
                        new StructureChoice(index, variants.stream()
                                .map(UltimateTerminalStructureVariants.Variant::label).toList()));
            }
        }
        return new PatternSelection(controller.getPattern(), StructureChoice.empty());
    }

    private static PatternSelection formedPattern(IMultiController controller) {
        if (controller instanceof UltimateTerminalStructureVariants provider) {
            List<UltimateTerminalStructureVariants.Variant> variants = provider.terminalStructureVariants();
            for (int i = 0; i < variants.size(); i++) {
                var variant = variants.get(i);
                if (variant.pattern().checkPatternAt(controller.getMultiblockState(), false)) {
                    return new PatternSelection(variant.pattern(), new StructureChoice(i,
                            variants.stream().map(UltimateTerminalStructureVariants.Variant::label).toList()));
                }
            }
            return null;
        }
        BlockPattern pattern = controller.getPattern();
        return pattern.checkPatternAt(controller.getMultiblockState(), false)
                ? new PatternSelection(pattern, StructureChoice.empty()) : null;
    }

    private static ModuleSelection selectedModule(MetaMachine machine, TerminalBuildProfile profile) {
        if (!GSEDifficultyConfig.externalModulesEnabled()
                || !(machine instanceof UltimateTerminalModuleProvider provider)) {
            return new ModuleSelection(ModuleChoice.empty(), null);
        }
        List<UltimateTerminalModuleProvider.Module> modules = provider.terminalModules().stream()
                .limit(UltimateTerminalConfig.MAX_CHANNEL_OPTIONS).toList();
        if (modules.isEmpty()) return new ModuleSelection(ModuleChoice.empty(), null);
        int configured = profile.channelSelection(UltimateTerminalModuleProvider.CHANNEL_ID);
        int selected = configured < 0 ? UltimateTerminalConfig.AUTO_CHANNEL_SELECTION
                : Math.min(configured, modules.size() - 1);
        ModuleChoice choice = new ModuleChoice(selected,
                modules.stream().map(UltimateTerminalModuleProvider.Module::translationKey).toList());
        return new ModuleSelection(choice, selected < 0 ? null : modules.get(selected));
    }

    private static String appendModule(ServerLevel level,
                                       UltimateTerminalModuleProvider.Module module,
                                       List<Placement> placements,
                                       List<Cell> cells) {
        if (module == null) return null;
        Set<BlockPos> occupied = new HashSet<>();
        cells.forEach(cell -> occupied.add(cell.pos()));
        String firstError = null;
        for (UltimateTerminalModuleProvider.Requirement requirement : module.requirements()) {
            BlockPos pos = requirement.pos();
            if (!occupied.add(pos)) continue;
            if (!level.hasChunkAt(pos)) {
                ItemStack expected = requirement.air() ? ItemStack.EMPTY
                        : requirement.block().asItem().getDefaultInstance();
                cells.add(new Cell(pos, expected.copyWithCount(1), CellStatus.UNLOADED));
                if (firstError == null) firstError = "chunk_unloaded@" + pos.toShortString();
                continue;
            }
            if (requirement.air()) {
                if (!level.getBlockState(pos).isAir()) {
                    cells.add(new Cell(pos, ItemStack.EMPTY, CellStatus.CONFLICT));
                    if (firstError == null) firstError = "module_air_obstructed@" + pos.toShortString();
                }
                continue;
            }
            ItemStack expected = requirement.block().asItem().getDefaultInstance();
            if (expected.isEmpty() || !(expected.getItem() instanceof BlockItem)) {
                if (firstError == null) firstError = "module_no_item@" + pos.toShortString();
                continue;
            }
            Block actual = level.getBlockState(pos).getBlock();
            if (actual == requirement.block()) {
                cells.add(new Cell(pos, expected.copyWithCount(1), CellStatus.SATISFIED));
                continue;
            }
            if (!level.isEmptyBlock(pos) && !level.getBlockState(pos).canBeReplaced()) {
                cells.add(new Cell(pos, expected.copyWithCount(1), CellStatus.CONFLICT));
                if (firstError == null) firstError = "module_collision@" + pos.toShortString();
                continue;
            }
            placements.add(new Placement(pos, expected.copyWithCount(1)));
            cells.add(new Cell(pos, expected.copyWithCount(1), CellStatus.MISSING));
        }
        return firstError;
    }

    private static Candidate matchingCandidate(ServerLevel level, BlockPos pos,
                                                TraceabilityPredicate predicate) {
        Block actual = level.getBlockState(pos).getBlock();
        for (SimplePredicate simple : combined(predicate)) {
            for (ItemStack stack : rawCandidates(simple)) {
                if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == actual) {
                    return new Candidate(stack, simple);
                }
            }
        }
        return null;
    }

    private static Candidate selectCandidate(TraceabilityPredicate predicate,
                                              Map<SimplePredicate, Integer> global,
                                              Map<SimplePredicate, Integer> layer,
                                              TerminalBuildProfile profile,
                                              Map<ResourceLocation, Integer> remainingRequests) {
        for (SimplePredicate simple : predicate.limited) {
            if (simple.minLayerCount > 0 && layer.getOrDefault(simple, 0) < simple.minLayerCount) {
                Candidate candidate = first(simple, profile);
                if (candidate != null) return candidate;
            }
        }
        for (SimplePredicate simple : predicate.limited) {
            if (simple.minCount > 0 && global.getOrDefault(simple, 0) < simple.minCount) {
                Candidate candidate = first(simple, profile);
                if (candidate != null) return candidate;
            }
        }
        Candidate requested = requestedCandidate(predicate, global, layer, profile, remainingRequests);
        if (requested != null) return requested;
        for (SimplePredicate simple : predicate.common) {
            Candidate candidate = first(simple, profile);
            if (candidate != null) return candidate;
        }
        for (SimplePredicate simple : predicate.limited) {
            int globalCount = global.getOrDefault(simple, 0);
            int layerCount = layer.getOrDefault(simple, 0);
            if ((simple.maxCount < 0 || globalCount < simple.maxCount)
                    && (simple.maxLayerCount < 0 || layerCount < simple.maxLayerCount)) {
                Candidate candidate = first(simple, profile);
                if (candidate != null) return candidate;
            }
        }
        return null;
    }

    private static Candidate requestedCandidate(TraceabilityPredicate predicate,
                                                 Map<SimplePredicate, Integer> global,
                                                 Map<SimplePredicate, Integer> layer,
                                                 TerminalBuildProfile profile,
                                                 Map<ResourceLocation, Integer> remainingRequests) {
        if (requiresUniformCandidate(predicate)) return null;
        for (ResourceLocation requestedId : remainingRequests.keySet()) {
            if (remainingRequests.getOrDefault(requestedId, 0) <= 0) continue;
            for (SimplePredicate simple : combined(predicate)) {
                if (!withinMaximum(simple, global, layer)) continue;
                for (ItemStack stack : candidates(simple, profile)) {
                    ResourceLocation blockId = blockId(stack);
                    if (requestedId.equals(blockId)) return new Candidate(stack, simple);
                }
            }
        }
        return null;
    }

    private static boolean withinMaximum(SimplePredicate simple, Map<SimplePredicate, Integer> global,
                                         Map<SimplePredicate, Integer> layer) {
        return (simple.maxCount < 0 || global.getOrDefault(simple, 0) < simple.maxCount)
                && (simple.maxLayerCount < 0 || layer.getOrDefault(simple, 0) < simple.maxLayerCount);
    }

    private static Candidate first(SimplePredicate predicate, TerminalBuildProfile profile) {
        List<ItemStack> candidates = candidates(predicate, profile);
        return candidates.isEmpty() ? null : new Candidate(candidates.get(0), predicate);
    }

    private static List<ItemStack> candidates(SimplePredicate predicate, TerminalBuildProfile profile) {
        List<ItemStack> stacks = new ArrayList<>();
        List<ItemStack> raw = rawCandidates(predicate).stream()
                .filter(stack -> !(((BlockItem) stack.getItem()).getBlock() instanceof MetaMachineBlock))
                .toList();
        ItemStack selectedCoil = preferredCoilStack(raw, profile);
        if (!selectedCoil.isEmpty()) return List.of(selectedCoil);
        ItemStack selectedChannel = preferredChannelStack(raw, profile);
        if (!selectedChannel.isEmpty()) return List.of(selectedChannel);
        ItemStack legacySelectedCoil = ItemStack.EMPTY;
        int selectedDistance = Integer.MAX_VALUE;
        for (ItemStack stack : raw) {
            Block block = ((BlockItem) stack.getItem()).getBlock();
            if (block instanceof CoilBlock coil && profile.coilTier() > 0) {
                int distance = Math.abs((coil.coilType.getTier() + 1) - profile.coilTier());
                if (distance < selectedDistance) {
                    selectedDistance = distance;
                    legacySelectedCoil = stack;
                }
            } else {
                stacks.add(stack);
            }
        }
        if (!legacySelectedCoil.isEmpty()) return List.of(legacySelectedCoil);
        return stacks;
    }

    private static List<ItemStack> rawCandidates(SimplePredicate predicate) {
        LinkedHashMap<ResourceLocation, ItemStack> stacks = new LinkedHashMap<>();
        // Use GTCEu's public candidate API instead of reading its supplier field directly. Besides
        // insulating the terminal from GTCEu internals, this lets addon predicates customize or
        // override candidate resolution and still participate in terminal planning.
        for (ItemStack stack : predicate.getCandidates()) {
            if (!(stack.getItem() instanceof BlockItem blockItem)) continue;
            Block block = blockItem.getBlock();
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
            if (block != Blocks.AIR && id != null && !stack.isEmpty()) {
                stacks.putIfAbsent(id, stack.copyWithCount(1));
            }
        }
        return List.copyOf(stacks.values());
    }

    private static boolean hasOnlyMachineCandidates(TraceabilityPredicate predicate) {
        boolean found = false;
        for (SimplePredicate simple : combined(predicate)) {
            for (ItemStack stack : rawCandidates(simple)) {
                found = true;
                if (!(((BlockItem) stack.getItem()).getBlock() instanceof MetaMachineBlock)) return false;
            }
        }
        return found;
    }

    private static Candidate closestCoil(TraceabilityPredicate predicate, int coilTier) {
        Candidate best = null;
        int distance = Integer.MAX_VALUE;
        for (SimplePredicate simple : combined(predicate)) {
            for (ItemStack stack : rawCandidates(simple)) {
                Block block = ((BlockItem) stack.getItem()).getBlock();
                if (block instanceof CoilBlock coil) {
                    int candidateDistance = Math.abs((coil.coilType.getTier() + 1) - coilTier);
                    if (candidateDistance < distance) {
                        distance = candidateDistance;
                        best = new Candidate(stack, simple);
                    }
                }
            }
        }
        return best;
    }

    private static Candidate selectedCoil(TraceabilityPredicate predicate, TerminalBuildProfile profile) {
        int selected = profile.channelSelection("coil");
        if (selected < 0) {
            return profile.coilTier() > 0 ? closestCoil(predicate, profile.coilTier()) : null;
        }
        List<Candidate> coils = new ArrayList<>();
        for (SimplePredicate simple : combined(predicate)) {
            for (ItemStack stack : rawCandidates(simple)) {
                if (((BlockItem) stack.getItem()).getBlock() instanceof CoilBlock) {
                    coils.add(new Candidate(stack, simple));
                }
            }
        }
        coils.sort(Comparator.comparingInt(candidate ->
                ((CoilBlock) ((BlockItem) candidate.stack().getItem()).getBlock()).coilType.getTier()));
        return coils.isEmpty() ? null : coils.get(Math.min(selected, coils.size() - 1));
    }

    private static ItemStack preferredCoilStack(List<ItemStack> candidates, TerminalBuildProfile profile) {
        int selected = profile.channelSelection("coil");
        if (selected < 0) return ItemStack.EMPTY;
        List<ItemStack> coils = candidates.stream()
                .filter(stack -> ((BlockItem) stack.getItem()).getBlock() instanceof CoilBlock)
                .sorted(Comparator.comparingInt(stack ->
                        ((CoilBlock) ((BlockItem) stack.getItem()).getBlock()).coilType.getTier()))
                .toList();
        return coils.isEmpty() ? ItemStack.EMPTY : coils.get(Math.min(selected, coils.size() - 1));
    }

    private static ChannelMatch closestChannel(TraceabilityPredicate predicate, TerminalBuildProfile profile) {
        for (UltimateTerminalConfig.SelectionChannel channel : UltimateTerminalConfig.selectionChannels()) {
            int selected = profile.channelSelection(channel.id());
            if (selected < 0) continue;
            List<ItemStack> options = resolvedOptions(channel);
            if (options.isEmpty()) continue;
            int requested = Math.min(selected, options.size() - 1);
            Candidate best = null;
            int distance = Integer.MAX_VALUE;
            for (SimplePredicate simple : combined(predicate)) {
                for (ItemStack stack : rawCandidates(simple)) {
                    int index = optionIndex(options, stack);
                    if (index >= 0 && Math.abs(index - requested) < distance) {
                        best = new Candidate(stack, simple);
                        distance = Math.abs(index - requested);
                    }
                }
            }
            if (best != null) return new ChannelMatch(channel.id(), best);
        }
        return null;
    }

    private static ItemStack preferredChannelStack(List<ItemStack> candidates, TerminalBuildProfile profile) {
        for (UltimateTerminalConfig.SelectionChannel channel : UltimateTerminalConfig.selectionChannels()) {
            int selected = profile.channelSelection(channel.id());
            if (selected < 0) continue;
            List<ItemStack> options = resolvedOptions(channel);
            if (options.isEmpty()) continue;
            int requested = Math.min(selected, options.size() - 1);
            ItemStack best = ItemStack.EMPTY;
            int distance = Integer.MAX_VALUE;
            for (ItemStack candidate : candidates) {
                int index = optionIndex(options, candidate);
                if (index >= 0 && Math.abs(index - requested) < distance) {
                    best = candidate;
                    distance = Math.abs(index - requested);
                }
            }
            if (!best.isEmpty()) return best;
        }
        return ItemStack.EMPTY;
    }

    private static boolean channelContains(String channelId, ItemStack stack) {
        if (channelId.equals("coil")) {
            return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CoilBlock;
        }
        UltimateTerminalConfig.SelectionChannel channel = UltimateTerminalConfig.channel(channelId);
        return channel != null && optionIndex(resolvedOptions(channel), stack) >= 0;
    }

    private static List<ChannelChoice> channelChoices(TerminalBuildProfile profile,
                                                       Map<ResourceLocation, CandidateAccumulator> stats) {
        List<ChannelChoice> choices = new ArrayList<>();
        List<ItemStack> coils = stats.values().stream().map(value -> value.stack)
                .filter(stack -> stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CoilBlock)
                .sorted(Comparator.comparingInt(stack ->
                        ((CoilBlock) ((BlockItem) stack.getItem()).getBlock()).coilType.getTier()))
                .map(stack -> stack.copyWithCount(1)).toList();
        if (coils.size() >= 2) {
            int selected = profile.channelSelection("coil");
            if (selected < 0 && profile.coilTier() > 0) {
                int bestDistance = Integer.MAX_VALUE;
                for (int i = 0; i < coils.size(); i++) {
                    int tier = ((CoilBlock) ((BlockItem) coils.get(i).getItem()).getBlock()).coilType.getTier() + 1;
                    int distance = Math.abs(tier - profile.coilTier());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        selected = i;
                    }
                }
            }
            choices.add(new ChannelChoice("coil", Math.min(selected, coils.size() - 1), coils));
        }
        for (UltimateTerminalConfig.SelectionChannel channel : UltimateTerminalConfig.selectionChannels()) {
            List<ItemStack> options = resolvedOptions(channel);
            long applicable = options.stream().map(UltimateStructurePlanner::blockId)
                    .filter(java.util.Objects::nonNull).filter(stats::containsKey).count();
            if (applicable < 2) continue;
            choices.add(new ChannelChoice(channel.id(),
                    Math.min(profile.channelSelection(channel.id()), options.size() - 1), options));
        }
        return List.copyOf(choices);
    }

    private static List<ItemStack> resolvedOptions(UltimateTerminalConfig.SelectionChannel channel) {
        List<ItemStack> options = new ArrayList<>();
        for (ResourceLocation id : channel.blocks()) {
            Block block = ForgeRegistries.BLOCKS.getValue(id);
            if (block == null || block instanceof MetaMachineBlock) continue;
            ItemStack stack = block.asItem().getDefaultInstance();
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) options.add(stack.copyWithCount(1));
        }
        return List.copyOf(options);
    }

    private static int optionIndex(List<ItemStack> options, ItemStack stack) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).getItem() == stack.getItem()) return i;
        }
        return -1;
    }

    private static void collectCandidateStats(TraceabilityPredicate predicate,
                                              Map<ResourceLocation, CandidateAccumulator> stats) {
        Set<ResourceLocation> cellCandidates = new HashSet<>();
        Map<ResourceLocation, ItemStack> stacks = new HashMap<>();
        for (SimplePredicate simple : combined(predicate)) {
            for (ItemStack stack : rawCandidates(simple)) {
                if (((BlockItem) stack.getItem()).getBlock() instanceof MetaMachineBlock) continue;
                ResourceLocation id = blockId(stack);
                if (id != null) {
                    cellCandidates.add(id);
                    stacks.putIfAbsent(id, stack);
                }
            }
        }
        boolean configurable = cellCandidates.size() > 1 && !requiresUniformCandidate(predicate);
        for (ResourceLocation id : cellCandidates) {
            CandidateAccumulator accumulator = stats.computeIfAbsent(id,
                    ignored -> new CandidateAccumulator(stacks.get(id)));
            accumulator.maximum++;
            accumulator.configurable |= configurable;
        }
    }

    private static boolean requiresUniformCandidate(TraceabilityPredicate predicate) {
        for (SimplePredicate simple : combined(predicate)) {
            if (simple.toolTips == null) continue;
            for (var tooltip : simple.toolTips) {
                if (tooltip.getContents() instanceof TranslatableContents translated
                        && UNIFORM_CANDIDATE_ERRORS.contains(translated.getKey())) return true;
            }
        }
        return false;
    }

    private static void markPresent(Map<ResourceLocation, CandidateAccumulator> stats, ItemStack stack) {
        ResourceLocation id = blockId(stack);
        CandidateAccumulator accumulator = id == null ? null : stats.get(id);
        if (accumulator != null) accumulator.present++;
    }

    private static void consumeRequest(Map<ResourceLocation, Integer> remaining, ItemStack stack) {
        ResourceLocation id = blockId(stack);
        if (id != null && remaining.getOrDefault(id, 0) > 0) remaining.computeIfPresent(id, (key, value) -> value - 1);
    }

    private static ResourceLocation blockId(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return null;
        return ForgeRegistries.BLOCKS.getKey(blockItem.getBlock());
    }

    private static List<SimplePredicate> combined(TraceabilityPredicate predicate) {
        List<SimplePredicate> result = new ArrayList<>(predicate.limited);
        result.addAll(predicate.common);
        return result;
    }

    private static void increment(Map<SimplePredicate, Integer> map, SimplePredicate predicate) {
        map.merge(predicate, 1, Integer::sum);
    }

    private record Candidate(ItemStack stack, SimplePredicate predicate) {}

    private record ChannelMatch(String id, Candidate candidate) {}

    private record PatternSelection(BlockPattern pattern, StructureChoice choice) {}

    private record ModuleSelection(ModuleChoice choice,
                                   UltimateTerminalModuleProvider.Module selected) {}

    private static final class CandidateAccumulator {
        final ItemStack stack;
        int present;
        int maximum;
        boolean configurable;

        CandidateAccumulator(ItemStack stack) {
            this.stack = stack.copyWithCount(1);
        }
    }

    private static BlockPos relativeOffset(int x, int y, int z, Direction facing, Direction upwardsFacing,
                                           boolean flipped, RelativeDirection[] dirs) {
        int[] source = { x, y, z };
        int[] result = new int[3];
        if (facing == Direction.UP || facing == Direction.DOWN) {
            Direction relativeFacing = facing == Direction.DOWN ? upwardsFacing : upwardsFacing.getOpposite();
            mapAxes(source, result, dirs, relativeFacing);
            int xOffset = upwardsFacing.getStepX();
            int zOffset = upwardsFacing.getStepZ();
            int tmp;
            if (xOffset == 0) {
                tmp = result[2];
                result[2] = zOffset > 0 ? result[1] : -result[1];
                result[1] = zOffset > 0 ? -tmp : tmp;
            } else {
                tmp = result[0];
                result[0] = xOffset > 0 ? result[1] : -result[1];
                result[1] = xOffset > 0 ? -tmp : tmp;
            }
            if (flipped) {
                if (upwardsFacing == Direction.NORTH || upwardsFacing == Direction.SOUTH) result[0] = -result[0];
                else result[2] = -result[2];
            }
        } else {
            mapAxes(source, result, dirs, facing);
            if (upwardsFacing == Direction.WEST || upwardsFacing == Direction.EAST) {
                int xOffset = upwardsFacing == Direction.EAST ? facing.getClockWise().getStepX()
                        : facing.getClockWise().getOpposite().getStepX();
                int zOffset = upwardsFacing == Direction.EAST ? facing.getClockWise().getStepZ()
                        : facing.getClockWise().getOpposite().getStepZ();
                int tmp;
                if (xOffset == 0) {
                    tmp = result[2];
                    result[2] = zOffset > 0 ? -result[1] : result[1];
                    result[1] = zOffset > 0 ? tmp : -tmp;
                } else {
                    tmp = result[0];
                    result[0] = xOffset > 0 ? -result[1] : result[1];
                    result[1] = xOffset > 0 ? tmp : -tmp;
                }
            } else if (upwardsFacing == Direction.SOUTH) {
                result[1] = -result[1];
                if (facing.getStepX() == 0) result[0] = -result[0];
                else result[2] = -result[2];
            }
            if (flipped) {
                if (upwardsFacing == Direction.NORTH || upwardsFacing == Direction.SOUTH) {
                    if (facing == Direction.NORTH || facing == Direction.SOUTH) result[0] = -result[0];
                    else result[2] = -result[2];
                } else result[1] = -result[1];
            }
        }
        return new BlockPos(result[0], result[1], result[2]);
    }

    private static void mapAxes(int[] source, int[] result, RelativeDirection[] dirs, Direction facing) {
        for (int i = 0; i < 3; i++) {
            switch (dirs[i].getActualDirection(facing)) {
                case UP -> result[1] = source[i];
                case DOWN -> result[1] = -source[i];
                case WEST -> result[0] = -source[i];
                case EAST -> result[0] = source[i];
                case NORTH -> result[2] = -source[i];
                case SOUTH -> result[2] = source[i];
            }
        }
    }
}
