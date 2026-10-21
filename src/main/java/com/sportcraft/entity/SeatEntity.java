package com.sportcraft.entity;

import com.sportcraft.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkHooks;

import java.util.Optional;

/**
 * Invisible, non-persistent mount spawned when a player sits on a seat block.
 * It has no collision and no gravity; once its passenger dismounts (or logs
 * out), the entity removes itself on the next tick.
 */
public class SeatEntity extends Entity {

    public SeatEntity(EntityType<?> type, Level level) {
        super(type, level);
        setNoGravity(true);
        this.noPhysics = true;
    }

    /** Reuses an existing seat at the block or spawns a fresh one server-side. */
    public static SeatEntity getOrCreate(Level level, BlockPos blockPos) {
        if (level.isClientSide) {
            return null;
        }
        AABB search = new AABB(blockPos);
        Optional<SeatEntity> existing = level.getEntitiesOfClass(SeatEntity.class, search).stream().findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        SeatEntity seat = new SeatEntity(ModEntities.SEAT.get(), level);
        seat.moveTo(blockPos.getX() + 0.5D, blockPos.getY() + 0.35D, blockPos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(seat);
        return seat;
    }

    @Override
    public void tick() {
        if (!level().isClientSide && getPassengers().isEmpty()) {
            discard();
            return;
        }
        super.tick();
    }

    @Override
    protected void defineSynchedData() {
        // No synched state: the entity is a pure mount.
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // Seats are transient; nothing to read.
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // Seats are transient; nothing to write.
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRiderSit() {
        return true;
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.0D;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
