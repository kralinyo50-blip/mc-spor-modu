package com.sportcraft.entity;

import com.sportcraft.entity.ai.NpcPlayGoal;
import com.sportcraft.entity.ai.RefereeGoal;
import com.sportcraft.match.Team;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * AI sports player. Bots are spawned by the match manager to fill teams up
 * to the configured team size and are driven entirely by {@link NpcPlayGoal}.
 * A third role, the referee, is the same entity with {@link #DATA_REFEREE}
 * set; it runs {@link RefereeGoal} instead of the team brain.
 *
 * <p>They are invulnerable, persistent while alive and never saved with the
 * world (the match lifecycle owns their spawn/despawn). Three synched action
 * values drive the custom limb animations on every client:
 * tackle (slide) timer, dribble phase and referee hand signal.</p>
 */
public class SoccerNpcEntity extends PathfinderMob {

    public static final int TACKLE_DURATION_TICKS = 12;
    public static final int DRIBBLE_KEEPALIVE_TICKS = 6;
    public static final int SIGNAL_DURATION_TICKS = 30;

    private static final EntityDataAccessor<Byte> DATA_TEAM =
            SynchedEntityData.defineId(SoccerNpcEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_KEEPER =
            SynchedEntityData.defineId(SoccerNpcEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_INDEX =
            SynchedEntityData.defineId(SoccerNpcEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_REFEREE =
            SynchedEntityData.defineId(SoccerNpcEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_TACKLE =
            SynchedEntityData.defineId(SoccerNpcEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_DRIBBLE =
            SynchedEntityData.defineId(SoccerNpcEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_SIGNAL =
            SynchedEntityData.defineId(SoccerNpcEntity.class, EntityDataSerializers.BYTE);

    public SoccerNpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_TEAM, (byte) Team.RED.ordinal());
        this.entityData.define(DATA_KEEPER, (byte) 0);
        this.entityData.define(DATA_INDEX, (byte) 0);
        this.entityData.define(DATA_REFEREE, (byte) 0);
        this.entityData.define(DATA_TACKLE, (byte) 0);
        this.entityData.define(DATA_DRIBBLE, (byte) 0);
        this.entityData.define(DATA_SIGNAL, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        // Both goals are always registered; their canUse gates select the
        // active brain based on the role assigned after the entity is created.
        this.goalSelector.addGoal(1, new NpcPlayGoal(this));
        this.goalSelector.addGoal(2, new RefereeGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            // Server owns the countdowns; synched data pushes them to clients.
            if (getTackleTicks() > 0) {
                setTackleTicks((byte) (getTackleTicks() - 1));
            }
            if (getDribbleTicks() > 0) {
                setDribbleTicks((byte) (getDribbleTicks() - 1));
            }
            if (getSignalTicks() > 0) {
                setSignalTicks((byte) (getSignalTicks() - 1));
            }
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    public Team getSportTeam() {
        return Team.byOrdinal(this.entityData.get(DATA_TEAM));
    }

    public void setSportTeam(Team team) {
        this.entityData.set(DATA_TEAM, (byte) team.ordinal());
    }

    public boolean isKeeper() {
        return this.entityData.get(DATA_KEEPER) == 1;
    }

    public void setKeeper(boolean keeper) {
        this.entityData.set(DATA_KEEPER, (byte) (keeper ? 1 : 0));
    }

    public int getSquadIndex() {
        return this.entityData.get(DATA_INDEX);
    }

    public void setSquadIndex(int index) {
        this.entityData.set(DATA_INDEX, (byte) Math.max(0, Math.min(127, index)));
    }

    public boolean isReferee() {
        return this.entityData.get(DATA_REFEREE) == 1;
    }

    public void setReferee(boolean referee) {
        this.entityData.set(DATA_REFEREE, (byte) (referee ? 1 : 0));
    }

    public byte getTackleTicks() {
        return this.entityData.get(DATA_TACKLE);
    }

    public boolean isSliding() {
        return getTackleTicks() > 0;
    }

    public void startTackle() {
        setTackleTicks((byte) TACKLE_DURATION_TICKS);
    }

    private void setTackleTicks(byte ticks) {
        this.entityData.set(DATA_TACKLE, ticks);
    }

    public byte getDribbleTicks() {
        return this.entityData.get(DATA_DRIBBLE);
    }

    public boolean isDribbling() {
        return getDribbleTicks() > 0;
    }

    /** Called by the basketball brain every tick while the bot controls the ball. */
    public void markDribbling() {
        setDribbleTicks((byte) DRIBBLE_KEEPALIVE_TICKS);
    }

    private void setDribbleTicks(byte ticks) {
        this.entityData.set(DATA_DRIBBLE, ticks);
    }

    public byte getSignalTicks() {
        return this.entityData.get(DATA_SIGNAL);
    }

    /** Referee-only: raises the arm for a short decision animation. */
    public void raiseSignal() {
        setSignalTicks((byte) SIGNAL_DURATION_TICKS);
    }

    private void setSignalTicks(byte ticks) {
        this.entityData.set(DATA_SIGNAL, ticks);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Match participants cannot be injured or killed by players or the world.
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        // Bots are recreated by the match manager; never persist them in the save.
        return false;
    }

    @Override
    public boolean canBeLeashed(net.minecraft.world.entity.player.Player player) {
        return false;
    }

    // Mob hiyerarşisinde bu iki NBT metodu public'tir; Entity tabanındaki
    // protected imzayı zayıflatmamak için aynı erişim belirleyiciyi koruyoruz.
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        // Transient entity; nothing to restore.
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        // Transient entity; nothing to store.
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
