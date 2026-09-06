package com.hoshino.gregsteamexpansion.cokeoven;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipes;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.hoshino.gregsteamexpansion.GregSteamExpansion;
import com.hoshino.gregsteamexpansion.machine.multiblock.cokeoven.GSECokeOvenMachine;
import com.hoshino.gregsteamexpansion.machine.multiblock.part.GSECokeOvenHatch;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;

import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 普通焦炉兼容接入 (coke-ovens.md): 在 GTCEu 完成注册后把 `gtceu:coke_oven`
 * 与 `gtceu:coke_oven_hatch` 的机器工厂精确替换为本模组子类, 并替换结构图案与
 * 结构预览; 精确覆盖上游合成配方 (见 {@code GregSteamExpansionAddon#removeRecipes})
 * 后在启动时验证替换结果。
 */
public final class GSECokeOvenInit {

    /** 上游程序化合成配方的真实资源 ID (ShapedRecipeBuilder 会加 shaped/ 前缀)。 */
    public static final ResourceLocation UPSTREAM_OVEN_RECIPE_ID = com.gregtechceu.gtceu.GTCEu.id("shaped/coke_oven");
    public static final ResourceLocation UPSTREAM_HATCH_RECIPE_ID = com.gregtechceu.gtceu.GTCEu.id("shaped/coke_oven_hatch");

    private static boolean initialized;

    private GSECokeOvenInit() {}

    /** 在 FMLCommonSetupEvent 的 enqueueWork 中调用 (注册已完成, 世界未加载)。 */
    public static void init() {
        if (initialized) return;
        initialized = true;

        // 控制器: 精确替换机器工厂、结构图案与结构预览; 其他定义 (模型/纹理/
        // 掉落/物理属性) 保持上游不变。definition 级字段是 Supplier, 在此处
        // 以自身定义回填 (与 builder .pattern()/.shapeInfos() 的包装一致)。
        MultiblockMachineDefinition oven = GTMultiMachines.COKE_OVEN;
        oven.setMachineSupplier(holder -> new GSECokeOvenMachine(holder));
        oven.setPatternFactory(() -> CokeOvenStructures.createPattern(oven));
        oven.setShapes(() -> CokeOvenStructures.shapeInfos(oven));
        oven.setAdditionalDisplay(GSECokeOvenInit::addControllerDisplay);
        // 两级物品提示 (替代上游单行 tooltip, coke-ovens.md 物品提示)。
        oven.setTooltipBuilder((stack, lines) -> appendClientTooltip(
                "gregsteamexpansion.machine.coke_oven.tooltip", lines));

        // 焦炉仓: 精确替换机器工厂并允许正面覆板 (模式与覆板能力取交集)。
        MachineDefinition hatch = GTMachines.COKE_OVEN_HATCH;
        hatch.setMachineSupplier(holder -> new GSECokeOvenHatch(holder));
        hatch.setAllowCoverOnFront(true);
        hatch.setTooltipBuilder((stack, lines) -> appendClientTooltip(
                "gregsteamexpansion.machine.coke_oven_hatch.tooltip", lines));
    }

    /** 两级物品提示的客户端委托 (dist 守卫, 方法体惰性解析 client 类)。 */
    private static void appendClientTooltip(String prefix, java.util.List<net.minecraft.network.chat.Component> lines) {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            com.hoshino.gregsteamexpansion.client.cokeoven.CokeOvenTooltipBuilder.append(prefix, lines);
        }
    }

    /** 控制器多方块信息页: 复用与 Jade 相同的状态与阻塞原因 (服务端权威)。 */
    private static void addControllerDisplay(com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController controller,
                                             java.util.List<net.minecraft.network.chat.Component> components) {
        if (controller instanceof GSECokeOvenMachine oven) {
            components.add(oven.getStatusText());
            components.addAll(oven.getStatusDetails());
        }
    }

    //////////////////////////////////////
    // ******** 启动配方验证 ********//
    //////////////////////////////////////

    /**
     * 配方加载完成后的验收 (coke-ovens.md 获取配方): 旧配方不再残留, 新图案能够
     * 匹配, 每个目标输出只有一份正常获取配方; 异常时记录包含资源 ID 的明确错误。
     */
    public static void verifyRecipes(MinecraftServer server) {
        var manager = server.getRecipeManager();
        var registryAccess = server.registryAccess();
        verifySingleRecipe(manager, registryAccess, UPSTREAM_OVEN_RECIPE_ID, "gtceu:coke_oven");
        verifySingleRecipe(manager, registryAccess, UPSTREAM_HATCH_RECIPE_ID, "gtceu:coke_oven_hatch");
        // 大型焦炉获取配方验收 (coke-ovens.md 已确认大型焦炉控制器/仓配方):
        // 各只有一条获取配方且产物正确。
        verifySingleRecipe(manager, registryAccess,
                com.hoshino.gregsteamexpansion.GregSteamExpansion.id("large_coke_oven"),
                "gregsteamexpansion:large_coke_oven");
        verifySingleRecipe(manager, registryAccess,
                com.hoshino.gregsteamexpansion.GregSteamExpansion.id("large_coke_oven_hatch"),
                "gregsteamexpansion:large_coke_oven_hatch");
    }

    private static void verifySingleRecipe(net.minecraft.world.item.crafting.RecipeManager manager,
                                           net.minecraft.core.RegistryAccess registryAccess,
                                           ResourceLocation recipeId, String expectedItemId) {
        var expectedItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(expectedItemId));
        if (expectedItem == null) {
            GregSteamExpansion.LOGGER.error("[Coke Oven] Verification failed: expected item {} not registered",
                    expectedItemId);
            return;
        }
        var byId = manager.byKey(recipeId);
        if (byId.isEmpty()) {
            GregSteamExpansion.LOGGER.error(
                    "[Coke Oven] Verification failed: replacement recipe {} is missing", recipeId);
            return;
        }
        var recipe = byId.get();
        if (!recipe.getResultItem(registryAccess).is(expectedItem)) {
            GregSteamExpansion.LOGGER.error(
                    "[Coke Oven] Verification failed: recipe {} produces {}, expected {}",
                    recipeId, recipe.getResultItem(registryAccess).getItem(), expectedItemId);
        }
        // 同一输出只允许一份正常获取配方。
        int found = 0;
        for (Recipe<?> other : manager.getRecipes()) {
            if (other.getResultItem(registryAccess).is(expectedItem)) {
                found++;
                if (found > 1) {
                    GregSteamExpansion.LOGGER.error(
                            "[Coke Oven] Verification failed: duplicate acquisition recipe {} for {}",
                            other.getId(), expectedItemId);
                    break;
                }
            }
        }
    }

    //////////////////////////////////////
    // ****** 服务器事件维护 ******//
    //////////////////////////////////////

    /**
     * 数据包重载后刷新合法焦炉配方索引; 服务器启动后验证合成配方替换结果;
     * 区块加载后清理已确认控制器消失的陈旧占用记录。
     */
    @Mod.EventBusSubscriber(modid = GregSteamExpansion.MOD_ID)
    public static final class ServerListener {

        @SubscribeEvent
        public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
            GSECokeOvenInit.verifyRecipes(event.getServer());
        }

        @SubscribeEvent
        public static void onAddReloadListeners(net.minecraftforge.event.AddReloadListenerEvent event) {
            // 在 RecipeManager.apply 完成后、客户端同步前刷新合法配方索引并验证
            // 合成配方替换结果 (首次加载与每次 /reload 都会执行)。
            event.addListener((net.minecraft.server.packs.resources.PreparableReloadListener) (
                    barrier, resourceManager, preparations, executions, backgroundExecutor, gameExecutor) ->
                    java.util.concurrent.CompletableFuture
                            .completedFuture(null)
                            .thenCompose(barrier::wait)
                            .thenAcceptAsync(ignored -> {
                                CokeOvenRecipeIndex.invalidate();
                                var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
                                if (server != null) {
                                    GSECokeOvenInit.verifyRecipes(server);
                                }
                            }, gameExecutor));
        }
    }

    /** 客户端配方同步后刷新索引 (GUI 槽位过滤在两侧都要可用)。 */
    @Mod.EventBusSubscriber(modid = GregSteamExpansion.MOD_ID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class ClientListener {
        @SubscribeEvent
        public static void onRecipesUpdated(net.minecraftforge.client.event.RecipesUpdatedEvent event) {
            CokeOvenRecipeIndex.invalidate();
        }
    }

    //////////////////////////////////////
    // ****** 区块加载维护 ******//
    //////////////////////////////////////

    /**
     * 控制器所在区块加载并确认方块缺失后释放陈旧占用记录; 不能仅因区块暂时
     * 不可用删除记录 (coke-ovens.md 普通焦炉结构独占)。
     */
    @Mod.EventBusSubscriber(modid = GregSteamExpansion.MOD_ID)
    public static final class ChunkListener {
        @SubscribeEvent
        public static void onChunkLoad(ChunkEvent.Load event) {
            if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
            if (!(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)) return;
            CokeOvenWorldData.getOrCreate(serverLevel).pruneStaleClaims(serverLevel, chunk.getPos());
        }
    }
}
