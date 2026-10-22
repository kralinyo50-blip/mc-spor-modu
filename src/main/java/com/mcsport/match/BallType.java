package com.mcsport.match;

import net.minecraft.util.Mth;

/**
 * Supported ball sports. Each type carries its own physics feel and texture
 * so a single entity class can represent every ball in the mod.
 */
public enum BallType {
    SOCCER(0.58D, 0.60D, 0.80D, 0.045D, "soccer"),
    BASKETBALL(0.78D, 0.68D, 0.86D, 0.050D, "basketball");

    private final double bounceDamping;
    private final double wallDamping;
    private final double groundFriction;
    private final double gravity;
    private final String textureName;

    BallType(double bounceDamping, double wallDamping, double groundFriction, double gravity, String textureName) {
        this.bounceDamping = bounceDamping;
        this.wallDamping = wallDamping;
        this.groundFriction = groundFriction;
        this.gravity = gravity;
        this.textureName = textureName;
    }

    public double getBounceDamping() {
        return bounceDamping;
    }

    public double getWallDamping() {
        return wallDamping;
    }

    public double getGroundFriction() {
        return groundFriction;
    }

    public double getGravity() {
        return gravity;
    }

    public String getTextureName() {
        return textureName;
    }

    public BallType next() {
        BallType[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public BallType previous() {
        BallType[] values = values();
        return values[Math.floorMod(ordinal() - 1, values.length)];
    }

    public static BallType byOrdinal(int ordinal) {
        BallType[] values = values();
        return values[Mth.clamp(ordinal, 0, values.length - 1)];
    }
}
