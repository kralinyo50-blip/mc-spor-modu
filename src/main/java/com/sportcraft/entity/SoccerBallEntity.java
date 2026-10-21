package com.sportcraft.entity;

import com.sportcraft.match.BallType;
import com.sportcraft.registry.ModEntities;
import com.sportcraft.registry.ModItems;
import com.sportcraft.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Physics-driven ball entity. One class backs every {@link BallType}; the type
 * is a synched data value so the renderer picks the right texture and the
 * server-authoritative physics use the right damping values.
 *
 * <p>All movement runs on the server. Clients only interpolate the received
 * position and run the cosmetic rotation locally.</p>
 */
public class SoccerBallEntity extends Entity {

    private static final EntityDataAccessor<Byte> DATA_BALL_TYPE =
            SynchedEntityData.defineId(SoccerBallEntity.class, EntityDataSerializers.BYTE);

    private static final double BALL_RADIUS = 0.32D;
    private static final double RESTITUTION_MIN = 0.42D;
    private static final int KICK_COOLDOWN_TICKS = 6;

    private int kickCooldown;
    private int goalCooldown;
    private float visualYaw;
    private float visualRoll;

    public SoccerBallEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        setNoGravity(false);
    }

    public static SoccerBallEntity spawn(Level level, Vec3 position, BallType type) {
        SoccerBallEntity ball = new SoccerBallEntity(ModEntities.SOCCER_BALL.get(), level);
        ball.setPos(position.x, position.y, position.z);
        ball.setBallType(type);
        level.addFreshEntity(ball);
        return ball;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_BALL_TYPE, (byte) BallType.SOCCER.ordinal());
    }

    public BallType getBallType() {
        return BallType.byOrdinal(this.entityData.get(DATA_BALL_TYPE));
    }

    public void setBallType(BallType type) {
        this.entityData.set(DATA_BALL_TYPE, (byte) type.ordinal());
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            serverTick();
        } else {
            clientTick();
        }
    }

    private void serverTick() {
        if (kickCooldown > 0) {
            kickCooldown--;
        }
        if (goalCooldown > 0) {
            goalCooldown--;
        }
        applyKicks();
        if (this.isInWater()) {
            setDeltaMovement(getDeltaMovement().scale(0.85D).add(0.0D, 0.04D, 0.0D));
        } else {
            setDeltaMovement(getDeltaMovement().add(0.0D, -getBallType().getGravity(), 0.0D));
        }
        moveControlled();
        if (onGround()) {
            setDeltaMovement(getDeltaMovement().multiply(
                    getBallType().getGroundFriction(), 1.0D, getBallType().getGroundFriction()));
        }
        if (getDeltaMovement().lengthSqr() < 0.0008D && onGround()) {
            setDeltaMovement(Vec3.ZERO);
        }
        if (tickCount % 200 == 0 && getDeltaMovement().lengthSqr() < 0.0001D) {
            BlockPos below = blockPosition().below();
            if (!level().getBlockState(below).isAir()
                    && !level().getBlockState(below).getFluidState().isEmpty()) {
                discard();
            }
        }
    }

    private void clientTick() {
        // Record previous position before vanilla interpolation below.
        this.xo = getX();
        this.yo = getY();
        this.zo = getZ();
        double horizontalSpeed = Math.sqrt(getDeltaMovement().x * getDeltaMovement().x
                + getDeltaMovement().z * getDeltaMovement().z);
        visualRoll = (float) ((visualRoll + horizontalSpeed * 42.0D) % 360.0D);
        setPos(getX() + getDeltaMovement().x, getY() + getDeltaMovement().y,
                getZ() + getDeltaMovement().z);
    }

    public float getVisualRoll() {
        return visualRoll;
    }

    /** Renderer hook; the roll is already advanced per client tick. */
    public float getVisualRoll(float partialTick) {
        return visualRoll;
    }

    public float getVisualYaw() {
        return visualYaw;
    }

    private void applyKicks() {
        AABB searchBox = getBoundingBox().inflate(1.2D);
        for (LivingEntity entity : level().getEntitiesOfClass(LivingEntity.class, searchBox)) {
            if (entity.isSpectator() || !entity.isAlive()) {
                continue;
            }
            AABB entityBox = entity.getBoundingBox();
            if (!entityBox.intersects(getBoundingBox().inflate(0.35D))) {
                continue;
            }
            if (kickCooldown > 0 && entity instanceof Player) {
                continue;
            }
            double power = entity.isSprinting() ? 1.55D : 1.0D;
            double yawRad = Math.toRadians(entity.getYRot());
            double forwardX = -Math.sin(yawRad);
            double forwardZ = Math.cos(yawRad);
            double speed = 0.55D * power;
            Vec3 impulse = new Vec3(forwardX * speed,
                    entity.isShiftKeyDown() ? 0.12D : 0.32D * power,
                    forwardZ * speed);
            setDeltaMovement(getDeltaMovement().add(impulse));
            visualYaw = entity.getYRot();
            if (entity instanceof Player) {
                kickCooldown = KICK_COOLDOWN_TICKS;
            }
            level().playSound(null, blockPosition(), ModSounds.KICK.get(),
                    SoundSource.PLAYERS, 0.7F, 0.9F + random.nextFloat() * 0.2F);
        }
    }

    public int getKickCooldown() {
        return kickCooldown;
    }

    public int getGoalCooldown() {
        return goalCooldown;
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
        visualYaw = player.getYRot();
        kickCooldown = KICK_COOLDOWN_TICKS;
        hasImpulse = true;
        level().playSound(null, this, ModSounds.KICK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Gentle pass from a non-sneaking right click. */
    public void softTouch(Player player) {
        if (kickCooldown > 0 || !isAlive()) {
            return;
        }
        Vec3 look = player.getLookAngle();
        setDeltaMovement(look.x * 0.75D, 0.18D, look.z * 0.75D);
        visualYaw = player.getYRot();
        kickCooldown = KICK_COOLDOWN_TICKS;
        hasImpulse = true;
        level().playSound(null, this, ModSounds.KICK.get(), SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    /** Sneak + right click: collect the ball back into the inventory. */
    public void pickUp(Player player) {
        if (level().isClientSide) {
            return;
        }
        ItemStack stack = new ItemStack(getBallType() == BallType.BASKETBALL
                ? ModItems.BASKETBALL.get() : ModItems.SOCCER_BALL.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        level().playSound(null, this, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.4F);
        discard();
    }

    /** Called by the goal detector after a scored goal to move the ball out. */
    public void resetTo(BlockPos pos) {
        moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        setDeltaMovement(Vec3.ZERO);
        hasImpulse = true;
        kickCooldown = 10;
        goalCooldown = 60;
    }

    /** CollisionGetter#getCollisions returns an Iterable in 1.20.1; wrap it as a Stream. */
    private Stream<VoxelShape> collisionStream(AABB swept) {
        return StreamSupport.stream(level().getCollisions(this, swept).spliterator(), false);
    }

    private void moveControlled() {
        Vec3 movement = getDeltaMovement();
        AABB box = getBoundingBox();

        // Vertical collision: floor and ceiling.
        if (movement.y <= 0.0D) {
            AABB swept = box.expandTowards(0.0D, movement.y, 0.0D);
            double floor = collisionStream(swept)
                    .map(shape -> shape.bounds().minY).filter(y -> y <= box.minY)
                    .max(Double::compare).orElse(Double.NEGATIVE_INFINITY);
            if (floor != Double.NEGATIVE_INFINITY) {
                setPos(getX(), floor + BALL_RADIUS, getZ());
                if (Math.abs(movement.y) > RESTITUTION_MIN) {
                    setDeltaMovement(getDeltaMovement().x,
                            -movement.y * getBallType().getBounceDamping(), getDeltaMovement().z);
                    spawnBounceEffects(floor);
                } else {
                    setDeltaMovement(getDeltaMovement().x, 0.0D, getDeltaMovement().z);
                }
                this.setOnGround(true);
                movement = getDeltaMovement();
                box = getBoundingBox();
            } else {
                this.setOnGround(false);
            }
        } else {
            AABB swept = box.expandTowards(0.0D, movement.y, 0.0D);
            double ceiling = collisionStream(swept)
                    .map(shape -> shape.bounds().maxY).filter(y -> y >= box.maxY)
                    .min(Double::compare).orElse(Double.POSITIVE_INFINITY);
            if (ceiling != Double.POSITIVE_INFINITY) {
                setPos(getX(), ceiling - BALL_RADIUS, getZ());
                setDeltaMovement(getDeltaMovement().x,
                        -movement.y * getBallType().getBounceDamping(), getDeltaMovement().z);
                movement = getDeltaMovement();
                box = getBoundingBox();
            }
        }

        // Horizontal collision: walls (and goal posts behave like walls here).
        double[] horizontal = resolveHorizontal(box, movement);
        setPos(getX() + horizontal[0], getY(), getZ() + horizontal[1]);
    }

    private double[] resolveHorizontal(AABB box, Vec3 movement) {
        double moveX = movement.x;
        double moveZ = movement.z;
        if (moveX != 0.0D) {
            AABB swept = box.expandTowards(moveX, 0.0D, 0.0D);
            double limit = moveX > 0.0D
                    ? collisionStream(swept).map(shape -> shape.bounds().maxX)
                            .filter(x -> x >= box.maxX).min(Double::compare).orElse(Double.POSITIVE_INFINITY)
                    : collisionStream(swept).map(shape -> shape.bounds().minX)
                            .filter(x -> x <= box.minX).max(Double::compare).orElse(Double.NEGATIVE_INFINITY);
            if (moveX > 0.0D && limit != Double.POSITIVE_INFINITY) {
                moveX = limit - box.maxX - 0.001D;
                setDeltaMovement(-getDeltaMovement().x * getBallType().getWallDamping(),
                        getDeltaMovement().y, getDeltaMovement().z);
            } else if (moveX < 0.0D && limit != Double.NEGATIVE_INFINITY) {
                moveX = limit - box.minX + 0.001D;
                setDeltaMovement(-getDeltaMovement().x * getBallType().getWallDamping(),
                        getDeltaMovement().y, getDeltaMovement().z);
            }
        }
        if (moveZ != 0.0D) {
            box = getBoundingBox();
            AABB swept = box.expandTowards(0.0D, 0.0D, moveZ);
            double limit = moveZ > 0.0D
                    ? collisionStream(swept).map(shape -> shape.bounds().maxZ)
                            .filter(z -> z >= box.maxZ).min(Double::compare).orElse(Double.POSITIVE_INFINITY)
                    : collisionStream(swept).map(shape -> shape.bounds().minZ)
                            .filter(z -> z <= box.minZ).max(Double::compare).orElse(Double.NEGATIVE_INFINITY);
            if (moveZ > 0.0D && limit != Double.POSITIVE_INFINITY) {
                moveZ = limit - box.maxZ - 0.001D;
                setDeltaMovement(getDeltaMovement().x, getDeltaMovement().y,
                        -getDeltaMovement().z * getBallType().getWallDamping());
            } else if (moveZ < 0.0D && limit != Double.NEGATIVE_INFINITY) {
                moveZ = limit - box.minZ + 0.001D;
                setDeltaMovement(getDeltaMovement().x, getDeltaMovement().y,
                        -getDeltaMovement().z * getBallType().getWallDamping());
            }
        }
        return new double[]{moveX, moveZ};
    }

    private void spawnBounceEffects(double floorY) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState below = level().getBlockState(BlockPos.containing(getX(), floorY - 0.1D, getZ()));
        if (below.isAir()) {
            return;
        }
        serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below),
                getX(), floorY + 0.05D, getZ(), 6, 0.15D, 0.05D, 0.15D, 0.02D);
        level().playSound(null, blockPosition(), ModSounds.BOUNCE.get(),
                SoundSource.NEUTRAL, 0.6F, 0.9F + random.nextFloat() * 0.2F);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putByte("BallType", (byte) getBallType().ordinal());
        tag.putFloat("Roll", visualRoll);
        tag.putInt("KickCooldown", kickCooldown);
        tag.putInt("GoalCooldown", goalCooldown);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setBallType(BallType.byOrdinal(tag.contains("BallType") ? tag.getByte("BallType") : 0));
        visualRoll = tag.getFloat("Roll");
        kickCooldown = tag.getInt("KickCooldown");
        goalCooldown = tag.getInt("GoalCooldown");
    }

    @Override
    protected AABB makeBoundingBox() {
        return new AABB(
                getX() - BALL_RADIUS, getY() - BALL_RADIUS, getZ() - BALL_RADIUS,
                getX() + BALL_RADIUS, getY() + BALL_RADIUS, getZ() + BALL_RADIUS);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !isRemoved();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
