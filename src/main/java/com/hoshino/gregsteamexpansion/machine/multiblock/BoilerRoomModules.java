package com.hoshino.gregsteamexpansion.machine.multiblock;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.SteamHatchPartMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.SteamFluidHatchPartMachine;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalModuleProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Boiler-room module catalogue, geometry and dedicated-port validation. */
public final class BoilerRoomModules {

    public static final String SOFTENER = "BR-L-01";
    public static final String FORCE_COOLING = "BR-R-02";
    public static final String ACID_RECOVERY = "BR-D-05";
    public static final String FORCED_DRAFT = "BR-B-07";
    public static final String ATOMIZER = "BR-L-09";
    public static final String HOT_ACID = "BR-D-11";
    public static final String CONDENSER = "BR-B-12";
    public static final String STEAM_BUFFER = "BR-R-15";
    public static final String AUTO_ACID = "BR-T-16";

    public enum Face {
        LEFT("left"), RIGHT("right"), BACK("back"), TOP("top"), DOWN("down");

        private final String id;

        Face(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public enum Port {
        NONE, ITEM_IN, FLUID_IN, FLUID_OUT
    }

    public enum Theme {
        CASING, GLASS, FIREBOX, RAISED
    }

    public enum Status {
        MISSING("missing"), INVALID("invalid"), UNLOADED("unloaded"), CONFLICT("conflict"), VALID("ready");

        private final String key;

        Status(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    /** Coordinates use controller-relative right/back/up axes and include the complete exclusive box. */
    public record Descriptor(String id, Face face, int minSide, int maxSide, int minBack, int maxBack,
                             int minUp, int maxUp, Port port, Theme theme, String translationKey) {

        public BlockPos min(BlockPos controller, Direction front) {
            return bounds(controller, front)[0];
        }

        public BlockPos max(BlockPos controller, Direction front) {
            return bounds(controller, front)[1];
        }

        public BlockPos anchor(BlockPos controller, Direction front) {
            return portPos(controller, front);
        }

        public BlockPos portPos(BlockPos controller, Direction front) {
            int side = (minSide + maxSide) / 2;
            int back = (minBack + maxBack) / 2;
            int up = (minUp + maxUp) / 2;
            switch (face) {
                case LEFT -> side = minSide;
                case RIGHT -> side = maxSide;
                case BACK -> back = maxBack;
                case TOP -> up = maxUp;
                case DOWN -> up = minUp;
            }
            return local(controller, front, side, back, up);
        }

        public Direction outward(Direction front) {
            return switch (face) {
                case LEFT -> front.getCounterClockWise();
                case RIGHT -> front.getClockWise();
                case BACK -> front.getOpposite();
                case TOP -> Direction.UP;
                case DOWN -> Direction.DOWN;
            };
        }

        public BlockPos[] bounds(BlockPos controller, Direction front) {
            BlockPos a = local(controller, front, minSide, minBack, minUp);
            BlockPos b = local(controller, front, maxSide, maxBack, maxUp);
            return new BlockPos[] {
                    new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                            Math.min(a.getZ(), b.getZ())),
                    new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()),
                            Math.max(a.getZ(), b.getZ()))
            };
        }
    }

    public record Validation(Status status, @Nullable IMultiPart portPart, int presentBlocks) {}

    private enum CellKind { BLOCK, AIR, PORT }

    private record Cell(BlockPos pos, CellKind kind, @Nullable Block block) {}

    public static final Descriptor WATER_SOFTENER = descriptor(SOFTENER, Face.LEFT,
            -6, -4, 2, 8, -3, -1, Port.ITEM_IN, Theme.GLASS, "water_softener");
    public static final Descriptor COOLING_TANK = descriptor(FORCE_COOLING, Face.RIGHT,
            4, 7, 2, 8, -3, 1, Port.FLUID_IN, Theme.GLASS, "force_cooling");
    public static final Descriptor RECOVERY_POOL = descriptor(ACID_RECOVERY, Face.DOWN,
            -2, 2, 2, 8, -5, -4, Port.FLUID_OUT, Theme.GLASS, "acid_recovery");
    public static final Descriptor DRAFT_ROOM = descriptor(FORCED_DRAFT, Face.BACK,
            -3, 3, 11, 13, -3, -1, Port.NONE, Theme.CASING, "forced_draft");
    public static final Descriptor ATOMIZATION_ROOM = descriptor(ATOMIZER, Face.LEFT,
            -8, -4, 3, 7, -3, 1, Port.NONE, Theme.FIREBOX, "atomizer");
    public static final Descriptor HOT_ACID_FACILITY = descriptor(HOT_ACID, Face.DOWN,
            -2, 2, 3, 7, -6, -4, Port.FLUID_IN, Theme.FIREBOX, "hot_acid");
    public static final Descriptor CONDENSER_TOWER = descriptor(CONDENSER, Face.BACK,
            1, 3, 11, 13, -3, 3, Port.FLUID_OUT, Theme.GLASS, "condenser");
    public static final Descriptor BUFFER_TANK = descriptor(STEAM_BUFFER, Face.RIGHT,
            4, 7, 2, 8, -3, 1, Port.NONE, Theme.GLASS, "steam_buffer");
    public static final Descriptor AUTO_WASH_STATION = descriptor(AUTO_ACID, Face.TOP,
            -2, 2, 3, 7, 4, 7, Port.FLUID_IN, Theme.RAISED, "auto_acid");

    /** Stable order. The retired BR-R-06 deliberately does not appear here. */
    public static final List<Descriptor> ALL = List.of(
            WATER_SOFTENER, COOLING_TANK, RECOVERY_POOL, DRAFT_ROOM, ATOMIZATION_ROOM,
            HOT_ACID_FACILITY, CONDENSER_TOWER, BUFFER_TANK, AUTO_WASH_STATION);

    private BoilerRoomModules() {}

    private static Descriptor descriptor(String id, Face face, int minSide, int maxSide,
                                         int minBack, int maxBack, int minUp, int maxUp,
                                         Port port, Theme theme, String suffix) {
        return new Descriptor(id, face, minSide, maxSide, minBack, maxBack, minUp, maxUp, port, theme,
                "gregsteamexpansion.machine.boiler_room.module." + suffix);
    }

    public static Validation validate(ServerLevel level, BlockPos controller, Direction front,
                                      int tierIndex, Descriptor descriptor) {
        List<Cell> cells = cells(controller, front, tierIndex, descriptor);
        for (Cell cell : cells) {
            if (!level.hasChunkAt(cell.pos())) return new Validation(Status.UNLOADED, null, 0);
        }
        int present = 0;
        boolean valid = true;
        IMultiPart port = null;
        for (Cell cell : cells) {
            if (!level.isEmptyBlock(cell.pos())) present++;
            if (cell.kind() == CellKind.AIR) {
                valid &= level.getBlockState(cell.pos()).isAir();
            } else if (cell.kind() == CellKind.BLOCK) {
                valid &= level.getBlockState(cell.pos()).getBlock() == cell.block();
            } else {
                port = validatePort(level, cell.pos(), descriptor.port(), descriptor.outward(front));
                valid &= port != null;
            }
        }
        return new Validation(valid ? Status.VALID : present == 0 ? Status.MISSING : Status.INVALID, port, present);
    }

    @Nullable
    private static IMultiPart validatePort(ServerLevel level, BlockPos pos, Port required, Direction outward) {
        MetaMachine machine = MetaMachine.getMachine(level, pos);
        if (!(machine instanceof IMultiPart part) || part.isFormed()
                || machine instanceof SteamFluidHatchPartMachine || machine instanceof SteamHatchPartMachine
                || machine.getFrontFacing() != outward) {
            return null;
        }
        return hasHandler(part, required) ? part : null;
    }

    private static boolean hasHandler(IMultiPart part, Port required) {
        if (required == Port.NONE) return true;
        for (RecipeHandlerList list : part.getRecipeHandlers()) {
            IO io = required == Port.FLUID_OUT ? IO.OUT : IO.IN;
            if (!list.isValid(io)) continue;
            if (required == Port.ITEM_IN
                    && !list.getHandlerMap().getOrDefault(ItemRecipeCapability.CAP, List.of()).isEmpty()) return true;
            if ((required == Port.FLUID_IN || required == Port.FLUID_OUT)
                    && !list.getHandlerMap().getOrDefault(FluidRecipeCapability.CAP, List.of()).isEmpty()) return true;
        }
        return false;
    }

    public static BoilerRoomModuleWorldData.Claim claim(BlockPos controller, Direction front,
                                                        Descriptor descriptor) {
        BlockPos[] bounds = descriptor.bounds(controller, front);
        return new BoilerRoomModuleWorldData.Claim(controller.immutable(), descriptor.id(), descriptor.face().id(),
                descriptor.anchor(controller, front), bounds[0], bounds[1]);
    }

    public static BoilerRoomModuleWorldData.Claim bodyClaim(BlockPos controller, Direction front) {
        BlockPos a = local(controller, front, -3, 0, -3);
        BlockPos b = local(controller, front, 3, 10, 3);
        return new BoilerRoomModuleWorldData.Claim(controller.immutable(), "__body", "body", controller,
                new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())));
    }

    public static List<UltimateTerminalModuleProvider.Requirement> terminalRequirements(
            BlockPos controller, Direction front, int tierIndex, Descriptor descriptor) {
        List<UltimateTerminalModuleProvider.Requirement> requirements = new ArrayList<>();
        for (Cell cell : cells(controller, front, tierIndex, descriptor)) {
            if (cell.kind() == CellKind.AIR) {
                requirements.add(UltimateTerminalModuleProvider.Requirement.air(cell.pos()));
            } else {
                Block block = cell.kind() == CellKind.PORT ? defaultPort(descriptor.port()) : cell.block();
                requirements.add(UltimateTerminalModuleProvider.Requirement.solid(cell.pos(), block));
            }
        }
        return requirements;
    }

    public static int countPresent(ServerLevel level, BlockPos controller, Direction front,
                                   int tierIndex, Descriptor descriptor) {
        int count = 0;
        for (Cell cell : cells(controller, front, tierIndex, descriptor)) {
            if (level.hasChunkAt(cell.pos()) && !level.isEmptyBlock(cell.pos())) count++;
        }
        return count;
    }

    /** Test helper; functional ports are placed at LV and rotated to the required outward face. */
    public static void place(ServerLevel level, BlockPos controller, Direction front,
                             int tierIndex, Descriptor descriptor) {
        for (Cell cell : cells(controller, front, tierIndex, descriptor)) {
            if (cell.kind() == CellKind.AIR) {
                level.setBlockAndUpdate(cell.pos(), Blocks.AIR.defaultBlockState());
            } else {
                Block block = cell.kind() == CellKind.PORT ? defaultPort(descriptor.port()) : cell.block();
                level.setBlockAndUpdate(cell.pos(), block.defaultBlockState());
                if (cell.kind() == CellKind.PORT) {
                    MetaMachine machine = MetaMachine.getMachine(level, cell.pos());
                    if (machine != null) machine.setFrontFacing(descriptor.outward(front));
                }
            }
        }
    }

    private static List<Cell> cells(BlockPos controller, Direction front, int tierIndex, Descriptor descriptor) {
        if (descriptor == WATER_SOFTENER) return softenerCells(controller, front, tierIndex);
        List<Cell> result = new ArrayList<>();
        Block casing = casing(tierIndex);
        Block pipe = pipe(tierIndex);
        Block firebox = firebox(tierIndex);
        BlockPos port = descriptor.port() == Port.NONE ? null : descriptor.portPos(controller, front);
        int centerSide = (descriptor.minSide() + descriptor.maxSide()) / 2;
        int centerUp = (descriptor.minUp() + descriptor.maxUp()) / 2;
        for (int side = descriptor.minSide(); side <= descriptor.maxSide(); side++) {
            for (int back = descriptor.minBack(); back <= descriptor.maxBack(); back++) {
                for (int up = descriptor.minUp(); up <= descriptor.maxUp(); up++) {
                    BlockPos pos = local(controller, front, side, back, up);
                    if (pos.equals(port)) {
                        result.add(new Cell(pos, CellKind.PORT, null));
                        continue;
                    }
                    boolean boundary = side == descriptor.minSide() || side == descriptor.maxSide()
                            || back == descriptor.minBack() || back == descriptor.maxBack()
                            || up == descriptor.minUp() || up == descriptor.maxUp();
                    if (descriptor.theme() == Theme.RAISED && up == descriptor.minUp()) {
                        boolean support = (side == descriptor.minSide() || side == descriptor.maxSide())
                                && (back == descriptor.minBack() || back == descriptor.maxBack());
                        result.add(new Cell(pos, support ? CellKind.BLOCK : CellKind.AIR,
                                support ? casing : null));
                    } else if (side == centerSide && up == centerUp) {
                        result.add(new Cell(pos, CellKind.BLOCK, pipe));
                    } else if (descriptor.theme() == Theme.FIREBOX && up == descriptor.minUp()
                            && side > descriptor.minSide() && side < descriptor.maxSide()
                            && back > descriptor.minBack() && back < descriptor.maxBack()) {
                        result.add(new Cell(pos, CellKind.BLOCK, firebox));
                    } else if (boundary) {
                        boolean glass = descriptor.theme() == Theme.GLASS
                                && up > descriptor.minUp() && up < descriptor.maxUp()
                                && back > descriptor.minBack() && back < descriptor.maxBack();
                        result.add(new Cell(pos, CellKind.BLOCK, glass ? Blocks.GLASS : casing));
                    } else {
                        result.add(new Cell(pos, CellKind.AIR, null));
                    }
                }
            }
        }
        return result;
    }

    private static List<Cell> softenerCells(BlockPos controller, Direction front, int tierIndex) {
        List<Cell> result = new ArrayList<>(63);
        Block casing = casing(tierIndex);
        Block pipe = pipe(tierIndex);
        BlockPos port = WATER_SOFTENER.portPos(controller, front);
        for (int side = -6; side <= -4; side++) {
            for (int back = 2; back <= 8; back++) {
                result.add(new Cell(local(controller, front, side, back, -3), CellKind.BLOCK, casing));
                result.add(new Cell(local(controller, front, side, back, -1), CellKind.BLOCK, casing));
            }
        }
        for (int back = 2; back <= 8; back++) {
            BlockPos outer = local(controller, front, -6, back, -2);
            if (outer.equals(port)) result.add(new Cell(outer, CellKind.PORT, null));
            else result.add(new Cell(outer, CellKind.BLOCK,
                    back == 2 || back == 8 ? casing : Blocks.GLASS));
            result.add(new Cell(local(controller, front, -5, back, -2), CellKind.BLOCK,
                    back == 2 || back == 8 ? casing : pipe));
            result.add(new Cell(local(controller, front, -4, back, -2), CellKind.BLOCK,
                    back == 5 ? pipe : casing));
        }
        return result;
    }

    private static Block defaultPort(Port port) {
        return switch (port) {
            case ITEM_IN -> GTMachines.ITEM_IMPORT_BUS[GTValues.LV].getBlock();
            case FLUID_IN -> GTMachines.FLUID_IMPORT_HATCH[GTValues.LV].getBlock();
            case FLUID_OUT -> GTMachines.FLUID_EXPORT_HATCH[GTValues.LV].getBlock();
            case NONE -> Blocks.AIR;
        };
    }

    public static Block casing(int tierIndex) {
        return switch (tierIndex) {
            case BoilerRoomMachine.BRONZE_TIER -> GTBlocks.CASING_BRONZE_BRICKS.get();
            case BoilerRoomMachine.STEEL_TIER -> GTBlocks.CASING_STEEL_SOLID.get();
            case BoilerRoomMachine.TITANIUM_TIER -> GTBlocks.CASING_TITANIUM_STABLE.get();
            case BoilerRoomMachine.TUNGSTENSTEEL_TIER -> GTBlocks.CASING_TUNGSTENSTEEL_ROBUST.get();
            default -> throw new IllegalArgumentException("Unknown boiler-room tier " + tierIndex);
        };
    }

    public static Block pipe(int tierIndex) {
        return switch (tierIndex) {
            case BoilerRoomMachine.BRONZE_TIER -> GTBlocks.CASING_BRONZE_PIPE.get();
            case BoilerRoomMachine.STEEL_TIER -> GTBlocks.CASING_STEEL_PIPE.get();
            case BoilerRoomMachine.TITANIUM_TIER -> GTBlocks.CASING_TITANIUM_PIPE.get();
            case BoilerRoomMachine.TUNGSTENSTEEL_TIER -> GTBlocks.CASING_TUNGSTENSTEEL_PIPE.get();
            default -> throw new IllegalArgumentException("Unknown boiler-room tier " + tierIndex);
        };
    }

    public static Block firebox(int tierIndex) {
        return switch (tierIndex) {
            case BoilerRoomMachine.BRONZE_TIER -> GTBlocks.FIREBOX_BRONZE.get();
            case BoilerRoomMachine.STEEL_TIER -> GTBlocks.FIREBOX_STEEL.get();
            case BoilerRoomMachine.TITANIUM_TIER -> GTBlocks.FIREBOX_TITANIUM.get();
            case BoilerRoomMachine.TUNGSTENSTEEL_TIER -> GTBlocks.FIREBOX_TUNGSTENSTEEL.get();
            default -> throw new IllegalArgumentException("Unknown boiler-room tier " + tierIndex);
        };
    }

    public static BlockPos local(BlockPos controller, Direction front, int side, int back, int up) {
        return controller.relative(front.getClockWise(), side).relative(front.getOpposite(), back).above(up);
    }
}
