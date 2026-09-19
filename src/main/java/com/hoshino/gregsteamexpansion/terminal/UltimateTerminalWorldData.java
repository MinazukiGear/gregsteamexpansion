package com.hoshino.gregsteamexpansion.terminal;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.hoshino.gregsteamexpansion.terminal.compat.UltimateTerminalAECompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent owner projects, transaction escrow and rate-limited construction jobs. */
public final class UltimateTerminalWorldData extends SavedData {
    private static final String DATA_NAME = "gregsteamexpansion_ultimate_terminal";
    private static final int DATA_VERSION = 3;
    public static final int MAX_TARGETS = 16;

    public enum JobState {
        IDLE,
        RUNNING,
        PAUSED,
        FAILED,
        COMPLETE
    }

    public record Target(ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos pos) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", dimension.location().toString());
            tag.putLong("pos", pos.asLong());
            return tag;
        }

        static Target load(CompoundTag tag) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("dimension"));
            if (id == null) id = net.minecraft.world.level.Level.OVERWORLD.location();
            return new Target(ResourceKey.create(Registries.DIMENSION, id), BlockPos.of(tag.getLong("pos")));
        }

        String key() {
            return dimension.location() + "#" + pos.asLong();
        }
    }

    public static final class Project {
        final List<Target> targets = new ArrayList<>();
        final Map<String, List<OwnedBlock>> ownedPositions = new HashMap<>();
        final List<ItemStack> pending = new ArrayList<>();
        final Map<String, TerminalBuildProfile> templates = new HashMap<>();
        final Map<String, TerminalBuildProfile> overrides = new HashMap<>();
        UltimateTerminalMode mode = UltimateTerminalMode.BUILD;
        TerminalBuildProfile defaultProfile = new TerminalBuildProfile();
        int selectedTarget;
        boolean useAE = true;
        @Nullable Job job;

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            ListTag targetList = new ListTag();
            targets.forEach(target -> targetList.add(target.save()));
            tag.put("targets", targetList);
            tag.putInt("mode", mode.ordinal());
            tag.put("defaultProfile", defaultProfile.save());
            tag.putInt("selectedTarget", selectedTarget);
            tag.putBoolean("useAE", useAE);
            tag.put("templates", saveProfiles(templates));
            tag.put("overrides", saveProfiles(overrides));
            ListTag ownedList = new ListTag();
            ownedPositions.forEach((key, blocks) -> {
                CompoundTag entry = new CompoundTag();
                entry.putString("key", key);
                ListTag blockList = new ListTag();
                for (OwnedBlock block : blocks) blockList.add(block.save());
                entry.put("blocks", blockList);
                ownedList.add(entry);
            });
            tag.put("owned", ownedList);
            tag.put("pending", saveStacks(pending));
            if (job != null) tag.put("job", job.save());
            return tag;
        }

        static Project load(CompoundTag tag) {
            Project project = new Project();
            ListTag targetList = tag.getList("targets", Tag.TAG_COMPOUND);
            for (int i = 0; i < targetList.size(); i++) project.targets.add(Target.load(targetList.getCompound(i)));
            int mode = tag.getInt("mode");
            if (mode >= 0 && mode < UltimateTerminalMode.values().length) {
                project.mode = UltimateTerminalMode.values()[mode];
            }
            project.defaultProfile = tag.contains("defaultProfile", Tag.TAG_COMPOUND)
                    ? TerminalBuildProfile.load(tag.getCompound("defaultProfile"))
                    : new TerminalBuildProfile(tag.getInt("repeatCount"), tag.getInt("coilTier"),
                            !tag.contains("buildHatches") || tag.getBoolean("buildHatches"));
            project.selectedTarget = Math.max(0, tag.getInt("selectedTarget"));
            project.useAE = !tag.contains("useAE") || tag.getBoolean("useAE");
            loadProfiles(tag.getList("templates", Tag.TAG_COMPOUND), project.templates);
            loadProfiles(tag.getList("overrides", Tag.TAG_COMPOUND), project.overrides);
            ListTag ownedList = tag.getList("owned", Tag.TAG_COMPOUND);
            for (int i = 0; i < ownedList.size(); i++) {
                CompoundTag entry = ownedList.getCompound(i);
                List<OwnedBlock> blocks = new ArrayList<>();
                ListTag blockList = entry.getList("blocks", Tag.TAG_COMPOUND);
                for (int j = 0; j < blockList.size(); j++) blocks.add(OwnedBlock.load(blockList.getCompound(j)));
                project.ownedPositions.put(entry.getString("key"), blocks);
            }
            project.pending.addAll(loadStacks(tag.getList("pending", Tag.TAG_COMPOUND)));
            if (tag.contains("job", Tag.TAG_COMPOUND)) project.job = Job.load(tag.getCompound("job"));
            return project;
        }

        private static ListTag saveProfiles(Map<String, TerminalBuildProfile> profiles) {
            ListTag list = new ListTag();
            profiles.forEach((key, profile) -> {
                CompoundTag entry = new CompoundTag();
                entry.putString("key", key);
                entry.put("profile", profile.save());
                list.add(entry);
            });
            return list;
        }

        private static void loadProfiles(ListTag list, Map<String, TerminalBuildProfile> output) {
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                if (!entry.getString("key").isBlank()) {
                    output.put(entry.getString("key"), TerminalBuildProfile.load(entry.getCompound("profile")));
                }
            }
        }
    }

    public record TargetView(ResourceLocation dimension, BlockPos pos, String name) {}

    public record MaterialView(ItemStack stack, long required, long inventory, long network) {
        public long missing() { return Math.max(0, required - inventory - network); }
    }

    public record PreviewData(List<TargetView> targets, int selectedTarget,
                              UltimateStructurePlanner.Plan plan,
                              List<MaterialView> selectedMaterials,
                              List<MaterialView> batchMaterials,
                              TerminalBuildProfile profile, boolean targetOverride,
                              String error) {}

    private record OwnedBlock(long pos, String blockId) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("pos", pos);
            tag.putString("block", blockId);
            return tag;
        }

        static OwnedBlock load(CompoundTag tag) {
            return new OwnedBlock(tag.getLong("pos"), tag.getString("block"));
        }
    }

    private static final class TargetWork {
        final Target target;
        final List<UltimateStructurePlanner.Placement> operations;
        final List<Long> changed = new ArrayList<>();
        int cursor;

        TargetWork(Target target, List<UltimateStructurePlanner.Placement> operations) {
            this.target = target;
            this.operations = new ArrayList<>(operations);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put("target", target.save());
            tag.putInt("cursor", cursor);
            ListTag ops = new ListTag();
            for (var operation : operations) {
                CompoundTag entry = new CompoundTag();
                entry.putLong("pos", operation.pos().asLong());
                entry.put("stack", operation.stack().save(new CompoundTag()));
                if (!operation.replaced().isEmpty()) {
                    entry.put("replaced", operation.replaced().save(new CompoundTag()));
                }
                ops.add(entry);
            }
            tag.put("operations", ops);
            tag.putLongArray("changed", changed);
            return tag;
        }

        static TargetWork load(CompoundTag tag) {
            List<UltimateStructurePlanner.Placement> operations = new ArrayList<>();
            ListTag ops = tag.getList("operations", Tag.TAG_COMPOUND);
            for (int i = 0; i < ops.size(); i++) {
                CompoundTag entry = ops.getCompound(i);
                operations.add(new UltimateStructurePlanner.Placement(
                        BlockPos.of(entry.getLong("pos")), ItemStack.of(entry.getCompound("stack")),
                        entry.contains("replaced", Tag.TAG_COMPOUND)
                                ? ItemStack.of(entry.getCompound("replaced")) : ItemStack.EMPTY));
            }
            TargetWork work = new TargetWork(Target.load(tag.getCompound("target")), operations);
            work.cursor = tag.getInt("cursor");
            for (long pos : tag.getLongArray("changed")) work.changed.add(pos);
            return work;
        }
    }

    private static final class Job {
        final UltimateTerminalMode mode;
        final List<TargetWork> targets;
        final List<ItemStack> escrow;
        final List<ItemStack> recovered = new ArrayList<>();
        JobState state = JobState.RUNNING;
        int targetIndex;
        int completedOperations;
        int totalOperations;
        String failure = "";

        Job(UltimateTerminalMode mode, List<TargetWork> targets, List<ItemStack> escrow) {
            this.mode = mode;
            this.targets = targets;
            this.escrow = escrow;
            this.totalOperations = targets.stream().mapToInt(work -> work.operations.size()).sum();
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("mode", mode.ordinal());
            tag.putInt("state", state.ordinal());
            tag.putInt("targetIndex", targetIndex);
            tag.putInt("completedOperations", completedOperations);
            tag.putInt("totalOperations", totalOperations);
            tag.putString("failure", failure);
            ListTag targetList = new ListTag();
            targets.forEach(target -> targetList.add(target.save()));
            tag.put("targets", targetList);
            tag.put("escrow", saveStacks(escrow));
            tag.put("recovered", saveStacks(recovered));
            return tag;
        }

        static Job load(CompoundTag tag) {
            List<TargetWork> targets = new ArrayList<>();
            ListTag targetList = tag.getList("targets", Tag.TAG_COMPOUND);
            for (int i = 0; i < targetList.size(); i++) targets.add(TargetWork.load(targetList.getCompound(i)));
            int modeId = tag.getInt("mode");
            UltimateTerminalMode mode = modeId >= 0 && modeId < UltimateTerminalMode.values().length
                    ? UltimateTerminalMode.values()[modeId] : UltimateTerminalMode.BUILD;
            Job job = new Job(mode, targets, loadStacks(tag.getList("escrow", Tag.TAG_COMPOUND)));
            int stateId = tag.getInt("state");
            if (stateId >= 0 && stateId < JobState.values().length) job.state = JobState.values()[stateId];
            job.targetIndex = tag.getInt("targetIndex");
            job.completedOperations = tag.getInt("completedOperations");
            job.totalOperations = tag.getInt("totalOperations");
            job.failure = tag.getString("failure");
            job.recovered.addAll(loadStacks(tag.getList("recovered", Tag.TAG_COMPOUND)));
            return job;
        }
    }

    private final Map<UUID, Project> projects = new LinkedHashMap<>();

    public static UltimateTerminalWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                UltimateTerminalWorldData::load, UltimateTerminalWorldData::new, DATA_NAME);
    }

    private UltimateTerminalWorldData() {}

    public static UltimateTerminalWorldData load(CompoundTag tag) {
        UltimateTerminalWorldData data = new UltimateTerminalWorldData();
        ListTag list = tag.getList("projects", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("owner")) data.projects.put(entry.getUUID("owner"), Project.load(entry.getCompound("data")));
        }
        return data;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        tag.putInt("dataVersion", DATA_VERSION);
        ListTag list = new ListTag();
        projects.forEach((owner, project) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("owner", owner);
            entry.put("data", project.save());
            list.add(entry);
        });
        tag.put("projects", list);
        return tag;
    }

    private Project project(UUID owner) {
        return projects.computeIfAbsent(owner, ignored -> new Project());
    }

    @Nullable
    private static Target selectedTarget(Project project) {
        if (project.targets.isEmpty()) return null;
        project.selectedTarget = Math.max(0, Math.min(project.selectedTarget, project.targets.size() - 1));
        return project.targets.get(project.selectedTarget);
    }

    private TerminalBuildProfile selectedProfile(ServerPlayer player, boolean create) {
        Project project = project(player.getUUID());
        Target target = selectedTarget(project);
        return target == null ? project.defaultProfile : profileFor(player.server, project, target, create);
    }

    private static TerminalBuildProfile profileFor(MinecraftServer server, Project project,
                                                   Target target, boolean create) {
        TerminalBuildProfile override = project.overrides.get(target.key());
        if (override != null) return override;
        ServerLevel level = server.getLevel(target.dimension());
        String templateKey = target.key();
        if (level != null && level.hasChunkAt(target.pos())) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(level.getBlockState(target.pos()).getBlock());
            if (id != null) templateKey = id.toString();
        }
        TerminalBuildProfile template = project.templates.get(templateKey);
        if (template == null && create) {
            template = project.defaultProfile.copy();
            project.templates.put(templateKey, template);
        }
        return template != null ? template : project.defaultProfile;
    }

    public PreviewData preview(ServerPlayer player) {
        Project project = project(player.getUUID());
        List<TargetView> targets = new ArrayList<>();
        for (Target target : project.targets) {
            ServerLevel targetLevel = player.server.getLevel(target.dimension());
            String name = target.pos().toShortString();
            if (targetLevel != null && targetLevel.hasChunkAt(target.pos())) {
                ItemStack controller = targetLevel.getBlockState(target.pos()).getBlock().asItem().getDefaultInstance();
                if (!controller.isEmpty()) name = controller.getHoverName().getString();
            }
            targets.add(new TargetView(target.dimension().location(), target.pos(), name));
        }
        Target selected = selectedTarget(project);
        if (selected == null) {
            return new PreviewData(List.copyOf(targets), 0,
                    UltimateStructurePlanner.Plan.failed("no_target"), List.of(), List.of(),
                    project.defaultProfile.copy(), false, "no_target");
        }
        TerminalBuildProfile profile = profileFor(player.server, project, selected, false);
        UltimateStructurePlanner.Plan selectedPlan = planForPreview(player.server, project, selected, profile);
        List<ItemStack> selectedRequirements = requirements(selectedPlan);
        List<ItemStack> batchRequirements = new ArrayList<>();
        for (Target target : project.targets) {
            UltimateStructurePlanner.Plan plan = planForPreview(player.server, project, target,
                    profileFor(player.server, project, target, false));
            for (ItemStack requirement : requirements(plan)) {
                mergeRequirement(batchRequirements, requirement, requirement.getCount());
            }
        }
        return new PreviewData(List.copyOf(targets), project.selectedTarget, selectedPlan,
                materialViews(player, selectedRequirements, project.useAE),
                materialViews(player, batchRequirements, project.useAE), profile.copy(),
                project.overrides.containsKey(selected.key()), selectedPlan.error());
    }

    private UltimateStructurePlanner.Plan planForPreview(MinecraftServer server, Project project,
                                                         Target target, TerminalBuildProfile profile) {
        ServerLevel level = server.getLevel(target.dimension());
        if (level == null || !level.hasChunkAt(target.pos())) {
            return UltimateStructurePlanner.Plan.failed("controller_unloaded");
        }
        if (project.mode != UltimateTerminalMode.DISMANTLE) {
            return UltimateStructurePlanner.plan(level, target.pos(), profile,
                    project.mode == UltimateTerminalMode.UPGRADE);
        }
        return UltimateStructurePlanner.planDismantle(level, target.pos(), profile);
    }

    private static List<ItemStack> requirements(UltimateStructurePlanner.Plan plan) {
        List<ItemStack> requirements = new ArrayList<>();
        for (var placement : plan.placements()) mergeRequirement(requirements, placement.stack(), 1);
        return requirements;
    }

    private static List<MaterialView> materialViews(ServerPlayer player, List<ItemStack> requirements,
                                                    boolean useAE) {
        List<MaterialView> views = new ArrayList<>();
        for (ItemStack requirement : requirements) {
            long inventory = countInventory(player, requirement);
            long network = useAE ? UltimateTerminalAECompat.available(player, requirement) : 0;
            views.add(new MaterialView(requirement.copy(), requirement.getCount(), inventory, network));
        }
        views.sort(java.util.Comparator
                .comparingLong(MaterialView::missing).reversed()
                .thenComparing(view -> ForgeRegistries.ITEMS.getKey(view.stack().getItem()).toString()));
        return List.copyOf(views);
    }

    @Nullable
    private static ResourceLocation blockId(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return null;
        return ForgeRegistries.BLOCKS.getKey(blockItem.getBlock());
    }

    public boolean addTarget(ServerPlayer player, ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos pos) {
        Project project = project(player.getUUID());
        if (isActive(project.job)) return false;
        Target target = new Target(dimension, pos.immutable());
        if (project.targets.contains(target) || project.targets.size() >= MAX_TARGETS) return false;
        if (!project.targets.isEmpty() && !project.targets.get(0).dimension().equals(dimension)) return false;
        project.targets.add(target);
        project.selectedTarget = project.targets.size() - 1;
        setDirty();
        return true;
    }

    public boolean removeTarget(ServerPlayer player, ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos pos) {
        Project project = project(player.getUUID());
        if (isActive(project.job)) return false;
        boolean removed = project.targets.remove(new Target(dimension, pos));
        if (removed) {
            project.overrides.remove(new Target(dimension, pos).key());
            project.selectedTarget = Math.max(0, Math.min(project.selectedTarget, project.targets.size() - 1));
            setDirty();
        }
        return removed;
    }

    public int targetCount(UUID owner) { return project(owner).targets.size(); }
    public int mode(UUID owner) { return project(owner).mode.ordinal(); }
    public int selectedTarget(UUID owner) { return project(owner).selectedTarget; }
    public int repeatCount(ServerPlayer player) { return selectedProfile(player, false).repeatCount(); }
    public boolean targetOverride(ServerPlayer player) {
        Project project = project(player.getUUID());
        Target target = selectedTarget(project);
        return target != null && project.overrides.containsKey(target.key());
    }
    public boolean useAE(UUID owner) { return project(owner).useAE; }
    public JobState state(UUID owner) {
        Job job = project(owner).job;
        return job == null ? JobState.IDLE : job.state;
    }
    public int progress(UUID owner) {
        Job job = project(owner).job;
        return job == null ? 0 : job.completedOperations;
    }
    public int total(UUID owner) {
        Job job = project(owner).job;
        return job == null ? 0 : job.totalOperations;
    }
    public int pendingCount(UUID owner) {
        return project(owner).pending.stream().mapToInt(ItemStack::getCount).sum();
    }

    public void cycleMode(UUID owner) { project(owner).mode = project(owner).mode.next(); setDirty(); }
    public void changeRepeats(ServerPlayer player, int delta) {
        selectedProfile(player, true).changeRepeats(delta);
        setDirty();
    }
    public void setChannel(ServerPlayer player, String channelId, int selection) {
        boolean structureSize = channelId.equals(UltimateTerminalStructureVariants.CHANNEL_ID);
        if (!structureSize && !channelId.equals("coil") && UltimateTerminalConfig.channel(channelId) == null) return;
        Project project = project(player.getUUID());
        Target target = selectedTarget(project);
        if (target == null) return;
        ServerLevel level = player.server.getLevel(target.dimension());
        if (level == null || !level.hasChunkAt(target.pos())) return;
        TerminalBuildProfile profile = profileFor(player.server, project, target, true);
        UltimateStructurePlanner.Plan plan = UltimateStructurePlanner.plan(level, target.pos(), profile,
                project.mode == UltimateTerminalMode.UPGRADE);
        int maximum = structureSize ? plan.structure().options().size()
                : plan.channels().stream().filter(choice -> choice.id().equals(channelId))
                        .mapToInt(choice -> choice.options().size()).findFirst().orElse(0);
        if (maximum <= 0) return;
        profile.setChannel(channelId, selection, maximum);
        setDirty();
    }
    public void toggleAE(UUID owner) { Project project = project(owner); project.useAE = !project.useAE; setDirty(); }
    public void selectTarget(ServerPlayer player, int index) {
        Project project = project(player.getUUID());
        if (index >= 0 && index < project.targets.size()) {
            project.selectedTarget = index;
            setDirty();
        }
    }
    public void toggleTargetOverride(ServerPlayer player) {
        Project project = project(player.getUUID());
        Target target = selectedTarget(project);
        if (target == null) return;
        if (project.overrides.remove(target.key()) == null) {
            project.overrides.put(target.key(), profileFor(player.server, project, target, false).copy());
        }
        setDirty();
    }

    public void changePart(ServerPlayer player, ResourceLocation blockId, int delta) {
        Project project = project(player.getUUID());
        Target target = selectedTarget(project);
        if (target == null) return;
        ServerLevel level = player.server.getLevel(target.dimension());
        if (level == null || !level.hasChunkAt(target.pos())) return;
        TerminalBuildProfile profile = profileFor(player.server, project, target, true);
        UltimateStructurePlanner.Plan plan = UltimateStructurePlanner.plan(level, target.pos(), profile,
                project.mode == UltimateTerminalMode.UPGRADE);
        int maximum = plan.candidates().stream()
                .filter(candidate -> blockId.equals(blockId(candidate.stack())))
                .mapToInt(UltimateStructurePlanner.CandidateChoice::maximum).findFirst().orElse(0);
        if (maximum <= 0) return;
        profile.changePart(blockId, delta, maximum);
        setDirty();
    }
    public void clear(UUID owner) {
        Project project = project(owner);
        if (!isActive(project.job)) {
            project.targets.clear();
            project.overrides.clear();
            project.selectedTarget = 0;
        }
        setDirty();
    }

    public void collectPending(ServerPlayer player) {
        Project project = project(player.getUUID());
        deliver(player, project.pending, project.useAE, project.pending);
        setDirty();
    }

    public boolean start(ServerPlayer player) {
        Project project = project(player.getUUID());
        if (project.targets.isEmpty() || isActive(project.job)) return false;
        List<TargetWork> works = new ArrayList<>();
        List<ItemStack> requirements = new ArrayList<>();
        if (project.mode == UltimateTerminalMode.DISMANTLE) {
            for (Target target : project.targets) {
                ServerLevel level = player.server.getLevel(target.dimension());
                if (level == null || !level.hasChunkAt(target.pos())) return false;
                UltimateStructurePlanner.Plan plan = UltimateStructurePlanner.planDismantle(level, target.pos(),
                        profileFor(player.server, project, target, false));
                if (!plan.valid()) {
                    player.displayClientMessage(Component.literal(plan.error()), false);
                    return false;
                }
                works.add(new TargetWork(target, plan.placements()));
            }
            project.job = new Job(project.mode, works, new ArrayList<>());
        } else {
            for (Target target : project.targets) {
                ServerLevel level = player.server.getLevel(target.dimension());
                if (level == null || !level.hasChunkAt(target.pos())) return false;
                TerminalBuildProfile profile = profileFor(player.server, project, target, false);
                UltimateStructurePlanner.Plan plan = UltimateStructurePlanner.plan(level, target.pos(),
                        profile,
                        project.mode == UltimateTerminalMode.UPGRADE);
                if (!plan.valid()) {
                    player.displayClientMessage(Component.literal(plan.error()), false);
                    return false;
                }
                works.add(new TargetWork(target, plan.placements()));
                for (var placement : plan.placements()) mergeRequirement(requirements, placement.stack(), 1);
            }
            List<ItemStack> escrow = reserve(player, requirements, project.useAE);
            if (escrow == null) {
                player.displayClientMessage(Component.translatable(
                        "gregsteamexpansion.ultimate_terminal.materials_missing"), false);
                return false;
            }
            project.job = new Job(project.mode, works, escrow);
        }
        setDirty();
        return true;
    }

    public int tick(MinecraftServer server, int budget) {
        if (budget <= 0) return 0;
        int used = 0;
        for (var entry : projects.entrySet()) {
            if (used >= budget) break;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Project project = entry.getValue();
            Job job = project.job;
            if (job == null || (job.state != JobState.RUNNING && job.state != JobState.PAUSED)) continue;
            if (player == null || job.targetIndex >= job.targets.size()) {
                if (player == null) job.state = JobState.PAUSED;
                continue;
            }
            TargetWork work = job.targets.get(job.targetIndex);
            ServerLevel level = server.getLevel(work.target.dimension());
            if (level == null || player.level() != level || !level.hasChunkAt(work.target.pos())) {
                job.state = JobState.PAUSED;
                continue;
            }
            job.state = JobState.RUNNING;
            while (used < budget && work.cursor < work.operations.size()) {
                if (!level.hasChunkAt(work.operations.get(work.cursor).pos())) {
                    job.state = JobState.PAUSED;
                    setDirty();
                    break;
                }
                boolean success = job.mode == UltimateTerminalMode.DISMANTLE
                        ? removeOne(level, job, work)
                        : placeOne(level, player, job, work);
                if (!success) {
                    failCurrent(player, project, job, work, "operation_failed");
                    break;
                }
                work.cursor++;
                job.completedOperations++;
                used++;
                setDirty();
            }
            if (job.state == JobState.RUNNING && work.cursor >= work.operations.size()) {
                if (job.mode != UltimateTerminalMode.DISMANTLE && !validateOperations(level, work)) {
                    failCurrent(player, project, job, work, "structure_invalid");
                } else {
                    if (job.mode == UltimateTerminalMode.DISMANTLE) {
                        project.ownedPositions.remove(work.target.key());
                    } else {
                        List<OwnedBlock> owned = project.ownedPositions.computeIfAbsent(
                                work.target.key(), ignored -> new ArrayList<>());
                        for (long encoded : work.changed) {
                            ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(
                                    level.getBlockState(BlockPos.of(encoded)).getBlock());
                            if (blockId == null) continue;
                            owned.removeIf(block -> block.pos() == encoded);
                            owned.add(new OwnedBlock(encoded, blockId.toString()));
                        }
                    }
                    job.targetIndex++;
                    if (job.targetIndex >= job.targets.size()) finish(player, project, job);
                    setDirty();
                }
            }
        }
        return used;
    }

    private static boolean placeOne(ServerLevel level, ServerPlayer player, Job job, TargetWork work) {
        var operation = work.operations.get(work.cursor);
        boolean replacing = !operation.replaced().isEmpty();
        if (replacing) {
            if (level.getBlockEntity(operation.pos()) != null
                    || level.getBlockState(operation.pos()).getBlock().asItem() != operation.replaced().getItem()) {
                return false;
            }
        } else if (!level.isEmptyBlock(operation.pos())
                && !level.getBlockState(operation.pos()).canBeReplaced()) {
            return false;
        }
        ItemStack placing = operation.stack().copyWithCount(1);
        if (!(placing.getItem() instanceof BlockItem blockItem)) return false;
        if (!consume(job.escrow, operation.stack())) return false;
        if (replacing) level.setBlockAndUpdate(operation.pos(), Blocks.AIR.defaultBlockState());
        BlockPlaceContext context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, placing,
                new BlockHitResult(Vec3.atCenterOf(operation.pos()), net.minecraft.core.Direction.UP,
                        operation.pos(), false));
        InteractionResult result = blockItem.place(context);
        if (result == InteractionResult.FAIL || level.isEmptyBlock(operation.pos())) {
            if (replacing && operation.replaced().getItem() instanceof BlockItem oldBlock) {
                level.setBlockAndUpdate(operation.pos(), oldBlock.getBlock().defaultBlockState());
            }
            addSplit(job.escrow, operation.stack().copyWithCount(1));
            return false;
        }
        if (replacing) addSplit(job.recovered, operation.replaced().copyWithCount(1));
        work.changed.add(operation.pos().asLong());
        return true;
    }

    private static boolean removeOne(ServerLevel level, Job job, TargetWork work) {
        var operation = work.operations.get(work.cursor);
        if (level.getBlockEntity(operation.pos()) != null) return true;
        if (!level.isEmptyBlock(operation.pos())) {
            if (level.getBlockState(operation.pos()).getBlock().asItem() != operation.stack().getItem()) return false;
            level.setBlockAndUpdate(operation.pos(), Blocks.AIR.defaultBlockState());
            addSplit(job.recovered, operation.stack().copyWithCount(1));
            work.changed.add(operation.pos().asLong());
        }
        return true;
    }

    private static boolean validateOperations(ServerLevel level, TargetWork work) {
        for (var operation : work.operations) {
            if (level.getBlockState(operation.pos()).getBlock().asItem() != operation.stack().getItem()) return false;
        }
        return true;
    }

    private void failCurrent(ServerPlayer player, Project project, Job job, TargetWork work, String reason) {
        ServerLevel level = player.server.getLevel(work.target.dimension());
        if (level != null && job.mode != UltimateTerminalMode.DISMANTLE) {
            for (int i = 0; i < work.cursor; i++) {
                var operation = work.operations.get(i);
                if (level.getBlockEntity(operation.pos()) == null) {
                    if (operation.replaced().getItem() instanceof BlockItem oldBlock) {
                        level.setBlockAndUpdate(operation.pos(), oldBlock.getBlock().defaultBlockState());
                        consume(job.recovered, operation.replaced());
                    } else {
                        level.setBlockAndUpdate(operation.pos(), Blocks.AIR.defaultBlockState());
                    }
                }
                addSplit(job.escrow, operation.stack().copyWithCount(1));
            }
        }
        job.state = JobState.FAILED;
        job.failure = reason;
        deliver(player, job.escrow, project.useAE, project.pending);
        deliver(player, job.recovered, project.useAE, project.pending);
        player.displayClientMessage(Component.translatable(
                "gregsteamexpansion.ultimate_terminal.job_failed", reason), false);
        setDirty();
    }

    private void finish(ServerPlayer player, Project project, Job job) {
        deliver(player, job.escrow, project.useAE, project.pending);
        deliver(player, job.recovered, project.useAE, project.pending);
        job.state = JobState.COMPLETE;
        player.displayClientMessage(Component.translatable(
                "gregsteamexpansion.ultimate_terminal.job_complete"), false);
    }

    @Nullable
    private static List<ItemStack> reserve(ServerPlayer player, List<ItemStack> requirements, boolean useAE) {
        for (ItemStack requirement : requirements) {
            long inventory = countInventory(player, requirement);
            long network = useAE ? UltimateTerminalAECompat.available(player, requirement) : 0;
            if (inventory + network < requirement.getCount()) return null;
        }
        List<ItemStack> escrow = new ArrayList<>();
        for (ItemStack requirement : requirements) {
            int remaining = requirement.getCount();
            for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
                ItemStack stored = player.getInventory().getItem(slot);
                if (!ItemStack.isSameItemSameTags(stored, requirement)) continue;
                int amount = Math.min(remaining, stored.getCount());
                ItemStack extracted = player.getInventory().removeItem(slot, amount);
                addSplit(escrow, extracted);
                remaining -= amount;
            }
            if (remaining > 0 && useAE) {
                long extracted = UltimateTerminalAECompat.extract(player, requirement, remaining, false);
                if (extracted > 0) addSplit(escrow, requirement.copyWithCount((int) extracted));
                remaining -= (int) extracted;
            }
            if (remaining > 0) {
                List<ItemStack> pending = new ArrayList<>();
                deliver(player, escrow, useAE, pending);
                return null;
            }
        }
        return escrow;
    }

    private static long countInventory(ServerPlayer player, ItemStack wanted) {
        long count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameTags(stack, wanted)) count += stack.getCount();
        }
        return count;
    }

    private static boolean consume(List<ItemStack> escrow, ItemStack wanted) {
        for (Iterator<ItemStack> iterator = escrow.iterator(); iterator.hasNext();) {
            ItemStack stack = iterator.next();
            if (!ItemStack.isSameItemSameTags(stack, wanted)) continue;
            stack.shrink(1);
            if (stack.isEmpty()) iterator.remove();
            return true;
        }
        return false;
    }

    private static boolean isActive(@Nullable Job job) {
        return job != null && (job.state == JobState.RUNNING || job.state == JobState.PAUSED);
    }

    private static void deliver(ServerPlayer player, List<ItemStack> source, boolean useAE,
                                List<ItemStack> pending) {
        List<ItemStack> copy = new ArrayList<>(source);
        source.clear();
        for (ItemStack original : copy) {
            ItemStack remaining = original.copy();
            player.getInventory().add(remaining);
            if (!remaining.isEmpty() && useAE) {
                long inserted = UltimateTerminalAECompat.insert(player, remaining, remaining.getCount(), false);
                remaining.shrink((int) inserted);
            }
            if (!remaining.isEmpty()) addSplit(pending, remaining);
        }
    }

    private static void mergeRequirement(List<ItemStack> requirements, ItemStack stack, int amount) {
        for (ItemStack existing : requirements) {
            if (ItemStack.isSameItemSameTags(existing, stack)) {
                existing.grow(amount);
                return;
            }
        }
        requirements.add(stack.copyWithCount(amount));
    }

    private static void addSplit(List<ItemStack> list, ItemStack stack) {
        int remaining = stack.getCount();
        while (remaining > 0) {
            int amount = Math.min(remaining, Math.max(1, stack.getMaxStackSize()));
            list.add(stack.copyWithCount(amount));
            remaining -= amount;
        }
    }

    private static ListTag saveStacks(List<ItemStack> stacks) {
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) if (!stack.isEmpty()) list.add(stack.save(new CompoundTag()));
        return list;
    }

    private static List<ItemStack> loadStacks(ListTag list) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            if (!stack.isEmpty()) stacks.add(stack);
        }
        return stacks;
    }
}
