package com.sportcraft.entity;

import com.sportcraft.registry.ModEntities;
import com.sportcraft.registry.ModItems;
import com.sportcraft.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * A physics-driven soccer ball.
 *
 * <p>Physics are simulated <b>only on the server</b>; the vanilla entity tracker
 * (update interval 1 tick, velocity updates enabled) keeps clients in sync, so
 * multiplayer behaviour stays authoritative and deterministic. The movement uses
 * Minecraft's own {@link #move(MoverType, Vec3)} collision pipeline, meaning the
 * ball correctly collides with blocks, walls, slabs, fences and other entities'
 * environments without replacing the game physics.</p>
 */
public class SoccerBallEntity extends Entity {

    public static final double GRAVITY = 0.045D;
    private static final double GROUND_FRICTION = 0.80D;
    private static final double AIR_DRAG = 0.99D;
    private static final double BOUNCE_DAMPING = 0.58D;
    private static final double WALL_DAMPING = 0.60D;
    private static final int KICK_COOLDOWN_TICKS = 5;
    private static final int MAX_AGE = 12_000; // 10 minutes, then despawn
    private static final float ROLL_FACTOR = 230.0F; // degrees per block travelled

    private int age;
    private int kickCooldown;
    private int goalCooldown;
    private int lastBounceTick = -10;
    private UUID lastKicker;

    private float visualRoll;
    private float prevVisualRoll;
    private float visualYaw;

    public SoccerBallEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    public SoccerBallEntity(Level level, double x, double y, double z) {
        this(ModEntities.SOCCER_BALL.get(), level);
        setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData() {
        // All state is server-authoritative; no synched data accessors required.
    }

    @Override
    public void tick() {
        this.baseTick();

        if (this.level().isClientSide) {
            // Smooth the tracker position updates, the same way ItemEntity does.
            this.xo = this.getX();
            this.yo = this.getY();
            this.zo = this.getZ();
            this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ,
                    this.lerpYRot, this.lerpXRot);
            updateVisualRoll();
            return;
        }

        // Remember the previous position for collision checks and render interpolation.
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();

        if (kickCooldown > 0) {
            kickCooldown--;
        }
        if (goalCooldown > 0) {
            goalCooldown--;
        }

        Vec3 motion = getDeltaMovement();
        if (!isOnGround()) {
            motion = motion.add(0.0D, GRAVITY, 0.0D);
        }
        double drag = isOnGround() ? GROUND_FRICTION : AIR_DRAG;
        motion = motion.multiply(drag, 1.0D, drag);
        if (isOnGround() && Math.abs(motion.y) < 0.1D) {
            motion = new Vec3(motion.x, 0.0D, motion.z);
        }

        double preX = motion.x;
        double preY = motion.y;
        double preZ = motion.z;
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);

        Vec3 post = getDeltaMovement();

        // Vertical collision: bounce off the floor (or ceiling).
        if (verticalCollision) {
            if (preY < -0.15D) {
                setDeltaMovement(post.x, -preY * BOUNCE_DAMPING, post.z);
                playBounceSound(Math.abs(preY));
            } else {
                setDeltaMovement(post.x, 0.0D, post.z);
            }
        }

        // Horizontal collision: reflect only the axis that was actually blocked.
        if (horizontalCollision) {
            double newX = post.x;
            double newZ = post.z;
            double dx = getX() - xo;
            double dz = getZ() - zo;
            if (Math.abs(preX) > 0.05D && Math.abs(dx) < Math.abs(preX) * 0.4D) {
                newX = -preX * WALL_DAMPING;
                playBounceSound(Math.abs(preX));
            }
            if (Math.abs(preZ) > 0.05D && Math.abs(dz) < Math.abs(preZ) * 0.4D) {
                newZ = -preZ * WALL_DAMPING;
                playBounceSound(Math.abs(preZ));
            }
            setDeltaMovement(newX, getDeltaMovement().y, newZ);
        }

        // Settle the ball instead of jittering forever.
        Vec3 settled = getDeltaMovement();
        if (isOnGround() && settled.horizontalDistanceSqr() < 0.0004D) {
            setDeltaMovement(0.0D, settled.y, 0.0D);
        }

        updateVisualRoll();

        age++;
        if (age > MAX_AGE || getY() < level().getMinBuildHeight() - 16) {
            discard();
        }
    }

    private void updateVisualRoll() {
        prevVisualRoll = visualRoll;
        double dx = getX() - xo;
        double dz = getZ() - zo;
        float travelled = (float) Math.sqrt(dx * dx + dz * dz);
        visualRoll = (visualRoll + travelled * ROLL_FACTOR) % 360.0F;
        if (travelled > 1.0E-4D) {
            visualYaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        }
    }

    /**
     * Full-power kick, usually from a left click. {@code power} is expected in a
     * 0..1.5 range (attack strength, up-scaled while sprinting).
     */
    public void kick(Player player, float power) {
        if (kickCooldown > 0 || !isAlive()) {
            return;
        }
        Vec3 look = player.getLookAngle();
        double clamped = Mth.clamp(power, 0.1F, 1.5F);
        double speed = 0.55D + clamped * 1.35D;
        double upward = 0.22D + Math.max(0.0D, look.y) * 0.45D;
        setDeltaMovement(look.x * speed, upward, look.z * speed);
        applyKickState(player);
        level().playSound(null, this, ModSounds.KICK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Gentle pass from a non-sneaking right click. */
    public void softTouch(Player player) {
        if (kickCooldown > 0 || !isAlive()) {
            return;
        }
        Vec3 look = player.getLookAngle();
        setDeltaMovement(look.x * 0.75D, 0.18D, look.z * 0.75D);
        applyKickState(player);
        level().playSound(null, this, ModSounds.KICK.get(), SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    /** Throw produced by the charged ball item. */
    public void launchFrom(Player player, float power) {
        Vec3 look = player.getLookAngle();
        double speed = 0.6D + Mth.clamp(power, 0.15F, 1.0F) * 1.5D;
        setDeltaMovement(look.x * speed, look.y * speed + 0.15D, look.z * speed);
        applyKickState(player);
        level().playSound(null, this, ModSounds.KICK.get(), SoundSource.PLAYERS, 0.8F, 1.1F);
    }

    private void applyKickState(Player player) {
        lastKicker = player.getUUID();
        kickCooldown = KICK_COOLDOWN_TICKS;
        hasImpulse = true;
    }

    /** Sneak + right click: collect the ball back into the inventory. */
    public void pickUp(Player player) {
        if (level().isClientSide) {
            return;
        }
        ItemStack stack = new ItemStack(ModItems.SOCCER_BALL.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        level().playSound(null, this, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.4F);
        discard();
    }

    private void playBounceSound(double strength) {
        if (!level().isClientSide && strength > 0.18D && age - lastBounceTick > 4) {
            lastBounceTick = age;
            float pitch = (float) (0.9D + Math.min(strength, 1.0D) * 0.3D);
            level().playSound(null, this, ModSounds.BOUNCE.get(), SoundSource.NEUTRAL, 0.6F, pitch);
        }
    }

    public void resetTo(BlockPos pos) {
        moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        setDeltaMovement(Vec3.ZERO);
        hasImpulse = true;
        kickCooldown = 10;
        goalCooldown = 60;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age);
        tag.putInt("KickCooldown", kickCooldown);
        tag.putInt("GoalCooldown", goalCooldown);
        if (lastKicker != null) {
            tag.putUUID("LastKicker", lastKicker);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("Age");
        kickCooldown = tag.getInt("KickCooldown");
        goalCooldown = tag.getInt("GoalCooldown");
        if (tag.hasUUID("LastKicker")) {
            lastKicker = tag.getUUID("LastKicker");
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        // Players walk through the ball; interaction happens via clicks.
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    public int getKickCooldown() {
        return kickCooldown;
    }

    public int getGoalCooldown() {
        return goalCooldown;
    }

    public void setGoalCooldown(int ticks) {
        this.goalCooldown = ticks;
    }

    public UUID getLastKicker() {
        return lastKicker;
    }

    public float getVisualRoll(float partialTick) {
        return Mth.lerp(partialTick, prevVisualRoll, visualRoll);
    }

    public float getVisualYaw() {
        return visualYaw;
    }
}
