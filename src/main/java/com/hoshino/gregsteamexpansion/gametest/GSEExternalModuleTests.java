package com.hoshino.gregsteamexpansion.gametest;

import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.cokeoven.CokeOvenModuleWorldData;
import com.hoshino.gregsteamexpansion.machine.module.ExternalModuleWorldData;
import com.hoshino.gregsteamexpansion.machine.module.ExternalModuleWorldData.Claim;
import com.hoshino.gregsteamexpansion.machine.module.ModuleStructure;
import com.hoshino.gregsteamexpansion.machine.module.ModuleStructure.Requirement;
import com.hoshino.gregsteamexpansion.machine.module.ModuleStructure.Result;
import com.hoshino.gregsteamexpansion.machine.multiblock.BoilerRoomModuleWorldData;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.BlastFurnaceHotBlastModule;
import com.hoshino.gregsteamexpansion.machine.multiblock.processor.BlastFurnaceHotBlastWorldData;
import com.hoshino.gregsteamexpansion.registry.GSEMachines;
import com.hoshino.gregsteamexpansion.terminal.UltimateTerminalModuleProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

@GameTestHolder(GregSteamExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GSEExternalModuleTests {

    private static final List<Function<CompoundTag, ExternalModuleWorldData>> LOADERS = List.of(
            BoilerRoomModuleWorldData::load, BlastFurnaceHotBlastWorldData::load, CokeOvenModuleWorldData::load);

    private GSEExternalModuleTests() {}

    @GameTest(template = "empty_32x32x32", timeoutTicks = 100)
    public static void moduleClaimsPreserveFamilyConflictPolicies(GameTestHelper helper) {
        BlockPos controller = helper.absolutePos(new BlockPos(3, 3, 3));
        BlockPos min = controller.east(4);
        BlockPos max = min.offset(2, 2, 2);
        Claim first = new Claim(controller, "test-right", "right", min, min, max);
        BlockPos foreignController = controller.west(10);
        for (var loader : LOADERS) {
            ExternalModuleWorldData data = loader.apply(new CompoundTag());
            helper.assertTrue(data.claim(first).success(), "Initial module claim failed");
            data.setDirty(false);
            helper.assertTrue(data.claim(first).success() && !data.isDirty(),
                    "Unchanged revalidation dirtied persistent ownership");
            var faceConflict = data.claim(new Claim(controller, "test-other-right", "right",
                    min.east(10), min.east(10), max.east(10)));
            helper.assertTrue(!faceConflict.success() && controller.equals(faceConflict.conflictingController())
                            && !data.isDirty(),
                    "Same-face rejection lost its owner or mutated ownership");
            helper.assertTrue(!data.claim(new Claim(foreignController, "test-foreign", "left", min, min, max))
                            .success(),
                    "Foreign module boxes were allowed to overlap");
            boolean cokeOven = data instanceof CokeOvenModuleWorldData;
            Claim adjacent = new Claim(foreignController, "test-adjacent", "left",
                    max.east(), max.east(), max.east());
            helper.assertTrue(data.claim(adjacent).success() == !cokeOven,
                    "Machine-specific spacing policy changed");
            data.releaseAll(foreignController);
            Claim separated = new Claim(foreignController, "test-separated", "left",
                    max.east(2), max.east(2), max.east(2));
            helper.assertTrue(data.claim(separated).success(), "A one-block gap was rejected");
            helper.assertTrue(data.claim(new Claim(controller, "test-top", "top", min, min, max))
                            .success() == cokeOven,
                    "Same-controller overlap policy changed");
            if (data instanceof CokeOvenModuleWorldData cokeData) {
                helper.assertTrue(controller.equals(cokeData.findConflict(foreignController, max.east(), max.east())),
                        "Coke-oven body query omitted the module clearance");
                helper.assertTrue(cokeData.findConflict(controller, min, max) == null,
                        "Coke-oven body query conflicted with its own modules");
            }
        }

        for (var loader : LOADERS.subList(0, 2)) {
            ExternalModuleWorldData data = loader.apply(new CompoundTag());
            Claim body = new Claim(controller, ExternalModuleWorldData.BODY_ID, "body", controller, min, max);
            data.claimBody(body);
            helper.assertTrue(data.claim(first).success() == (data instanceof BlastFurnaceHotBlastWorldData),
                    "Own-body overlap exception changed");
            Claim foreignModule = new Claim(foreignController, "test-foreign", "left",
                    min.east(10), min.east(10), max.east(10));
            helper.assertTrue(data.claim(foreignModule).success(), "Foreign fixture module was rejected");
            data.claimBody(new Claim(controller, ExternalModuleWorldData.BODY_ID, "body", controller,
                    min, max.east(10)));
            helper.assertTrue(data.claimOf(foreignController, foreignModule.moduleId()) == null,
                    "Body expansion did not evict an overlapping foreign module");
            data.claimBody(new Claim(foreignController, ExternalModuleWorldData.BODY_ID, "body",
                    foreignController, min, max));
            helper.assertTrue(data.claimOf(controller, ExternalModuleWorldData.BODY_ID) != null,
                    "Overlapping body claims incorrectly evicted each other");
        }
        helper.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 100)
    public static void moduleClaimsPreserveNbtIdentityAndAtomicReplacement(GameTestHelper helper) {
        BlockPos controller = helper.absolutePos(new BlockPos(3, 3, 3));
        for (var loader : LOADERS) {
            ExternalModuleWorldData data = loader.apply(new CompoundTag());
            BlockPos.MutableBlockPos mutable = controller.mutable();
            Claim first = new Claim(mutable, "test-right", "right", mutable, mutable, mutable);
            helper.assertTrue(data.claim(first).success(), "Mutable-position fixture claim failed");
            mutable.move(Direction.EAST);
            helper.assertTrue(first.controller().equals(controller) && first.anchor().equals(controller)
                            && first.min().equals(controller) && first.max().equals(controller),
                    "Claim geometry retained a mutable position reference");
            Claim second = new Claim(controller, "test-top", "top", controller.above(5),
                    controller.above(5), controller.above(6));
            Claim foreign = new Claim(controller.east(10), "test-foreign", "left", controller.east(8),
                    controller.east(8), controller.east(9));
            helper.assertTrue(data.claim(second).success() && data.claim(foreign).success(),
                    "Independent claims were rejected");
            data.setDirty(false);
            Claim blockedReplacement = new Claim(controller, first.moduleId(), first.faceId(), foreign.anchor(),
                    foreign.min(), foreign.max());
            helper.assertTrue(!data.claim(blockedReplacement).success()
                            && first.equals(data.claimOf(controller, first.moduleId())) && !data.isDirty(),
                    "Failed replacement changed the previous claim");
            Claim replacement = new Claim(controller, first.moduleId(), first.faceId(), controller.west(5),
                    controller.west(5), controller.west(4));
            helper.assertTrue(data.claim(replacement).success()
                            && !data.owns(controller, first.anchor()) && data.owns(controller, replacement.anchor()),
                    "Successful replacement retained the previous anchor");
            CompoundTag saved = data.save(new CompoundTag());
            ListTag savedClaims = saved.getList("claims", Tag.TAG_COMPOUND);
            helper.assertTrue(savedClaims.size() == 3 && savedClaims.getCompound(0).getAllKeys().equals(
                            Set.of("controller", "module", "face", "anchor", "min", "max")),
                    "Persisted module claim schema changed");
            ExternalModuleWorldData restored = loader.apply(saved);
            helper.assertTrue(replacement.equals(restored.claimOf(controller, first.moduleId()))
                            && second.equals(restored.claimOf(controller, second.moduleId()))
                            && foreign.equals(restored.claimOf(foreign.controller(), foreign.moduleId())),
                    "NBT round trip changed claim identity or geometry");
            restored.release(controller, first.moduleId());
            helper.assertTrue(restored.claimOf(controller, first.moduleId()) == null
                            && restored.claimOf(controller, second.moduleId()) != null,
                    "Releasing one module removed another module");
            restored.releaseAll(controller);
            helper.assertTrue(restored.claimOf(controller, second.moduleId()) == null
                            && restored.claimOf(foreign.controller(), foreign.moduleId()) != null,
                    "Controller removal affected foreign ownership");
        }

        CompoundTag legacyClaim = new CompoundTag();
        legacyClaim.putLong("controller", controller.asLong());
        legacyClaim.putLong("anchor", controller.east().asLong());
        legacyClaim.putLong("min", controller.asLong());
        legacyClaim.putLong("max", controller.offset(3, 3, 3).asLong());
        ListTag legacyClaims = new ListTag();
        legacyClaims.add(legacyClaim);
        CompoundTag legacy = new CompoundTag();
        legacy.put("claims", legacyClaims);
        BlastFurnaceHotBlastWorldData migrated = BlastFurnaceHotBlastWorldData.load(legacy);
        Claim migratedClaim = migrated.claimOf(controller);
        helper.assertTrue(migratedClaim != null && migratedClaim.moduleId().equals(BlastFurnaceHotBlastModule.ID)
                        && migratedClaim.faceId().equals("back") && migrated.owns(controller, controller.east()),
                "Legacy hot-blast ownership did not acquire its stable module and face IDs");
        helper.assertTrue(migratedClaim.equals(BlastFurnaceHotBlastWorldData.load(migrated.save(new CompoundTag()))
                        .claimOf(controller)),
                "Migrated hot-blast claim did not survive a second load");
        migrated.release(controller);
        helper.assertTrue(migrated.claimOf(controller) == null, "Legacy release entry point stopped working");
        helper.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 100)
    public static void moduleClaimsPruneOnlyVerifiedLoadedOwners(GameTestHelper helper) {
        var definitions = List.of(GSEMachines.BOILER_ROOM_BRONZE,
                GSEMachines.LARGE_STEAM_BLAST_FURNACE, GSEMachines.LARGE_COKE_OVEN);
        for (int index = 0; index < LOADERS.size(); index++) {
            ExternalModuleWorldData data = LOADERS.get(index).apply(new CompoundTag());
            BlockPos controller = helper.absolutePos(new BlockPos(4 + index * 4, 3, 4));
            BlockPos absent = controller.south(3);
            BlockPos remote = controller.east(100_000);
            helper.getLevel().setBlockAndUpdate(controller, definitions.get(index).getBlock().defaultBlockState());
            helper.getLevel().setBlockAndUpdate(absent, Blocks.AIR.defaultBlockState());
            Claim live = new Claim(controller, "test-live", "right", controller, controller, controller);
            Claim stale = new Claim(absent, "test-stale", "right", absent, absent, absent);
            Claim unloaded = new Claim(remote, "test-remote", "right", remote, remote, remote);
            helper.assertTrue(data.claim(live).success() && data.claim(stale).success() && data.claim(unloaded).success(),
                    "Owner cleanup fixture claims failed");
            data.pruneStaleClaims(helper.getLevel().getChunkAt(controller));
            data.pruneStaleClaims(helper.getLevel().getChunkAt(absent));
            helper.assertTrue(live.equals(data.claimOf(controller, live.moduleId()))
                            && data.claimOf(absent, stale.moduleId()) == null
                            && unloaded.equals(data.claimOf(remote, unloaded.moduleId())),
                    "Cleanup removed a valid or unverified owner, or retained a verified stale owner");
            helper.assertTrue(!helper.getLevel().hasChunkAt(remote), "Owner cleanup loaded a remote chunk");
            helper.getLevel().setBlockAndUpdate(controller, Blocks.STONE.defaultBlockState());
            data.pruneStaleClaims(helper.getLevel().getChunkAt(controller));
            helper.assertTrue(data.claimOf(controller, live.moduleId()) == null,
                    "Removed controller retained module ownership");
        }
        helper.succeed();
    }

    @GameTest(template = "empty_32x32x32", timeoutTicks = 100)
    public static void moduleStructuresDistinguishMissingInvalidAndUnloaded(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos solid = helper.absolutePos(new BlockPos(3, 3, 3));
        BlockPos air = solid.east();
        BlockPos.MutableBlockPos mutable = solid.mutable();
        List<Requirement> requirements = List.of(Requirement.solid(mutable, Blocks.STONE), Requirement.air(air));
        mutable.move(Direction.UP);
        helper.assertTrue(requirements.get(0).pos().equals(solid), "Structure retained a mutable position");
        level.setBlockAndUpdate(solid, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
        helper.assertTrue(ModuleStructure.validate(level, requirements) == Result.MISSING, "Empty module was not missing");
        ModuleStructure.place(level, requirements);
        helper.assertTrue(ModuleStructure.validate(level, requirements) == Result.VALID, "Exact module was not valid");
        level.setBlockAndUpdate(solid, Blocks.DIRT.defaultBlockState());
        helper.assertTrue(ModuleStructure.validate(level, requirements) == Result.INVALID, "Wrong block was accepted");
        ModuleStructure.place(level, requirements);
        level.setBlockAndUpdate(air, Blocks.STONE.defaultBlockState());
        helper.assertTrue(ModuleStructure.validate(level, requirements) == Result.INVALID, "Blocked strict air was accepted");
        level.setBlockAndUpdate(air, Blocks.CAVE_AIR.defaultBlockState());
        helper.assertTrue(ModuleStructure.validate(level, requirements) == Result.VALID, "Cave air was not treated as air");
        BlockPos unloaded = solid.east(100_000);
        helper.assertTrue(!level.hasChunkAt(unloaded), "Unloaded structure fixture was already loaded");
        helper.assertTrue(ModuleStructure.validate(level, List.of(Requirement.solid(solid, Blocks.DIRT),
                        Requirement.solid(unloaded, Blocks.STONE))) == Result.UNLOADED
                        && !level.hasChunkAt(unloaded),
                "Incomplete chunk coverage was treated as damage or forced a chunk load");
        var terminal = UltimateTerminalModuleProvider.fromStructure(requirements);
        helper.assertTrue(terminal.size() == 2 && terminal.get(0).pos().equals(solid)
                        && terminal.get(0).block() == Blocks.STONE && !terminal.get(0).air()
                        && terminal.get(1).pos().equals(air) && terminal.get(1).air(),
                "Terminal conversion changed structure order, blocks or strict air");

        BlockPos controller = helper.absolutePos(new BlockPos(16, 3, 3));
        var hotBlast = BlastFurnaceHotBlastModule.terminalRequirements(controller, Direction.NORTH);
        for (var requirement : hotBlast) level.setBlockAndUpdate(requirement.pos(), Blocks.AIR.defaultBlockState());
        var connector = hotBlast.get(0);
        level.setBlockAndUpdate(connector.pos(), connector.block().defaultBlockState());
        helper.assertTrue(BlastFurnaceHotBlastModule.validate(level, controller, Direction.NORTH) == Result.MISSING,
                "Main-body connector was mistaken for a partially built hot-blast module");
        helper.succeed();
    }
}
