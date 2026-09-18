package com.hoshino.gregsteamexpansion.terminal;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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

    public record Plan(List<Placement> placements, List<Cell> cells,
                       List<CandidateChoice> candidates, String error) {
        public boolean valid() {
            return error == null;
        }

        static Plan failed(String error) {
            return new Plan(List.of(), List.of(), List.of(), error);
        }
    }

    public static Plan plan(ServerLevel level, BlockPos controllerPos, int requestedRepeats,
                            int requestedCoilTier, boolean buildHatches, boolean upgrade) {
        return plan(level, controllerPos,
                new TerminalBuildProfile(requestedRepeats, requestedCoilTier, buildHatches), upgrade);
    }

    public static Plan plan(ServerLevel level, BlockPos controllerPos,
                            TerminalBuildProfile profile, boolean upgrade) {
        MetaMachine machine = MetaMachine.getMachine(level, controllerPos);
        if (!(machine instanceof IMultiController controller)) {
            return Plan.failed("not_controller");
        }
        try {
            BlockPattern pattern = controller.getPattern();
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
                int repetitions = min == max ? min : Math.max(min,
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
                            Candidate desiredCoil = profile.coilTier() > 0
                                    ? closestCoil(predicate, profile.coilTier()) : null;
                            boolean replaceCoil = existing != null && desiredCoil != null && upgrade
                                    && existing.stack().getItem() != desiredCoil.stack().getItem();
                            if (existing != null && !replaceCoil) {
                                increment(global, existing.predicate);
                                increment(layer, existing.predicate);
                                consumeRequest(remainingRequests, existing.stack());
                                markPresent(candidateStats, existing.stack());
                                cells.add(new Cell(pos.immutable(), existing.stack().copyWithCount(1),
                                        CellStatus.SATISFIED));
                                continue;
                            }
                            Candidate selected = replaceCoil ? desiredCoil : selectCandidate(predicate, global, layer,
                                    profile.coilTier(), profile.buildHatches(), remainingRequests);
                            if (selected == null || selected.stack.isEmpty()
                                    || !(selected.stack.getItem() instanceof BlockItem)) {
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
            return new Plan(List.copyOf(placements), List.copyOf(cells), candidates, firstError);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return Plan.failed("pattern_error:" + exception.getClass().getSimpleName());
        }
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
                                             int coilTier, boolean buildHatches,
                                             Map<ResourceLocation, Integer> remainingRequests) {
        for (SimplePredicate simple : predicate.limited) {
            if (simple.minLayerCount > 0 && layer.getOrDefault(simple, 0) < simple.minLayerCount) {
                Candidate candidate = first(simple, coilTier, buildHatches);
                if (candidate != null) return candidate;
            }
        }
        for (SimplePredicate simple : predicate.limited) {
            if (simple.minCount > 0 && global.getOrDefault(simple, 0) < simple.minCount) {
                Candidate candidate = first(simple, coilTier, buildHatches);
                if (candidate != null) return candidate;
            }
        }
        Candidate requested = requestedCandidate(predicate, global, layer, coilTier,
                buildHatches, remainingRequests);
        if (requested != null) return requested;
        for (SimplePredicate simple : predicate.common) {
            Candidate candidate = first(simple, coilTier, buildHatches);
            if (candidate != null) return candidate;
        }
        for (SimplePredicate simple : predicate.limited) {
            int globalCount = global.getOrDefault(simple, 0);
            int layerCount = layer.getOrDefault(simple, 0);
            if ((simple.maxCount < 0 || globalCount < simple.maxCount)
                    && (simple.maxLayerCount < 0 || layerCount < simple.maxLayerCount)) {
                Candidate candidate = first(simple, coilTier, buildHatches);
                if (candidate != null) return candidate;
            }
        }
        return null;
    }

    private static Candidate requestedCandidate(TraceabilityPredicate predicate,
                                                Map<SimplePredicate, Integer> global,
                                                Map<SimplePredicate, Integer> layer,
                                                int coilTier, boolean buildHatches,
                                                Map<ResourceLocation, Integer> remainingRequests) {
        for (ResourceLocation requestedId : remainingRequests.keySet()) {
            if (remainingRequests.getOrDefault(requestedId, 0) <= 0) continue;
            for (SimplePredicate simple : combined(predicate)) {
                if (!withinMaximum(simple, global, layer)) continue;
                for (ItemStack stack : candidates(simple, coilTier, buildHatches)) {
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

    private static Candidate first(SimplePredicate predicate, int coilTier, boolean buildHatches) {
        List<ItemStack> candidates = candidates(predicate, coilTier, buildHatches);
        return candidates.isEmpty() ? null : new Candidate(candidates.get(0), predicate);
    }

    private static List<ItemStack> candidates(SimplePredicate predicate, int coilTier, boolean buildHatches) {
        List<ItemStack> stacks = new ArrayList<>();
        ItemStack selectedCoil = ItemStack.EMPTY;
        int selectedDistance = Integer.MAX_VALUE;
        for (ItemStack stack : rawCandidates(predicate)) {
            Block block = ((BlockItem) stack.getItem()).getBlock();
            if (!buildHatches && block instanceof MetaMachineBlock) continue;
            if (block instanceof CoilBlock coil && coilTier > 0) {
                int distance = Math.abs((coil.coilType.getTier() + 1) - coilTier);
                if (distance < selectedDistance) {
                    selectedDistance = distance;
                    selectedCoil = stack;
                }
            } else {
                stacks.add(stack);
            }
        }
        if (!selectedCoil.isEmpty()) return List.of(selectedCoil);
        return stacks;
    }

    private static List<ItemStack> rawCandidates(SimplePredicate predicate) {
        if (predicate.candidates == null) return List.of();
        BlockInfo[] infos = predicate.candidates.get();
        if (infos == null) return List.of();
        LinkedHashMap<ResourceLocation, ItemStack> stacks = new LinkedHashMap<>();
        for (BlockInfo info : infos) {
            Block block = info.getBlockState().getBlock();
            ItemStack stack = info.getItemStackForm();
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
            if (block != Blocks.AIR && id != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                stacks.putIfAbsent(id, stack.copyWithCount(1));
            }
        }
        return List.copyOf(stacks.values());
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

    private static void collectCandidateStats(TraceabilityPredicate predicate,
                                              Map<ResourceLocation, CandidateAccumulator> stats) {
        Set<ResourceLocation> cellCandidates = new HashSet<>();
        Map<ResourceLocation, ItemStack> stacks = new HashMap<>();
        for (SimplePredicate simple : combined(predicate)) {
            for (ItemStack stack : rawCandidates(simple)) {
                ResourceLocation id = blockId(stack);
                if (id != null) {
                    cellCandidates.add(id);
                    stacks.putIfAbsent(id, stack);
                }
            }
        }
        boolean configurable = cellCandidates.size() > 1;
        for (ResourceLocation id : cellCandidates) {
            CandidateAccumulator accumulator = stats.computeIfAbsent(id,
                    ignored -> new CandidateAccumulator(stacks.get(id)));
            accumulator.maximum++;
            accumulator.configurable |= configurable;
        }
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
