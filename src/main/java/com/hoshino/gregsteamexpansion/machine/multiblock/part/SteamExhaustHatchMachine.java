package com.hoshino.gregsteamexpansion.machine.multiblock.part;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.common.data.GTDamageTypes;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 蒸汽排气仓 / Steam Exhaust Hatch (large-heat-storage-steam-furnace.md):
 * reusable exhaust interface for compatible multiblock steam machines.
 *
 * <p>The hatch owns no timing itself: a consuming controller checks
 * {@link #isExhaustBlocked()} before drawing steam, calls
 * {@link #performExhaustFeedback()} every 20 ticks while it actually consumes
 * steam, and calls {@link #applyExhaustDamage()} whenever its own persisted
 * 200-active-tick damage cycle elapses. There is no GUI, no inventory and no
 * fluid output; obstruction uses a strict air check along the six-way front
 * facing and never clears blocks.</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SteamExhaustHatchMachine extends MultiblockPartMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            SteamExhaustHatchMachine.class, MultiblockPartMachine.MANAGED_FIELD_HOLDER);

    /** Straight channel length checked along the front facing. */
    public static final int EXHAUST_CHANNEL_LENGTH = 3;
    /** Particle and sound feedback cycle while steam is actually consumed. */
    public static final int FEEDBACK_INTERVAL_TICKS = 20;
    /** Damage cycle measured in the consumer machine's actual run ticks. */
    public static final int DAMAGE_CYCLE_TICKS = 200;
    /** Heat damage per elapsed damage cycle, matching GTCEu HP steam machines. */
    public static final float EXHAUST_DAMAGE = 12.0F;

    public SteamExhaustHatchMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /**
     * 严格空气判定: the three blocks straight ahead must all be air. Any snow
     * layer, fire, plant, non-colliding block or fluid blocks the exhaust.
     */
    public boolean isExhaustBlocked() {
        Direction facing = getFrontFacing();
        Level level = getLevel();
        if (facing == null || level == null) {
            return false;
        }
        BlockPos front = getPos();
        for (int i = 1; i <= EXHAUST_CHANNEL_LENGTH; i++) {
            if (!level.getBlockState(front.relative(facing, i)).isAir()) {
                return true;
            }
        }
        return false;
    }

    /**
     * One exhaust feedback pulse: GTCEu steam machine cloud particles plus the
     * lava-extinguish venting sound, which obeys the global GTCEu machine
     * sound toggle. Particles, obstruction checks and heat damage keep working
     * while sounds are muted.
     */
    public void performExhaustFeedback() {
        Direction facing = getFrontFacing();
        Level level = getLevel();
        if (facing == null || level == null) {
            return;
        }
        double x = getPos().getX() + 0.5 + facing.getStepX() * 0.6;
        double y = getPos().getY() + 0.5 + facing.getStepY() * 0.6;
        double z = getPos().getZ() + 0.5 + facing.getStepZ() * 0.6;
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, x, y, z,
                    7 + level.random.nextInt(3),
                    facing.getStepX() / 2.0,
                    facing.getStepY() / 2.0,
                    facing.getStepZ() / 2.0, 0.1);
            if (ConfigHolder.INSTANCE.machines.machineSounds) {
                level.playSound(null, x, y, z, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
    }

    /**
     * Deals the heat damage of one elapsed damage cycle to living entities in
     * the first block straight ahead. Creative and spectator players are
     * unaffected, mirroring GTCEu's HP steam machine venting.
     */
    public void applyExhaustDamage() {
        Direction facing = getFrontFacing();
        Level level = getLevel();
        if (facing == null || level == null) {
            return;
        }
        BlockPos front = getPos().relative(facing);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(front),
                target -> !(target instanceof Player player) || !player.isSpectator() && !player.isCreative())) {
            entity.hurt(GTDamageTypes.HEAT.source(level), EXHAUST_DAMAGE);
        }
    }
}
