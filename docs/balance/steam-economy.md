# 蒸汽经济基线（自动生成）

由 `python tools/export_steam_economy.py` 从本仓库 Java 默认档位、常量与已生成获取配方导出。核对命令为 `python tools/export_steam_economy.py --check`。修改公式后须先复核模型再重新生成。

## 比较口径

以下为默认配置的理论批次结算，假设 20 TPS、供汽/辅料充足、输入连续且输出畅通。不包含管网限流、搜索/提交批次的调度间隔、缺料、卸载或人为停机；实际持续产能通常更低。随机增产列为长期期望，不能保证单批结果。关闭难度另列：倍率回到 1，但电路专精、高炉熟练度与水垢仍用 Normal 基线。整合包自定义参数、权重和数据包配方需另算；关闭难度时的 GTCEu 外壳产量沿用上游配置。

## 三档参数

| 参数 | Easy | Normal | Expert |
| --- | --- | --- | --- |
| GTCEu 外壳产量/次 | 2 | 1 | 1 |
| 上游/单方块产汽倍率 | 5 | 5 | 2 |
| 锅炉房产汽倍率 | 3 | 2 | 1.5 |
| 组装产出倍率 | 2 | 1 | 1 |
| 虚空产出倍率 | 4 | 2 | 1 |
| 熔炉预热成本 % | 40 | 100 | 220 |
| 熔炉每升一度 tick | 2 | 5 | 10 |
| 熔炉加工蒸汽 % | 50 | 100 | 100 |
| 粉碎产出倍率 | 2 | 1.5 | 1 |
| 满负载水垢报废小时 | 720 | 24 | 8 |

## 获取成本与阶段

表中材料为一次手工合成的直接输入；工具单列在 JSON 中，只占槽并产生耐久损耗，不作为消耗材料计数。控制器产 1，结构外壳、接口、升级槽中的电力机器和工具损耗另计，不能把控制器价格当整机造价。核心部件的条件配方已按四种模式选择；关闭难度使用 GSE Normal 的默认配方开关，外壳产量按 GTCEu 7.5.3 默认的 2 件计算，服务器修改上游配置后须另算。原料使用注册名，避免翻译歧义。

| 档位 | 核心部件 | 每次产量 | 直接材料 |
| --- | --- | --- | --- |
| 关闭难度 | bronze_component | 1 | 1×`gtceu:bronze_frame`、4×`gtceu:bronze_plate`、2×`gtceu:copper_spring` |
| 关闭难度 | industrial_steam_casing | 2 | 6×`gtceu:brass_plate`、1×`gtceu:bronze_frame` |
| 关闭难度 | steam_assembly_block | 2 | 1×`gregsteamexpansion:bronze_component`、2×`gtceu:bronze_gear`、4×`gtceu:bronze_plate` |
| 关闭难度 | steam_circuit_assembly_block | 2 | 1×`gregsteamexpansion:bronze_component`、2×`gtceu:bronze_gear`、2×`gtceu:bronze_plate`、2×`gtceu:rubber_plate` |
| 关闭难度 | steam_grinding_block | 2 | 4×`gtceu:bronze_plate`、1×`gtceu:diamond_grinding_head`、2×`gtceu:small_bronze_gear` |
| 关闭难度 | steam_mixing_block | 2 | 1×`gtceu:bronze_gear`、4×`gtceu:bronze_plate`、2×`gtceu:bronze_rotor` |
| Easy | bronze_component | 1 | 1×`gtceu:bronze_frame`、4×`gtceu:bronze_plate`、2×`gtceu:copper_spring` |
| Easy | industrial_steam_casing | 2 | 6×`gtceu:brass_plate`、1×`gtceu:bronze_frame` |
| Easy | steam_assembly_block | 2 | 1×`gregsteamexpansion:bronze_component`、2×`gtceu:bronze_gear`、4×`gtceu:bronze_plate` |
| Easy | steam_circuit_assembly_block | 2 | 1×`gregsteamexpansion:bronze_component`、2×`gtceu:bronze_gear`、2×`gtceu:bronze_plate`、2×`gtceu:rubber_plate` |
| Easy | steam_grinding_block | 2 | 4×`gtceu:bronze_plate`、1×`gtceu:diamond_grinding_head`、2×`gtceu:small_bronze_gear` |
| Easy | steam_mixing_block | 2 | 1×`gtceu:bronze_gear`、4×`gtceu:bronze_plate`、2×`gtceu:bronze_rotor` |
| Normal | bronze_component | 1 | 1×`gtceu:bronze_frame`、4×`gtceu:bronze_plate`、2×`gtceu:copper_spring` |
| Normal | industrial_steam_casing | 1 | 6×`gtceu:brass_plate`、1×`gtceu:bronze_frame` |
| Normal | steam_assembly_block | 1 | 1×`gregsteamexpansion:bronze_component`、2×`gtceu:bronze_gear`、4×`gtceu:bronze_plate` |
| Normal | steam_circuit_assembly_block | 1 | 1×`gregsteamexpansion:bronze_component`、2×`gtceu:bronze_gear`、2×`gtceu:bronze_plate`、2×`gtceu:rubber_plate` |
| Normal | steam_grinding_block | 1 | 4×`gtceu:bronze_plate`、1×`gtceu:diamond_grinding_head`、2×`gtceu:small_bronze_gear` |
| Normal | steam_mixing_block | 1 | 1×`gtceu:bronze_gear`、4×`gtceu:bronze_plate`、2×`gtceu:bronze_rotor` |
| Expert | bronze_component | 1 | 1×`gtceu:bronze_frame`、2×`gtceu:copper_spring`、4×`gtceu:double_bronze_plate` |
| Expert | industrial_steam_casing | 1 | 6×`gtceu:brass_plate`、1×`gtceu:bronze_frame` |
| Expert | steam_assembly_block | 1 | 1×`gregsteamexpansion:bronze_component`、2×`gtceu:bronze_gear`、2×`gtceu:bronze_plate`、2×`gtceu:double_bronze_plate` |
| Expert | steam_circuit_assembly_block | 1 | 1×`gregsteamexpansion:bronze_component`、2×`gtceu:bronze_gear`、2×`gtceu:double_bronze_plate`、2×`gtceu:rubber_plate` |
| Expert | steam_grinding_block | 1 | 2×`gtceu:bronze_gear`、4×`gtceu:bronze_plate`、1×`gtceu:diamond_grinding_head` |
| Expert | steam_mixing_block | 1 | 1×`gtceu:bronze_gear`、2×`gtceu:bronze_plate`、2×`gtceu:bronze_rotor`、2×`gtceu:double_bronze_plate` |

| 控制器/接口（四种模式相同） | 直接材料 |
| --- | --- |
| advanced_steam_exhaust_hatch | 1×`gregsteamexpansion:steam_exhaust_hatch`、4×`gtceu:bronze_drum`、4×`gtceu:circuits/hv` |
| boiler_room_bronze | 1×`gregsteamexpansion:hp_steam_mixed_fuel_boiler`、1×`gtceu:bronze_large_boiler`、7×`gtceu:bronze_plate` |
| boiler_room_steel | 1×`gregsteamexpansion:hp_steam_mixed_fuel_boiler`、1×`gtceu:steel_large_boiler`、7×`gtceu:steel_plate` |
| boiler_room_titanium | 1×`gregsteamexpansion:hp_steam_mixed_fuel_boiler`、1×`gtceu:titanium_large_boiler`、7×`gtceu:titanium_plate` |
| boiler_room_tungstensteel | 1×`gregsteamexpansion:hp_steam_mixed_fuel_boiler`、7×`gtceu:tungsten_steel_plate`、1×`gtceu:tungstensteel_large_boiler` |
| hp_steam_mixed_fuel_boiler | 4×`gtceu:double_steel_plate`、1×`gtceu:hp_steam_liquid_boiler`、1×`gtceu:hp_steam_solid_boiler`、2×`gtceu:steel_quadruple_fluid_pipe` |
| large_heat_storage_steam_furnace | 4×`gtceu:double_steel_plate`、2×`gtceu:hp_steam_furnace`、1×`gtceu:steam_oven`、2×`gtceu:steel_huge_fluid_pipe` |
| large_steam_assembler | 2×`gregsteamexpansion:bronze_component`、1×`gregsteamexpansion:steam_assembly_block`、2×`gtceu:bronze_gear`、4×`gtceu:bronze_plate` |
| large_steam_blast_furnace | 4×`gtceu:double_steel_plate`、1×`gtceu:primitive_blast_furnace`、4×`gtceu:steel_brick_casing` |
| large_steam_centrifuge | 2×`gregsteamexpansion:bronze_component`、1×`gregsteamexpansion:steam_centrifuge`、1×`gregsteamexpansion:steam_mixing_block`、2×`gtceu:bronze_gear`、3×`gtceu:bronze_plate` |
| large_steam_circuit_assembler | 2×`gregsteamexpansion:bronze_component`、1×`gregsteamexpansion:steam_circuit_assembly_block`、4×`gtceu:bronze_plate`、2×`gtceu:rubber_plate` |
| large_steam_crusher | 1×`gregsteamexpansion:steam_crusher`、4×`gtceu:double_steel_plate`、1×`gtceu:steel_buzz_saw_blade`、2×`gtceu:steel_gear`、1×`gtceu:steel_rotor` |
| large_steam_fluid_drill | 2×`gregsteamexpansion:bronze_component`、4×`gtceu:double_steel_plate`、2×`gtceu:steel_frame`、1×`gtceu:steel_pipe_casing` |
| large_steam_macerator | 2×`gregsteamexpansion:bronze_component`、1×`gregsteamexpansion:steam_grinding_block`、1×`gtceu:bronze_gear`、4×`gtceu:bronze_plate`、1×`gtceu:hp_steam_macerator` |
| large_steam_mixer | 4×`gregsteamexpansion:bronze_component`、1×`gregsteamexpansion:steam_mixing_block`、2×`gtceu:bronze_plate`、2×`gtceu:bronze_rotor` |
| large_steam_ore_plant | 2×`gregsteamexpansion:bronze_component`、1×`gregsteamexpansion:steam_grinding_block`、4×`gtceu:double_steel_plate`、2×`gtceu:steel_frame` |
| large_steam_ore_washer | 2×`gregsteamexpansion:bronze_component`、1×`gregsteamexpansion:steam_mixing_block`、4×`gtceu:bronze_plate`、2×`gtceu:bronze_rotor` |
| large_steam_supply_hatch | 1×`gregsteamexpansion:steam_supply_hatch`、4×`gtceu:bronze_drum`、4×`gtceu:circuits/hv` |
| large_steam_tank | 1×`gregsteamexpansion:steam_supply_hatch`、1×`gtceu:bronze_drum`、2×`gtceu:steam_machine_casing` |
| large_steam_thermal_centrifuge | 2×`gregsteamexpansion:bronze_component`、1×`gregsteamexpansion:steam_mixing_block`、2×`gtceu:bronze_firebox_casing`、4×`gtceu:bronze_plate` |
| lp_steam_mixed_fuel_boiler | 2×`gtceu:bronze_quadruple_fluid_pipe`、4×`gtceu:double_bronze_plate`、1×`gtceu:lp_steam_liquid_boiler`、1×`gtceu:lp_steam_solid_boiler` |
| steam_centrifuge | 1×`gregsteamexpansion:steam_mixing_block`、1×`gtceu:bronze_gear`、7×`gtceu:bronze_plate` |
| steam_chemical_bath | 1×`gregsteamexpansion:bronze_component`、4×`gtceu:bronze_plate`、4×`c:glass` |
| steam_compressor | 7×`gtceu:bronze_plate`、1×`gtceu:small_bronze_gear`、1×`minecraft:piston` |
| steam_crusher | 4×`gtceu:bronze_plate`、2×`gtceu:bronze_rotor`、1×`gtceu:diamond_grinding_head`、1×`gtceu:hp_steam_macerator` |
| steam_extractor | 1×`gtceu:bronze_pipe_casing`、7×`gtceu:bronze_plate`、1×`gtceu:small_bronze_gear` |
| steam_forge | 7×`gtceu:bronze_plate`、1×`gtceu:small_bronze_gear`、1×`minecraft:piston` |
| steam_supply_hatch | 1×`gtceu:bronze_drum`、2×`gtceu:bronze_normal_fluid_pipe`、6×`gtceu:bronze_plate` |

## 基础产汽、通用处理与采矿

混合锅炉满温、连续燃料和水供给，按实际 10 tick 产汽周期折算。协同模式还消耗粉料。

| 档位 | 压力 | 协同 | 蒸汽 mB/t |
| --- | --- | --- | --- |
| 关闭难度 | LP | 关 | 16 |
| 关闭难度 | LP | 开 | 24 |
| 关闭难度 | HP | 关 | 40 |
| 关闭难度 | HP | 开 | 60 |
| Easy | LP | 关 | 80 |
| Easy | LP | 开 | 120 |
| Easy | HP | 关 | 200 |
| Easy | HP | 开 | 300 |
| Normal | LP | 关 | 80 |
| Normal | LP | 开 | 120 |
| Normal | HP | 关 | 200 |
| Normal | HP | 开 | 300 |
| Expert | LP | 关 | 32 |
| Expert | LP | 开 | 48 |
| Expert | HP | 关 | 80 |
| Expert | HP | 开 | 120 |

以下通用处理机用 100 tick、32 EU/t、1 个保证产物的归一化配方比较满并行；仅在对应类型确有可接受配方时应用，配方流体和副产物另计。普通排气、不超频、100% 节流，三档没有该家族独立产出倍率；并行增加的是产能，单位蒸汽不随并行降低。

| 机器 | 并行 | 耗时 tick | 蒸汽 mB/t | 产物/t | mB 汽/件 |
| --- | --- | --- | --- | --- | --- |
| 蒸汽压缩机 | 8 | 150 | 512 | 0.0533 | 9,600 |
| 蒸汽提取机 | 8 | 150 | 512 | 0.0533 | 9,600 |
| 蒸汽锻压机 | 8 | 150 | 512 | 0.0533 | 9,600 |
| 蒸汽化学浸洗机 | 8 | 150 | 512 | 0.0533 | 9,600 |
| 蒸汽离心机 | 8 | 150 | 512 | 0.0533 | 9,600 |
| 大型蒸汽离心机 | 64 | 150 | 4,096 | 0.4267 | 9,600 |
| 大型蒸汽热力离心机 | 64 | 150 | 4,096 | 0.4267 | 9,600 |
| 大型蒸汽洗矿厂 | 64 | 150 | 4,096 | 0.4267 | 9,600 |
| 大型蒸汽研磨厂 | 64 | 150 | 4,096 | 0.4267 | 9,600 |
| 大型蒸汽搅拌机 | 16 | 150 | 1,024 | 0.1067 | 9,600 |

粉碎机另用固定 600 tick、每并行 200 mB/t。示例原配方保证产出 3 个同材质粉碎矿石，主产物乘难度倍率后按 Java 四舍五入，不能把 Normal 的 1.5 倍直接当 4.5 件。副产物不计入下表；大型机扩建并行不减少该配方的单位蒸汽。

| 档位 | 机器 | 并行 | 蒸汽 mB/t | 主产物/t | mB 汽/主产物 |
| --- | --- | --- | --- | --- | --- |
| 关闭难度 | 蒸汽粉碎机 | 8 | 1,600 | 0.04 | 40,000 |
| 关闭难度 | 大型蒸汽粉碎机 | 64 | 12,800 | 0.32 | 40,000 |
| Easy | 蒸汽粉碎机 | 8 | 1,600 | 0.08 | 20,000 |
| Easy | 大型蒸汽粉碎机 | 64 | 12,800 | 0.64 | 20,000 |
| Normal | 蒸汽粉碎机 | 8 | 1,600 | 0.0667 | 24,000 |
| Normal | 大型蒸汽粉碎机 | 64 | 12,800 | 0.5333 | 24,000 |
| Expert | 蒸汽粉碎机 | 8 | 1,600 | 0.04 | 40,000 |
| Expert | 大型蒸汽粉碎机 | 64 | 12,800 | 0.32 | 40,000 |

虚空采矿厂默认 4 工位、200 tick 周期、每工位每刻 3,000 mB 蒸汽、每抽 8 件粗矿。产物随机，以下按全部粗矿合计，普通排气、不超频；具体目标矿种产量还需乘其权重份额。

| 档位 | 蒸汽 mB/t | 粗矿/t | mB 汽/粗矿 |
| --- | --- | --- | --- |
| 关闭难度 | 12,000 | 0.16 | 75,000 |
| Easy | 12,000 | 0.64 | 18,750 |
| Normal | 12,000 | 0.32 | 37,500 |
| Expert | 12,000 | 0.16 | 75,000 |

科技顺序与依赖见[玩家成长指南](../guides/steam-progression.md)。大型供汽仓与高效排气仓均需要 HV 电路，属于后期回访升级。

## 组装并行与电路专精

示例配方：100 tick、32 EU/t、1 个保证产物；非专精、普通排气、不超频、100% 节流。空槽仅 ULV；单槽堆叠 1–4 台同种 LV–EV 电力组装机允许 2/4/8/16 并行，配方等级由该机种决定。表中 32 EU/t 示例需要至少 LV 槽位；1 并行行用于少量输入时的结算比较。实际不足满并行时按实际并行落入阶梯，不能套用满槽成本。

| 档位 | 并行 | 耗时 tick | 蒸汽 mB/t | 批次产物 | mB 汽/件 |
| --- | --- | --- | --- | --- | --- |
| 关闭难度 | 1 | 150 | 64 | 1 | 9,600 |
| 关闭难度 | 2 | 225 | 80 | 2 | 9,000 |
| 关闭难度 | 4 | 300 | 96 | 4 | 7,200 |
| 关闭难度 | 8 | 375 | 112 | 8 | 5,250 |
| 关闭难度 | 16 | 450 | 128 | 16 | 3,600 |
| Easy | 1 | 150 | 64 | 2 | 4,800 |
| Easy | 2 | 225 | 80 | 4 | 4,500 |
| Easy | 4 | 300 | 96 | 8 | 3,600 |
| Easy | 8 | 375 | 112 | 16 | 2,625 |
| Easy | 16 | 450 | 128 | 32 | 1,800 |
| Normal | 1 | 150 | 64 | 1 | 9,600 |
| Normal | 2 | 225 | 80 | 2 | 9,000 |
| Normal | 4 | 300 | 96 | 4 | 7,200 |
| Normal | 8 | 375 | 112 | 8 | 5,250 |
| Normal | 16 | 450 | 128 | 16 | 3,600 |
| Expert | 1 | 150 | 64 | 1 | 9,600 |
| Expert | 2 | 225 | 80 | 2 | 9,000 |
| Expert | 4 | 300 | 96 | 4 | 7,200 |
| Expert | 8 | 375 | 112 | 8 | 5,250 |
| Expert | 16 | 450 | 128 | 16 | 3,600 |

同档位从 1 到 16 满并行，理论产能为 `16/3 = 5.3333×`，单位蒸汽为 `2×3/16 = 0.375×`。升级槽内机器还影响电压门槛，无法仅以并行倍率评估投资。

专精仅对与样品物品及 NBT 完全相同的保证输出生效。样品不消耗；输入物品、焊料按普通批次扣取。每批一次随机，期望产出倍率为 `1 + 概率×额外倍率`，耗时向上取 `1.5×`，每刻耗汽不变。预留输出容量按最大增产计算。下表沿用上述配方，16 并行；单位比率与同档、同并行的非专精批次相比。

| 档位 | 期望产出倍率 | 期望产能比 | 单位蒸汽比 | 期望批产物 | 示例 mB 汽/件 |
| --- | --- | --- | --- | --- | --- |
| 关闭难度 | 2.5 | 1.6667 | 0.6 | 40 | 2,160 |
| Easy | 8 | 5.3333 | 0.1875 | 256 | 337.5 |
| Normal | 2.5 | 1.6667 | 0.6 | 40 | 2,160 |
| Expert | 1.25 | 0.8333 | 1.2 | 20 | 4,320 |

Expert 专精的期望产出仅 1.25×，耗时 1.5×：产能为 0.8333×，单位蒸汽为 1.2×。它节省目标电路的原料和焊料，适用于材料受限场景；蒸汽或时间受限时应慎用。Easy/Normal 专精可同时改善期望产能和单位蒸汽，但仍需覆盖最高输出容量。

## 钻井与燃料闭环

默认 2 泵位、每泵位每 200 tick 抽 2,000 mB、每泵位 3,000 mB/t。普通满速总需求 6,000 mB/t。高效排气先将每刻蒸汽向上取整到 67%；超频再将耗时向上取半、每刻耗汽乘 3。

| 档位 | 高效排气 | 超频 | 蒸汽 mB/t | 流体 mB/t | 流体 mB/小时 | mB 汽/mB 流体 |
| --- | --- | --- | --- | --- | --- | --- |
| 关闭难度 | 无 | 关 | 6,000 | 20 | 1,440,000 | 300 |
| 关闭难度 | 无 | 开 | 18,000 | 40 | 2,880,000 | 450 |
| 关闭难度 | 有 | 关 | 4,020 | 20 | 1,440,000 | 201 |
| 关闭难度 | 有 | 开 | 12,060 | 40 | 2,880,000 | 301.5 |
| Easy | 无 | 关 | 6,000 | 80 | 5,760,000 | 75 |
| Easy | 无 | 开 | 18,000 | 160 | 11,520,000 | 112.5 |
| Easy | 有 | 关 | 4,020 | 80 | 5,760,000 | 50.25 |
| Easy | 有 | 开 | 12,060 | 160 | 11,520,000 | 75.375 |
| Normal | 无 | 关 | 6,000 | 40 | 2,880,000 | 150 |
| Normal | 无 | 开 | 18,000 | 80 | 5,760,000 | 225 |
| Normal | 有 | 关 | 4,020 | 40 | 2,880,000 | 100.5 |
| Normal | 有 | 开 | 12,060 | 80 | 5,760,000 | 150.75 |
| Expert | 无 | 关 | 6,000 | 20 | 1,440,000 | 300 |
| Expert | 无 | 开 | 18,000 | 40 | 2,880,000 | 450 |
| Expert | 有 | 关 | 4,020 | 20 | 1,440,000 | 201 |
| Expert | 有 | 开 | 12,060 | 40 | 2,880,000 | 301.5 |

产物是全部油气合计，默认权重轻质原油/原油/重质原油/天然气为 25/20/15/15，目标流体期望份额为对应权重除以 75；短期可能连续抽到同一种。天然气在当前蒸汽阶段用于积累。

不能把全部产出直接折算为返供燃料。混合锅炉与锅炉房的白名单来自运行时 `steam_boiler` 流体配方，锅炉房复制其配方并将燃烧时长向下取整除以 4；上游大型锅炉、燃油发电机的油类配方并不会因此自动解锁。核验闭环时逐种检查配方查看器接受性、炼化损耗及粉料/水/鼓风，再计算 `净蒸汽 = Σ(产物量×可返供蒸汽/mB) − 钻井蒸汽 − 炼化与辅助成本`。未接受或尚不可加工的油气当期返供量计 0。本文不宣称默认钻井能够自持。

## 高炉熟练度、热风与高装料

| 档位 | 新手/熟悉/熟练/精通耗时 % | 熟悉/熟练/精通累计作业数 |
| --- | --- | --- |
| 关闭难度 | 80/70/60/50 | 192/768/1,920 |
| Easy | 75/65/55/45 | 144/576/1,440 |
| Normal | 80/70/60/50 | 192/768/1,920 |
| Expert | 90/80/70/60 | 288/1,152/2,880 |

累计作业数按完成并行累加；换配方 ID 后连贯熟练度重新起算。原始高炉配方仍消耗自己的固体燃料，大型机额外使用蒸汽与鼓风。普通满载 96 并行：19,200 mB/t 蒸汽、384 mB/t 鼓风。

高装料连续两批后复位，每批增加债务 `ceil(D×max(P−96,0)/96)`。两批和复位都消耗固定 19,200 mB/t 蒸汽、384 mB/t 鼓风；强制 100% 节流，禁用超频及热风节汽。示例原配方 100 tick、已精通、两批都满载 192 并行、无高效排气：

| 档位 | 普通批耗时 tick | 普通作业/t | 高装料含复位 tick | 高装料作业/t | 普通 mB 汽/作业 | 高装料 mB 汽/作业 |
| --- | --- | --- | --- | --- | --- | --- |
| 关闭难度 | 50 | 1.92 | 200 | 1.92 | 10,000 | 10,000 |
| Easy | 45 | 2.1333 | 180 | 2.1333 | 9,000 | 9,000 |
| Normal | 50 | 1.92 | 200 | 1.92 | 10,000 | 10,000 |
| Expert | 60 | 1.6 | 240 | 1.6 | 12,000 | 12,000 |

满装料 192 并行时，`两批生产 2D + 复位 2D = 4D`，输出 `384` 次作业，理论持续产能与普通 96 并行相同。它提供集中出货能力；第二批输入不足或配方不匹配会扩大等待成本。高效排气仍作用于生产批次；复位通过直接资源扣取，不享受该折扣，不能把 67% 套到整个周期。

热风模块普通批次回收最终普通蒸汽的 15% 为储热；储热足以覆盖整批后，该批节汽 15% 并消耗等量储热。相同配方/并行长期交替蓄热、放热且无容量溢出时，平均节汽约 7.5%，不是持续 15%。单批热需求超过储热上限 100,000,000 时无法启动整批优惠。拆坏模块清空储热；高装料期间储热冻结。

## 锅炉房预热与维护

满温、100% 节流、有效协同粉料、无产汽外挂模块。末档水垢仅表示 75%–100% 的减产阶段，到 100% 报废时不能继续产汽。满温理想升温时间从内部 0°C 算起，假设液体燃料、粉料与鼓风不断；实际升温过程的产汽随温度增长，不能全程按满温产量结算。

| 档位 | 材料级别 | 干净蒸汽 mB/t | 末档水垢蒸汽 mB/t | 理想升温秒 |
| --- | --- | --- | --- | --- |
| 关闭难度 | 青铜 | 6,000 | 3,000 | 800 |
| 关闭难度 | 钢 | 13,500 | 6,750 | 1,080 |
| 关闭难度 | 钛 | 24,000 | 12,000 | 1,280 |
| 关闭难度 | 钨钢 | 48,000 | 24,000 | 1,600 |
| Easy | 青铜 | 18,000 | 14,400 | 800 |
| Easy | 钢 | 40,500 | 32,400 | 1,080 |
| Easy | 钛 | 72,000 | 57,600 | 1,280 |
| Easy | 钨钢 | 144,000 | 115,200 | 1,600 |
| Normal | 青铜 | 12,000 | 6,000 | 800 |
| Normal | 钢 | 27,000 | 13,500 | 1,080 |
| Normal | 钛 | 48,000 | 24,000 | 1,280 |
| Normal | 钨钢 | 96,000 | 48,000 | 1,600 |
| Expert | 青铜 | 9,000 | 2,250 | 800 |
| Expert | 钢 | 20,250 | 5,062.5 | 1,080 |
| Expert | 钛 | 36,000 | 9,000 | 1,280 |
| Expert | 钨钢 | 72,000 | 18,000 | 1,600 |

水垢按干净产量相对该机器最大干净产量的负载积分累计；默认四段边界为 25%/50%/75%/100%。Easy/Normal/Expert 在理想满负载下每 180/6/2 小时增加 25 个百分点。默认每次冷酸洗消耗 8,000 mB 稀盐酸、耗时 3,200 tick（160 秒），清除 25 个百分点。仅清洗占时的理想损失为 `160/(工作秒+160)`，还需加上停炉冷却和重新升温；冷酸洗要求内部温度 ≤26°C，不能把 160 秒当整次维护总停机时间。低负载与软化模块能延后水垢，但模块辅料必须计入。

## 蓄热熔炉启动成本与摊销

尺寸按宽×高，选最小、中等、最大三种样本。计价体积为 `(宽²−4)×高`。理想首次启炉从 20°C 到最高温的 60%，每度蒸汽向上取整；下表排除冷却、补热、限流和停机。加工列是另一个满温锁定批次样例（100 tick、32 EU/t、1 并行、普通排气、不超频），按实际每刻取整后的批次总汽计价，并非把首次启炉温度当满温。

| 档位 | 尺寸 | 理想启动汽 mB | 理想启动秒 | 满温速度倍率 | 示例批次 tick | 示例 mB 汽/件 |
| --- | --- | --- | --- | --- | --- | --- |
| 关闭难度 | 7×6 | 313,200 | 145 | 3 | 34 | 6,426 |
| 关闭难度 | 11×12 | 2,471,040 | 220 | 3.6293 | 28 | 5,012 |
| 关闭难度 | 15×18 | 9,388,080 | 295 | 25 | 4 | 1,600 |
| Easy | 7×6 | 125,280 | 58 | 3 | 34 | 3,230 |
| Easy | 11×12 | 989,120 | 88 | 3.6293 | 28 | 2,520 |
| Easy | 15×18 | 3,755,940 | 118 | 25 | 4 | 800 |
| Normal | 7×6 | 313,200 | 145 | 3 | 34 | 6,426 |
| Normal | 11×12 | 2,471,040 | 220 | 3.6293 | 28 | 5,012 |
| Normal | 15×18 | 9,388,080 | 295 | 25 | 4 | 1,600 |
| Expert | 7×6 | 689,040 | 290 | 3 | 34 | 6,426 |
| Expert | 11×12 | 5,436,640 | 440 | 3.6293 | 28 | 5,012 |
| Expert | 15×18 | 20,654,720 | 590 | 25 | 4 | 1,600 |

规模越大，启动投资越高，满温速度和加工节汽越好。实际单位成本应按 `(首次预热汽+所有补热汽+加工汽)/累计有效输出` 计算，不能只取满温折扣。若把升级材料折算为蒸汽等价成本 `I`、额外启动成本 `H`，单位节省 `ΔS>0`，静态回本数量为 `ceil((I+H)/ΔS)`；若 `ΔS≤0` 则无蒸汽回本点。材料没有统一售价，本文不给虚构的固定回本分钟。

## 共用升级顺序

高效排气：名义每刻耗汽向上取整至 67%，短批次/小需求按实际取整。大型供汽仓超频：每刻×3、耗时向上取半；偶数耗时的产能约×2、单位蒸汽×1.5。同时使用高效排气与超频时名义单位蒸汽约 `0.67×1.5=1.005`，不等于节省一半。普通节流 25%/50%/75%/100% 保留批次总蒸汽，只把每刻需求转为更长耗时；它适合供汽瓶颈，不能当节汽升级。高装料复位是独立扣资源阶段，见上面的例外。

## 数据来源

数值来源文件及 SHA-256 记录在同目录 `steam-economy.json`；以下是本次模型读取的文件。

- [`gradle.properties`](../../gradle.properties)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/advanced_steam_exhaust_hatch.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/advanced_steam_exhaust_hatch.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/boiler_room_bronze.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/boiler_room_bronze.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/boiler_room_steel.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/boiler_room_steel.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/boiler_room_titanium.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/boiler_room_titanium.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/boiler_room_tungstensteel.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/boiler_room_tungstensteel.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/bronze_component_hard.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/bronze_component_hard.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/bronze_component_standard.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/bronze_component_standard.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/hp_steam_mixed_fuel_boiler.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/hp_steam_mixed_fuel_boiler.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/industrial_steam_casing_count_1.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/industrial_steam_casing_count_1.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/industrial_steam_casing_count_2.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/industrial_steam_casing_count_2.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/industrial_steam_casing_count_3.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/industrial_steam_casing_count_3.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_heat_storage_steam_furnace.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_heat_storage_steam_furnace.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_assembler.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_assembler.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_blast_furnace.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_blast_furnace.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_centrifuge.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_centrifuge.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_circuit_assembler.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_circuit_assembler.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_crusher.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_crusher.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_fluid_drill.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_fluid_drill.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_macerator.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_macerator.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_mixer.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_mixer.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_ore_plant.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_ore_plant.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_ore_washer.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_ore_washer.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_supply_hatch.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_supply_hatch.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_tank.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_tank.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_thermal_centrifuge.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/large_steam_thermal_centrifuge.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/lp_steam_mixed_fuel_boiler.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/lp_steam_mixed_fuel_boiler.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_assembly_block_hard_count_1.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_assembly_block_hard_count_1.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_assembly_block_hard_count_2.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_assembly_block_hard_count_2.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_assembly_block_hard_count_3.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_assembly_block_hard_count_3.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_assembly_block_standard_count_1.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_assembly_block_standard_count_1.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_assembly_block_standard_count_2.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_assembly_block_standard_count_2.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_assembly_block_standard_count_3.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_assembly_block_standard_count_3.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_centrifuge.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_centrifuge.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_chemical_bath.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_chemical_bath.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_circuit_assembly_block_hard_count_1.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_circuit_assembly_block_hard_count_1.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_circuit_assembly_block_hard_count_2.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_circuit_assembly_block_hard_count_2.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_circuit_assembly_block_hard_count_3.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_circuit_assembly_block_hard_count_3.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_circuit_assembly_block_standard_count_1.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_circuit_assembly_block_standard_count_1.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_circuit_assembly_block_standard_count_2.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_circuit_assembly_block_standard_count_2.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_circuit_assembly_block_standard_count_3.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_circuit_assembly_block_standard_count_3.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_compressor.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_compressor.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_crusher.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_crusher.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_extractor.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_extractor.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_forge.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_forge.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_grinding_block_hard_count_1.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_grinding_block_hard_count_1.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_grinding_block_hard_count_2.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_grinding_block_hard_count_2.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_grinding_block_hard_count_3.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_grinding_block_hard_count_3.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_grinding_block_standard_count_1.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_grinding_block_standard_count_1.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_grinding_block_standard_count_2.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_grinding_block_standard_count_2.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_grinding_block_standard_count_3.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_grinding_block_standard_count_3.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_mixing_block_hard_count_1.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_mixing_block_hard_count_1.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_mixing_block_hard_count_2.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_mixing_block_hard_count_2.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_mixing_block_hard_count_3.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_mixing_block_hard_count_3.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_mixing_block_standard_count_1.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_mixing_block_standard_count_1.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_mixing_block_standard_count_2.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_mixing_block_standard_count_2.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_mixing_block_standard_count_3.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_mixing_block_standard_count_3.json)
- [`src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_supply_hatch.json`](../../src/generated/resources/data/gregsteamexpansion/recipes/shaped/steam_supply_hatch.json)
- [`src/main/java/com/hoshino/gregsteamexpansion/data/GSERecipes.java`](../../src/main/java/com/hoshino/gregsteamexpansion/data/GSERecipes.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/difficulty/GSEDifficultyConfig.java`](../../src/main/java/com/hoshino/gregsteamexpansion/difficulty/GSEDifficultyConfig.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/difficulty/GSEDifficultyProfile.java`](../../src/main/java/com/hoshino/gregsteamexpansion/difficulty/GSEDifficultyProfile.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/difficulty/GSEDifficultyState.java`](../../src/main/java/com/hoshino/gregsteamexpansion/difficulty/GSEDifficultyState.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/BoilerRoomFuelSync.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/BoilerRoomFuelSync.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/BoilerRoomMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/BoilerRoomMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/BoilerRoomThermalLogic.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/BoilerRoomThermalLogic.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/LargeHeatStorageSteamFurnaceMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/LargeHeatStorageSteamFurnaceMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/LargeSteamOverclock.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/LargeSteamOverclock.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/SteamPartCollector.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/SteamPartCollector.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/SteamThrottle.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/SteamThrottle.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/crusher/AbstractSteamCrusherMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/crusher/AbstractSteamCrusherMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/crusher/LargeSteamCrusherMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/crusher/LargeSteamCrusherMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/crusher/SteamCrusherMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/crusher/SteamCrusherMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/furnace/FurnaceThermalLogic.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/furnace/FurnaceThermalLogic.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/part/AdvancedSteamExhaustHatchMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/part/AdvancedSteamExhaustHatchMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/part/LargeSteamSupplyHatchPartMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/part/LargeSteamSupplyHatchPartMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/part/SteamSupplyHatchPartMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/part/SteamSupplyHatchPartMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/AbstractSteamAssemblerMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/AbstractSteamAssemblerMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/AbstractSteamProcessorMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/AbstractSteamProcessorMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/LargeSteamBlastFurnaceMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/LargeSteamBlastFurnaceMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/LargeSteamCentrifugeMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/LargeSteamCentrifugeMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/LargeSteamCircuitAssemblerMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/LargeSteamCircuitAssemblerMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/LargeSteamMaceratorMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/LargeSteamMaceratorMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/LargeSteamMixerMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/LargeSteamMixerMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/LargeSteamOreWasherMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/LargeSteamOreWasherMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/LargeSteamThermalCentrifugeMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/LargeSteamThermalCentrifugeMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/SteamCentrifugeMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/SteamCentrifugeMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/SteamChemicalBathMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/SteamChemicalBathMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/SteamCompressorMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/SteamCompressorMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/SteamExtractorMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/SteamExtractorMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/SteamForgeMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/processor/SteamForgeMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/voidproducer/AbstractSteamVoidMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/voidproducer/AbstractSteamVoidMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/voidproducer/LargeSteamFluidDrillMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/voidproducer/LargeSteamFluidDrillMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/voidproducer/LargeSteamOrePlantMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/multiblock/voidproducer/LargeSteamOrePlantMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/machine/steam/MixedFuelBoilerMachine.java`](../../src/main/java/com/hoshino/gregsteamexpansion/machine/steam/MixedFuelBoilerMachine.java)
- [`src/main/java/com/hoshino/gregsteamexpansion/migration/OreCrushingMigration.java`](../../src/main/java/com/hoshino/gregsteamexpansion/migration/OreCrushingMigration.java)
