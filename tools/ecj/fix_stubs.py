#!/usr/bin/env python3
"""ECJ-driven stub fixer.

Each run parses ECJ diagnostics over the copied mod sources and mutates the
generated stubs under ~/.ecj-harness/stubs so that type-checking converges:
shells for unresolved types, nested-class/annotation handling, generics,
methods/constructors/fields with context-inferred (widened) signatures,
static constants and overridable supertype members.
"""
import glob
import os
import re
import subprocess
from collections import defaultdict

HARNESS = os.path.expanduser("~/.ecj-harness")
JAVA = os.path.join(HARNESS, "jdk4py-extract/jdk4py/java-runtime/bin/java")
ECJ = os.path.join(HARNESS, "ecj.jar")
MOD = os.path.join(HARNESS, "modcopy")
STUBS = os.path.join(HARNESS, "stubs")
OUT = os.path.join(HARNESS, "out")

PRIMITIVES = {"byte", "short", "int", "long", "float", "double", "boolean", "char"}

# ---------------------------------------------------------------- knowledge --

SUPERCLASS = {
    "net.minecraft.world.entity.Entity": None,
    "net.minecraft.world.entity.LivingEntity": "net.minecraft.world.entity.Entity",
    "net.minecraft.world.entity.Mob": "net.minecraft.world.entity.LivingEntity",
    "net.minecraft.world.entity.PathfinderMob": "net.minecraft.world.entity.Mob",
    "net.minecraft.world.entity.AgeableMob": "net.minecraft.world.entity.PathfinderMob",
    "net.minecraft.world.entity.player.Player": "net.minecraft.world.entity.LivingEntity",
    "net.minecraft.server.level.ServerPlayer":
        "net.minecraft.world.entity.player.Player",
    "net.minecraft.world.level.Level": None,
    "net.minecraft.server.level.ServerLevel": "net.minecraft.world.level.Level",
    "net.minecraft.world.item.Item": None,
    "net.minecraft.world.item.BlockItem": "net.minecraft.world.item.Item",
    "net.minecraft.world.level.block.state.BlockBehaviour": None,
    "net.minecraft.world.level.block.Block":
        "net.minecraft.world.level.block.state.BlockBehaviour",
    "net.minecraft.world.level.block.HorizontalDirectionalBlock":
        "net.minecraft.world.level.block.Block",
    "net.minecraft.world.level.block.RotatedPillarBlock":
        "net.minecraft.world.level.block.Block",
    "net.minecraft.client.model.EntityModel": None,
    "net.minecraft.client.model.HumanoidModel":
        "net.minecraft.client.model.EntityModel",
    "net.minecraft.client.renderer.entity.EntityRenderer": None,
    "net.minecraft.client.renderer.entity.MobRenderer":
        "net.minecraft.client.renderer.entity.EntityRenderer",
    "net.minecraft.client.renderer.entity.LivingEntityRenderer":
        "net.minecraft.client.renderer.entity.EntityRenderer",
    "net.minecraft.client.gui.screens.Screen": None,
    "net.minecraft.world.entity.ai.goal.Goal": None,
    "net.minecraft.network.chat.Component": None,
    "net.minecraft.network.chat.MutableComponent":
        "net.minecraft.network.chat.Component",
    "net.minecraft.world.level.block.CarpetBlock":
        "net.minecraft.world.level.block.Block",
    "net.minecraft.client.renderer.entity.HumanoidMobRenderer":
        "net.minecraft.client.renderer.entity.MobRenderer",
    "com.mojang.brigadier.exceptions.CommandSyntaxException":
        "java.lang.Exception",
    "net.minecraft.world.level.block.state.BlockBehaviour": None,
}

# Verbatim member declarations replacing any inferred same-name member. Keyed
# by simple class name; applied to every matching stub file (nested or top).
CURATED_DECLS = {
    "Entity": [
        "protected void defineSynchedData() {}",
        "protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag a0) {}",
        "protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag a0) {}",
        "protected net.minecraft.world.phys.AABB makeBoundingBox() { return null; }",
        "public boolean hasImpulse;",
    ],
    "Mob": [
        "protected void registerGoals() {}",
    ],
    "PathfinderMob": [
        "public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() { return null; }",
        "protected void registerGoals() {}",
        "protected void defineSynchedData() {}",
    ],
    "HorizontalDirectionalBlock": [
        "protected void createBlockStateDefinition("
        "net.minecraft.world.level.block.state.StateDefinition.Builder"
        "<net.minecraft.world.level.block.Block, net.minecraft.world.level.block.state.BlockState> a0) {}",
    ],
    "BlockBehaviour": [
        "protected net.minecraft.world.level.block.state.StateDefinition stateDefinition;",
        "protected void createBlockStateDefinition("
        "net.minecraft.world.level.block.state.StateDefinition.Builder"
        "<net.minecraft.world.level.block.Block, net.minecraft.world.level.block.state.BlockState> a0) {}",
        "public <T extends net.minecraft.world.level.block.entity.BlockEntity> "
        "net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker("
        "net.minecraft.world.level.Level a0, net.minecraft.world.level.block.state.BlockState a1, "
        "net.minecraft.world.level.block.entity.BlockEntityType<T> a2) { return null; }",
        "public net.minecraft.world.InteractionResult use("
        "net.minecraft.world.level.block.state.BlockState a0, net.minecraft.world.level.Level a1, "
        "net.minecraft.core.BlockPos a2, net.minecraft.world.entity.player.Player a3, "
        "net.minecraft.world.InteractionHand a4, net.minecraft.world.phys.BlockHitResult a5) { return null; }",
    ],
    "BlockEntity": [
        "public void serverTick(net.minecraft.world.level.Level a0) {}",
    ],
    "EntityModel": [
        "public void setupAnim(T0 a0, float a1, float a2, float a3, float a4, float a5) {}",
        "public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack a0, "
        "com.mojang.blaze3d.vertex.VertexConsumer a1, int a2, int a3, "
        "float a4, float a5, float a6, float a7) {}",
    ],
    "EntityRenderer": [
        "public net.minecraft.resources.ResourceLocation getTextureLocation(T0 a0) { return null; }",
        "public void render(T0 a0, float a1, float a2, "
        "com.mojang.blaze3d.vertex.PoseStack a3, "
        "net.minecraft.client.renderer.MultiBufferSource a4, int a5) {}",
    ],
    "Screen": [
        "protected void init() {}",
        "public boolean active;",
    ],
    "Block": [
        "public <T extends net.minecraft.world.level.block.entity.BlockEntity> "
        "net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker("
        "net.minecraft.world.level.Level a0, net.minecraft.world.level.block.state.BlockState a1, "
        "net.minecraft.world.level.block.entity.BlockEntityType<T> a2) { return null; }",
        "protected void createBlockStateDefinition("
        "net.minecraft.world.level.block.state.StateDefinition.Builder"
        "<net.minecraft.world.level.block.Block, net.minecraft.world.level.block.state.BlockState> a0) {}",
    ],
    "HumanoidModel": [
        "public void setupAnim(T0 a0, float a1, float a2, float a3, float a4, float a5) {}",
    ],
    "MobRenderer": [
        "public net.minecraft.resources.ResourceLocation getTextureLocation(T0 a0) { return null; }",
    ],
    "HumanoidMobRenderer": [
        "public net.minecraft.resources.ResourceLocation getTextureLocation(T0 a0) { return null; }",
    ],
    "EntityBlock": [
        "public <T extends net.minecraft.world.level.block.entity.BlockEntity> "
        "net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker("
        "net.minecraft.world.level.Level a0, net.minecraft.world.level.block.state.BlockState a1, "
        "net.minecraft.world.level.block.entity.BlockEntityType<T> a2) { return null; }",
    ],
}

KNOWN_FQN = {
    "Block": "net.minecraft.world.level.block.Block",
    "Blocks": "net.minecraft.world.level.block.Blocks",
    "BlockStateProperties":
        "net.minecraft.world.level.block.state.properties.BlockStateProperties",
    "BlockBehaviour": "net.minecraft.world.level.block.state.BlockBehaviour",
    "Properties": "net.minecraft.world.level.block.state.BlockBehaviour$Properties",
    "ItemProperties": "net.minecraft.world.item.Item$Properties",
    "Rotation": "net.minecraft.world.level.block.Rotation",
    "Mirror": "net.minecraft.world.level.block.Mirror",
    "SoundType": "net.minecraft.world.level.block.SoundType",
    "RenderShape": "net.minecraft.world.level.block.RenderShape",
    "Material": "net.minecraft.world.level.material.Material",
    "MapColor": "net.minecraft.world.level.material.MapColor",
    "Level": "net.minecraft.world.level.Level",
    "BlockGetter": "net.minecraft.world.level.BlockGetter",
    "ServerLevel": "net.minecraft.server.level.ServerLevel",
    "BlockPos": "net.minecraft.core.BlockPos",
    "Direction": "net.minecraft.core.Direction",
    "Vec3": "net.minecraft.world.phys.Vec3",
    "AABB": "net.minecraft.world.phys.AABB",
    "Entity": "net.minecraft.world.entity.Entity",
    "LivingEntity": "net.minecraft.world.entity.LivingEntity",
    "Mob": "net.minecraft.world.entity.Mob",
    "PathfinderMob": "net.minecraft.world.entity.PathfinderMob",
    "Player": "net.minecraft.world.entity.player.Player",
    "ServerPlayer": "net.minecraft.server.level.ServerPlayer",
    "InteractionHand": "net.minecraft.world.InteractionHand",
    "InteractionResult": "net.minecraft.world.InteractionResult",
    "InteractionResultHolder": "net.minecraft.world.InteractionResultHolder",
    "ItemStack": "net.minecraft.world.item.ItemStack",
    "Items": "net.minecraft.world.item.Items",
    "Item": "net.minecraft.world.item.Item",
    "CreativeModeTab": "net.minecraft.world.item.CreativeModeTab",
    "EntityType": "net.minecraft.world.entity.EntityType",
    "DamageSource": "net.minecraft.world.damagesource.DamageSource",
    "CompoundTag": "net.minecraft.nbt.CompoundTag",
    "Tag": "net.minecraft.nbt.Tag",
    "ResourceLocation": "net.minecraft.resources.ResourceLocation",
    "Component": "net.minecraft.network.chat.Component",
    "ChatFormatting": "net.minecraft.network.chat.ChatFormatting",
    "FriendlyByteBuf": "net.minecraft.network.FriendlyByteBuf",
    "Packet": "net.minecraft.network.protocol.Packet",
    "ClientGamePacketListener":
        "net.minecraft.network.protocol.game.ClientGamePacketListener",
    "Minecraft": "net.minecraft.client.Minecraft",
    "GuiGraphics": "net.minecraft.client.gui.GuiGraphics",
    "Screen": "net.minecraft.client.gui.screens.Screen",
    "Button": "net.minecraft.client.gui.components.Button",
    "Font": "net.minecraft.client.gui.Font",
    "PoseStack": "com.mojang.blaze3d.vertex.PoseStack",
    "VertexConsumer": "com.mojang.blaze3d.vertex.VertexConsumer",
    "MultiBufferSource": "net.minecraft.client.renderer.MultiBufferSource",
    "RenderType": "net.minecraft.client.renderer.RenderType",
    "EntityModel": "net.minecraft.client.model.EntityModel",
    "HumanoidModel": "net.minecraft.client.model.HumanoidModel",
    "ModelPart": "net.minecraft.client.model.geom.ModelPart",
    "LayerDefinition": "net.minecraft.client.model.geom.LayerDefinition",
    "CollisionContext": "net.minecraft.world.phys.shapes.CollisionContext",
    "VoxelShape": "net.minecraft.world.phys.shapes.VoxelShape",
    "Shapes": "net.minecraft.world.phys.shapes.Shapes",
    "BlockState": "net.minecraft.world.level.block.state.BlockState",
    "BlockPlaceContext": "net.minecraft.world.item.context.BlockPlaceContext",
    "BlockEntity": "net.minecraft.world.level.block.entity.BlockEntity",
    "BlockEntityType": "net.minecraft.world.level.block.entity.BlockEntityType",
    "BlockEntityTicker": "net.minecraft.world.level.block.entity.BlockEntityTicker",
    "EntityDataAccessor": "net.minecraft.network.syncher.EntityDataAccessor",
    "EntityDataSerializers": "net.minecraft.network.syncher.EntityDataSerializers",
    "SynchedEntityData": "net.minecraft.network.syncher.SynchedEntityData",
    "Goal": "net.minecraft.world.entity.ai.goal.Goal",
    "PathNavigation": "net.minecraft.world.entity.ai.navigation.PathNavigation",
    "RandomSource": "net.minecraft.util.RandomSource",
    "SavedData": "net.minecraft.world.level.saveddata.SavedData",
    "Commands": "net.minecraft.commands.Commands",
    "CommandSourceStack": "net.minecraft.commands.CommandSourceStack",
    "CommandDispatcher": "com.mojang.brigadier.CommandDispatcher",
    "CommandSyntaxException": "com.mojang.brigadier.exceptions.CommandSyntaxException",
    "UUID": "java.util.UUID",
    # Synthetic marker: erased RegistryObject.get() result that any chained call
    # can be typed against inside the stubs.
    "RegistryValue": "net.minecraftforge.registries.RegistryValue",
}

JDK_FQN = {
    "List": "java.util.List", "Map": "java.util.Map", "Set": "java.util.Set",
    "UUID": "java.util.UUID", "Consumer": "java.util.function.Consumer",
    "Supplier": "java.util.function.Supplier", "Function": "java.util.function.Function",
    "Predicate": "java.util.function.Predicate",
}

RETURN_HINTS = {
    "getX": "double", "getY": "double", "getZ": "double",
    "readByte": "byte", "readInt": "int", "readLong": "long",
    "readShort": "short", "readFloat": "float", "readDouble": "double",
    "readBoolean": "boolean", "readUtf": "String",
    "writeByte": "void", "writeInt": "void", "writeLong": "void",
    "writeShort": "void", "writeFloat": "void", "writeDouble": "void",
    "writeBoolean": "void", "writeUtf": "void",
    "isClientSide": "boolean", "isEmpty": "boolean", "isAlive": "boolean",
    "isRemoved": "boolean", "isSpectator": "boolean", "isCreative": "boolean",
    "level": "net.minecraft.world.level.Level",
    "position": "net.minecraft.world.phys.Vec3",
    "getDeltaMovement": "net.minecraft.world.phys.Vec3",
    "blockPosition": "net.minecraft.core.BlockPos",
    "getNavigation": "net.minecraft.world.entity.ai.navigation.PathNavigation",
    "getRandom": "net.minecraft.util.RandomSource",
    "getBoundingBox": "net.minecraft.world.phys.AABB",
    "getYRot": "float", "getXRot": "float", "getId": "int", "tickCount": "int",
    "defaultBlockState": "net.minecraft.world.level.block.state.BlockState",
}

# Authoritative return types keyed by owner.method; beats inference/tightening.
METHOD_RETURN = {
    "getLogger": "org.slf4j.Logger",
    "FMLJavaModLoadingContext.get": "net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext",
    "getModEventBus": "net.minecraftforge.eventbus.api.IEventBus",
    "box": "net.minecraft.world.phys.shapes.VoxelShape",
    "getBlockState": "net.minecraft.world.level.block.state.BlockState",
    "getBlock": "net.minecraft.world.level.block.Block",
    "inflate": "net.minecraft.world.phys.AABB",
    "getEntitiesOfClass": "java.util.List",
    "getEntity": "net.minecraft.world.entity.Entity",
    "getPlayerOrException": "net.minecraft.server.level.ServerPlayer",
    "getServer": "net.minecraft.server.MinecraftServer",
    "getLevel": "net.minecraft.world.level.Level",
    "Direction.getAxis": "net.minecraft.core.Direction.Axis",
    "Vec3.lengthSqr": "double",
    "Vec3.length": "double",
    "Vec3.horizontalDistanceSqr": "double",
    "Vec3.scale": "net.minecraft.world.phys.Vec3",
    "Vec3.atBottomCenterOf": "net.minecraft.world.phys.Vec3",
    "ItemStack.getItem": "net.minecraft.world.item.Item",
    "Component.empty": "net.minecraft.network.chat.MutableComponent",
    "Component.literal": "net.minecraft.network.chat.MutableComponent",
    "Component.append": "net.minecraft.network.chat.MutableComponent",
    "Entity.position": "net.minecraft.world.phys.Vec3",
    "Entity.getEyePosition": "net.minecraft.world.phys.Vec3",
    "LivingEntity.isUsingItem": "boolean",
    "LivingEntity.getUseItemRemainingTicks": "int",
    "ItemStack.getUseDuration": "int",
    "ItemStack.shrink": "void",
    "Goal.setFlags": "void",
    "Screen.hasShiftDown": "boolean",
    "Mob.getLookControl": "net.minecraft.world.entity.ai.control.LookControl",
    "PathfinderMob.getLookControl": "net.minecraft.world.entity.ai.control.LookControl",
    "MutableComponent.append": "net.minecraft.network.chat.MutableComponent",
    "CompoundTag.getByte": "byte",
    "CompoundTag.getInt": "int",
    "CompoundTag.getShort": "short",
    "CompoundTag.getLong": "long",
    "CompoundTag.getFloat": "float",
    "CompoundTag.getDouble": "double",
    "CompoundTag.getBoolean": "boolean",
    "CompoundTag.getString": "java.lang.String",
    "CompoundTag.getCompound": "net.minecraft.nbt.CompoundTag",
    "CompoundTag.getList": "net.minecraft.nbt.ListTag",
    "Vec3.subtract": "net.minecraft.world.phys.Vec3",
    "Vec3.add": "net.minecraft.world.phys.Vec3",
    "Vec3.horizontalDistance": "double",
    "AABB.expandTowards": "net.minecraft.world.phys.AABB",
    "Entity.getBoundingBox": "net.minecraft.world.phys.AABB",
    "Entity.getLookAngle": "net.minecraft.world.phys.Vec3",
    "Entity.getX": "double", "Entity.getY": "double", "Entity.getZ": "double",
    "Entity.getEyeY": "double",
    "Entity.level": "net.minecraft.world.level.Level",
    "BlockState.getFluidState": "net.minecraft.world.level.material.FluidState",
    "FluidState.isEmpty": "boolean",
    "LivingEntity.getUseItem": "net.minecraft.world.item.ItemStack",
    "Player.getInventory": "net.minecraft.world.entity.player.Inventory",
    "Player.getAbilities": "net.minecraft.world.entity.player.Abilities",
    "Inventory.add": "boolean",
    "RandomSource.nextFloat": "float",
    "RandomSource.nextDouble": "double",
    "RandomSource.nextBoolean": "boolean",
    "RandomSource.nextInt": "int",
    "VoxelShape.bounds": "net.minecraft.world.phys.AABB",
    "StateDefinition.any": "net.minecraft.world.level.block.state.BlockState",
    "Direction.getOpposite": "net.minecraft.core.Direction",
    "Mth.sin": "float", "Mth.cos": "float",
    "MinecraftServer.getTickCount": "int",
    "MinecraftServer.getPlayerList": "net.minecraft.server.players.PlayerList",
    "MinecraftServer.overworld": "net.minecraft.server.level.ServerLevel",
    "MinecraftServer.getLevel": "net.minecraft.server.level.ServerLevel",
    "PlayerList.getPlayers": "java.util.List",
    "PlayerList.getPlayer": "net.minecraft.server.level.ServerPlayer",
    "ServerLevel.getDataStorage":
        "net.minecraft.world.level.storage.DimensionDataStorage",
    "Level.getCollisions": "java.lang.Iterable",
    "Entity.getPassengers": "java.util.List",
    "Component.translatable": "net.minecraft.network.chat.MutableComponent",
    "BlockPlaceContext.getHorizontalDirection": "net.minecraft.core.Direction",
    "ListTag.size": "int",
    "ListTag.getCompound": "net.minecraft.nbt.CompoundTag",
    "CommandContext.getSource":
        "net.minecraft.commands.CommandSourceStack",
    "CommandSourceStack.hasPermission": "boolean",
    "success": "net.minecraft.world.InteractionResultHolder",
    "consume": "net.minecraft.world.InteractionResultHolder",
    "createTickerHelper": "net.minecraft.world.level.block.entity.BlockEntityTicker",
    "registerEntityRenderer": "void",
    "enqueueWork": "void",
    "displayClientMessage": "void",
    "sendSystemMessage": "void",
    "getLookAngle": "net.minecraft.world.phys.Vec3",
    "getItemInHand": "net.minecraft.world.item.ItemStack",
    "startUsingItem": "void",
    "getUseItem": "net.minecraft.world.item.ItemStack",
    "getEyeY": "double",
    "sendToServer": "void",
    "setScreen": "void",
    "registerAboveAll": "void",
    "atBottomCenterOf": "net.minecraft.core.BlockPos",
    "getStepX": "int",
    "getStepZ": "int",
    "entityCutoutNoCull": "net.minecraft.client.renderer.RenderType",
    "getEntitySpawningPacket": "net.minecraft.network.protocol.Packet",
    "getAddEntityPacket": "net.minecraft.network.protocol.Packet",
    "noCollision": "boolean",
    "getOpposite": "net.minecraft.core.Direction",
    "getClockWise": "net.minecraft.core.Direction",
    "getCounterClockWise": "net.minecraft.core.Direction",
    "normal": "net.minecraft.core.Direction",
    "getRegistry": "java.lang.Object",
    "PoseStack.last": "com.mojang.blaze3d.vertex.PoseStack$Pose",
    "BlockPos.getX": "int", "BlockPos.getY": "int", "BlockPos.getZ": "int",
}

# Members forced onto specific stub classes every normalization pass.
CURATED_METHODS = {
    "DeferredRegister": [
        "void register(java.lang.Object a0)",
        "net.minecraftforge.registries.RegistryObject register(java.lang.Object a0, java.util.function.Supplier a1)",
        "static net.minecraftforge.registries.DeferredRegister create(java.lang.Object a0, java.lang.String a1)"],
    "Button": ["static net.minecraft.client.gui.components.Button.Builder builder("
               "java.lang.Object a0, net.minecraft.client.gui.components.Button.OnPress a1)"],
    "Component": [
        "static net.minecraft.network.chat.MutableComponent translatable("
        "String a0, java.lang.Object... rest)",
        "static net.minecraft.network.chat.MutableComponent empty()",
        "static net.minecraft.network.chat.MutableComponent literal(java.lang.String a0)",
        "net.minecraft.network.chat.MutableComponent append(java.lang.String a0)",
        "net.minecraft.network.chat.MutableComponent append(net.minecraft.network.chat.Component a0)"],
    "MutableComponent": [
        "net.minecraft.network.chat.MutableComponent withStyle(java.lang.Object a0)",
        "net.minecraft.network.chat.MutableComponent withStyle(java.lang.Object a0, java.lang.Object a1)"],
    "Vec3": ["float dot(net.minecraft.world.phys.Vec3 a0)"],
    "CommandSourceStack": ["void sendSuccess(java.util.function.Supplier a0, boolean a1)"],
    "OptionInstance": ["java.lang.String get()"],
    "FMLCommonSetupEvent": ["void enqueueWork(java.lang.Runnable a0)"],
    "FMLClientSetupEvent": ["void enqueueWork(java.lang.Runnable a0)"],
    "Goal": ["void setFlags(java.util.EnumSet a0)"],
    "Mob": [
        "net.minecraft.world.entity.ai.control.LookControl getLookControl()"],
    "LookControl": [
        "void setLookAt(double a0, double a1, double a2)",
        "void setLookAt(net.minecraft.world.entity.Entity a0, float a1, float a2)"],
    "Direction": ["net.minecraft.core.Direction getOpposite()"],
    "Rotation": [
        "net.minecraft.world.level.block.state.BlockState rotate(net.minecraft.world.level.block.state.BlockState a0)"],
    "Mirror": [
        "static net.minecraft.world.level.block.Rotation getRotation(net.minecraft.core.Direction a0)"],
    "LookControl": [
        "void setLookAt(double a0, double a1, double a2)",
        "void setLookAt(net.minecraft.world.entity.Entity a0, float a1, float a2)"],
    "MinecraftServer": [
        "java.util.List<net.minecraft.server.level.ServerPlayer> getPlayers()"],
    "PlayerList": [
        "java.util.List<net.minecraft.server.level.ServerPlayer> getPlayers()",
        "net.minecraft.server.level.ServerPlayer getPlayer(java.util.UUID a0)"],
    "DistExecutor": [
        "static void unsafeRunWhenOn(java.lang.Object a0, java.util.function.Supplier a1)"],
    "PathfinderMob": [
        "static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createMobAttributes()"],
    "LivingEntityRenderer": ["void addLayer(java.lang.Object a0)"],
    "HumanoidMobRenderer": ["void addLayer(java.lang.Object a0)"],
    "Event": ["void setCanceled(boolean a0)"],
    "GuiGraphics": [
        "void drawCenteredString(net.minecraft.client.gui.Font a0, "
        "net.minecraft.network.chat.Component a1, int a2, int a3, int a4)",
        "void fill(int a0, int a1, int a2, int a3, int a4)"],
    "DimensionDataStorage": [],
    "VertexConsumer": [
        "net.minecraft.client.renderer.VertexConsumer vertex(java.lang.Object a0, float a1, float a2, float a3)",
        "net.minecraft.client.renderer.VertexConsumer color(float a0, float a1, float a2, float a3)",
        "net.minecraft.client.renderer.VertexConsumer uv(float a0, float a1)",
        "net.minecraft.client.renderer.VertexConsumer overlayCoords(int a0)",
        "net.minecraft.client.renderer.VertexConsumer uv2(int a0)",
        "net.minecraft.client.renderer.VertexConsumer normal(java.lang.Object a0, float a1, float a2, float a3)",
        "void endVertex()"],
    "Commands": [
        "static net.minecraft.commands.Commands.LiteralArgumentBuilder literal(java.lang.String a0)",
        "static net.minecraft.commands.Commands.RequiredArgumentBuilder argument(java.lang.String a0, java.lang.Object a1)"],
    "LivingEntity": ["int getUseItemRemainingTicks()"],
    "Player": ["int getUseItemRemainingTicks()"],
    "ItemStack": ["int getUseDuration()"],
    "StringArgumentType": [
        "static java.lang.String getString(net.minecraft.commands.CommandContext a0, java.lang.String a1)"],
    "IntegerArgumentType": [
        "static java.lang.Object integer(int a0, int a1)",
        "static int getInteger(net.minecraft.commands.CommandContext a0, java.lang.String a1)"],
    "SharedSuggestionProvider": ["static java.lang.Object suggest(java.lang.Object a0, java.lang.Object a1)"],
    "GoalSelector": ["void addGoal(int a0, net.minecraft.world.entity.ai.goal.Goal a1)"],
    "Holder": ["java.lang.Object get()"],
    "SimpleChannel": [
        "void send(java.lang.Object a0, java.lang.Object a1)"],
    "CreativeModeTab": [
        "static net.minecraft.world.item.CreativeModeTab.Builder builder(java.lang.Object a0, int a1)"],
    "PacketDistributor": [
        "net.minecraftforge.network.PacketDistributor.PacketTarget with(java.util.function.Supplier a0)"],
    "RegisterGuiOverlaysEvent": [
        "void registerAboveAll(java.lang.String a0, java.lang.Object a1)"],
    "RegisterKeyMappingsEvent": ["void register(java.lang.Object a0)"],
    "RegisterCommandsEvent": [
        "com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> getDispatcher()"],
    "CommandDispatcher": [
        "T0 register(java.lang.Object a0)",
        "java.lang.Object register(java.lang.String a0, java.lang.Object a1)"],
    "AttackEntityEvent": [
        "net.minecraft.world.entity.Entity getTarget()",
        "net.minecraft.world.entity.player.Player getEntity()",
        "void setCanceled(boolean a0)"],
}


# Materialized in normalize: functional one-SAM interfaces, typed interface
# methods and functional-argument constructors.
def materialize_curated_interfaces():
    for fqn, sam in FUNCTIONAL_FQN.items():
        ensure_shell(fqn, "interface")
        name = fqn.rpartition("$")[2] if "$" in fqn else fqn.rpartition(".")[2]
        outer = fqn.split("$")[0]
        if "$" in fqn:
            outer_path = fqn_to_path(outer)
            s = open(outer_path).read()
            # Replace any plain nested class of the same name to avoid duplicates.
            s = re.sub(r"    public static class " + re.escape(name) + r"\s*\{\s*\}", "", s)
            s = re.sub(r"    public interface " + re.escape(name)
                       + r"(?:<[^>]*>)?\s*\{[^}]*\}", "", s)
            decl = (f"    public interface {name} {{\n        {sam};\n    }}\n")
            idx = s.rfind("}")
            open(outer_path, "w").write(s[:idx] + decl + s[idx:])
        else:
            pkg, _, _ = fqn.rpartition(".")
            open(fqn_to_path(fqn), "w").write(
                f"package {pkg};\n\npublic interface {name} {{\n    {sam};\n}}\n")
    for fqn, decls in CURATED_INTERFACE_METHODS.items():
        ensure_shell(fqn, "interface")
        pkg, _, name = fqn.rpartition(".")
        if isinstance(decls, str):
            decls = [decls]
        body = "\n".join(f"    {d};" for d in decls)
        open(fqn_to_path(fqn), "w").write(
            f"package {pkg};\n\npublic interface {name} {{\n{body}\n}}\n")
    for fqn, ctor in CURATED_CTORS.items():
        path = ensure_shell(fqn)
        s = open(path).read()
        simple = fqn.rpartition(".")[2]
        params = ctor[ctor.find("("):]
        ptype = params[1:].split()[0]
        if ptype not in s:
            inject = f"\n    public {simple} {params} {{}}\n"
            # Subclasses (e.g. HumanoidModel) need an implicit super() ctor.
            if not re.search(r"public\s+" + simple + r"\s*\(\s*\)", s):
                inject += f"    protected {simple}() {{}}\n"
            idx = s.find("{")
            s = s[:idx + 1] + inject + s[idx + 1:]
            open(path, "w").write(s)
    for fqn, members in CURATED_NESTED_METHODS.items():
        outer, _, nested = fqn.rpartition("$")
        ensure_shell(fqn)
        outer_path = fqn_to_path(outer)
        s = open(outer_path).read()
        bounds = find_nested_bounds(s, nested)
        if not bounds:
            continue
        body = s[bounds[0]:bounds[1]]
        additions = []
        for m in members:
            is_static = m.startswith("static ")
            decl = m[len("static "):] if is_static else m
            mname = decl.split("(")[0].split()[-1]
            if re.search(r"\b" + re.escape(mname) + r"\s*\(", body):
                # Replace inferred same-name nested methods with the curated one.
                s = re.sub(r"[ \t]*public (?:static )?[\w.$<>\[\], ?]*\b"
                           + re.escape(mname) + r"\s*\([^)]*\)\s*\{[^}]*\}\n?",
                           "", s)
                bounds = find_nested_bounds(s, nested)
                body = s[bounds[0]:bounds[1]]
            prefix = "        public static " if is_static else "        public "
            if decl.startswith("void"):
                additions.append(f"{prefix}{decl} {{}}")
            else:
                additions.append(f"{prefix}{decl} {{ return null; }}")
        if additions:
            s = (s[:bounds[1]] + "\n" + "\n".join(additions) + "\n    "
                 + s[bounds[1]:])
            open(outer_path, "w").write(s)


CURATED_FIELDS = {
    "ModelPart": [("float", "xRot"), ("float", "yRot"), ("float", "zRot"),
                  ("float", "x"), ("float", "y"), ("float", "z")],
    "Entity": [("net.minecraft.util.RandomSource", "random")],
    "Minecraft": [("net.minecraft.client.gui.Font", "font"),
                  ("net.minecraft.client.Options", "options")],
    # languageCode is the public 1.20.1 OptionInstance<String> holding the
    # selected locale code (e.g. "tr_tr"); read by ClientLanguageSync.
    "Options": [("boolean", "hideGui"),
                ("net.minecraft.client.OptionInstance", "languageCode")],
    "AABB": [("double", "minX"), ("double", "minY"), ("double", "minZ"),
             ("double", "maxX"), ("double", "maxY"), ("double", "maxZ")],
    "Level": [("boolean", "isClientSide")],
    "Mob": [("net.minecraft.world.entity.ai.goal.GoalSelector", "goalSelector")],
    "Abilities": [("boolean", "instabuild")],
    "Screen": [("net.minecraft.client.gui.Font", "font"),
               ("net.minecraft.network.chat.Component", "title")],
    "Button": [("boolean", "active")],
    "HumanoidModel": [("net.minecraft.client.model.geom.ModelPart", "rightArm"),
                      ("net.minecraft.client.model.geom.ModelPart", "leftArm"),
                      ("net.minecraft.client.model.geom.ModelPart", "rightLeg"),
                      ("net.minecraft.client.model.geom.ModelPart", "leftLeg"),
                      ("net.minecraft.client.model.geom.ModelPart", "head"),
                      ("net.minecraft.client.model.geom.ModelPart", "body"),
                      ("net.minecraft.client.model.geom.ModelPart", "hat")],
}

# Static fields enforced on particular holder classes (owner -> [(type, name)]).
CURATED_STATIC_FIELDS = {
    "FMLEnvironment": [("net.minecraftforge.api.distmarker.Dist", "dist")],
    "PacketDistributor": [("net.minecraftforge.network.PacketDistributor", "PLAYER")],
    "Tag": [("int", "TAG_COMPOUND")],
    "MinecraftForge": [
        ("net.minecraftforge.eventbus.api.IEventBus", "EVENT_BUS")],
    "OverlayTexture": [("int", "NO_OVERLAY")],
}

# Functional interfaces (FQN -> full abstract-method declaration) used as
# lambda or method-reference targets. Fixed-arity signatures must match the
# lambda parameter counts; varargs is kept only where every call site uses a
# single-arg lambda (e.g. Button.OnPress).
FUNCTIONAL_FQN = {
    "net.minecraftforge.client.gui.overlay.IGuiOverlay":
        "void render(net.minecraft.client.Minecraft a0, "
        "net.minecraft.client.gui.GuiGraphics a1, float a2, int a3, int a4)",
    "net.minecraft.world.level.block.entity.BlockEntityTicker":
        "void tick(net.minecraft.world.level.Level a0, net.minecraft.core.BlockPos a1, "
        "net.minecraft.world.level.block.state.BlockState a2, T a3)",
    "net.minecraft.client.gui.components.Button$OnPress":
        "void onPress(java.lang.Object... args)",
    "net.minecraft.world.entity.EntityType$EntityFactory":
        "T create(net.minecraft.world.entity.EntityType<T> a0, net.minecraft.world.level.Level a1)",
    "net.minecraft.world.level.block.entity.BlockEntityType$BlockEntitySupplier":
        "T create(net.minecraft.core.BlockPos a0, net.minecraft.world.level.block.state.BlockState a1)",
    "net.minecraft.client.renderer.entity.EntityRendererProvider":
        "java.lang.Object create(net.minecraft.client.renderer.entity.EntityRendererProvider.Context a0)",
    "net.minecraftforge.network.simple.SimpleChannel$Encoder":
        "void encode(T a0, net.minecraft.network.FriendlyByteBuf a1)",
    "net.minecraftforge.network.simple.SimpleChannel$Decoder":
        "T decode(net.minecraft.network.FriendlyByteBuf a0)",
    "net.minecraftforge.network.simple.SimpleChannel$Handler":
        "void handle(T a0, java.util.function.Supplier a1)",
    "net.minecraft.world.item.CreativeModeTab$DisplayItemsGenerator":
        "void accept(java.lang.Object a0, net.minecraft.world.item.CreativeModeTab.Output a1)",
    "net.minecraft.commands.Commands$SourcePredicate":
        "boolean test(net.minecraft.commands.CommandSourceStack a0)",
    "net.minecraft.commands.Commands$CommandAction":
        "int run(net.minecraft.commands.CommandContext a0) "
        "throws com.mojang.brigadier.exceptions.CommandSyntaxException",
    "net.minecraft.commands.Commands$SuggestionAction":
        "java.lang.Object suggest(net.minecraft.commands.CommandContext a0, java.lang.Object a1)",
}

# Classes whose curated constructor must not be replaced by the all-Object
# varargs ctor (the functional-typed parameter is what makes call sites type-check).
NO_VARARGS_CTOR = {
    "net.minecraft.client.model.EntityModel",
    "net.minecraft.client.model.HumanoidModel",
}

# Nested annotation types with their members (Outer$Nested -> [(type, name, default)]).
CURATED_NESTED_ANNOTATIONS = {
    "net.minecraftforge.fml.common.Mod$EventBusSubscriber": [
        ("java.lang.String", "modid", '""'),
        ("net.minecraftforge.api.distmarker.Dist[]", "value", "{}"),
        ("net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus", "bus",
         "net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE"),
    ],
}

# Fields inserted into a particular nested class (Outer$Nested -> [(type, name)]).
CURATED_NESTED_FIELDS = {
    "net.minecraftforge.event.TickEvent$ServerTickEvent": [
        ("net.minecraftforge.event.TickEvent.Phase", "phase")],
    "net.minecraftforge.event.TickEvent$ClientTickEvent": [
        ("net.minecraftforge.event.TickEvent.Phase", "phase")],
}

# Interface types with explicit method declarations (string or list).
CURATED_INTERFACE_METHODS = {
    "net.minecraftforge.eventbus.api.IEventBus": [
        "<T> void addListener(java.util.function.Consumer<T> a0)",
        "void register(java.lang.Class a0)"],
}

# Constructors whose argument is a functional interface / specific type that the
# all-Object varargs ctor cannot express.
CURATED_CTORS = {
    "net.minecraft.client.model.EntityModel":
        "net.minecraft.client.model.EntityModel(java.util.function.Function a0)",
    "net.minecraft.client.model.HumanoidModel":
        "net.minecraft.client.model.HumanoidModel(net.minecraft.client.model.geom.ModelPart a0)",
}

# Methods injected into a nested class (keyed by Outer$Nested).
BP_PROPS = "net.minecraft.world.level.block.state.BlockBehaviour.Properties"
ITEM_PROPS = "net.minecraft.world.item.Item.Properties"
CURATED_NESTED_METHODS = {
    "net.minecraftforge.network.NetworkEvent$Context": [
        "void enqueueWork(java.lang.Runnable a0)",
        "void setPacketHandled(boolean a0)",
        "net.minecraft.server.level.ServerPlayer getSender()",
    ],
    "net.minecraft.world.level.block.state.BlockBehaviour$Properties": [
        f"static {BP_PROPS} of()", f"static {BP_PROPS} of(java.lang.Object a0)",
        f"{BP_PROPS} mapColor(java.lang.Object a0)",
        f"{BP_PROPS} noOcclusion()",
        f"{BP_PROPS} sound(java.lang.Object a0)",
        f"{BP_PROPS} strength(float a0)",
        f"{BP_PROPS} strength(float a0, float a1)",
        f"{BP_PROPS} lightLevel(java.util.function.ToIntFunction a0)",
        f"{BP_PROPS} requiresCorrectToolForDrops()",
        f"{BP_PROPS} build()",
    ],
    "net.minecraft.world.item.Item$Properties": [
        f"{ITEM_PROPS} stacksTo(int a0)",
        f"{ITEM_PROPS} build()",
    ],
    "net.minecraftforge.event.TickEvent$ServerTickEvent": [
        "net.minecraft.server.MinecraftServer getServer()",
    ],
    "net.minecraft.client.renderer.entity.EntityRendererProvider$Context": [
        "net.minecraft.client.model.geom.ModelPart bakeLayer(java.lang.Object a0)",
    ],
    "net.minecraft.world.entity.EntityType$Builder": [
        "static <T> net.minecraft.world.entity.EntityType.Builder<T> of("
        "net.minecraft.world.entity.EntityType.EntityFactory<T> a0, java.lang.Object a1)",
        "net.minecraft.world.entity.EntityType<T> build(java.lang.Object a0)",
        "net.minecraft.world.entity.EntityType<T> build()",
        "net.minecraft.world.entity.EntityType.Builder<T> sized(float a0, float a1)",
        "net.minecraft.world.entity.EntityType.Builder<T> clientTrackingRange(int a0)",
        "net.minecraft.world.entity.EntityType.Builder<T> updateInterval(int a0)",
        "net.minecraft.world.entity.EntityType.Builder<T> setShouldReceiveVelocityUpdates(boolean a0)",
    ],
    "net.minecraft.world.level.block.entity.BlockEntityType$Builder": [
        "static <T> net.minecraft.world.level.block.entity.BlockEntityType.Builder<T> of("
        "net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier<T> a0, "
        "java.lang.Object... rest)",
        "net.minecraft.world.level.block.entity.BlockEntityType<T> build(java.lang.Object a0)",
    ],
    "net.minecraft.client.gui.components.Button$Builder": [
        "net.minecraft.client.gui.components.Button.Builder bounds(int a0, int a1, int a2, int a3)",
        "net.minecraft.client.gui.components.Button build()",
        "net.minecraft.client.gui.components.Button.Builder pos(int a0, int a1)",
        "net.minecraft.client.gui.components.Button.Builder size(int a0, int a1)",
    ],
    "net.minecraft.world.level.block.state.StateDefinition$Builder": [
        "net.minecraft.world.level.block.state.StateDefinition.Builder add(java.lang.Object... rest)",
    ],
    "net.minecraft.world.entity.ai.attributes.AttributeSupplier$Builder": [
        "net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder add(java.lang.Object a0, double a1)",
        "net.minecraft.world.entity.ai.attributes.AttributeSupplier build()",
    ],
    "net.minecraft.world.item.CreativeModeTab$Builder": [
        "net.minecraft.world.item.CreativeModeTab.Builder title(net.minecraft.network.chat.Component a0)",
        "net.minecraft.world.item.CreativeModeTab.Builder icon(java.util.function.Supplier a0)",
        "net.minecraft.world.item.CreativeModeTab.Builder displayItems(net.minecraft.world.item.CreativeModeTab.DisplayItemsGenerator a0)",
        "net.minecraft.world.item.CreativeModeTab build()",
    ],
    "net.minecraftforge.client.event.EntityRenderersEvent$RegisterRenderers": [
        "void registerEntityRenderer(java.lang.Object a0, "
        "net.minecraft.client.renderer.entity.EntityRendererProvider a1)",
    ],
    "net.minecraftforge.event.entity.player.PlayerInteractEvent$EntityInteract": [
        "net.minecraft.world.entity.Entity getTarget()",
        "net.minecraft.world.InteractionHand getHand()",
        "net.minecraft.world.entity.player.Player getEntity()",
        "void setCanceled(boolean a0)",
    ],
    "net.minecraftforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent": [
        "net.minecraft.world.entity.player.Player getEntity()",
    ],
    "net.minecraft.client.renderer.entity.EntityRendererProvider$Context": [
        "net.minecraft.client.model.geom.ModelPart bakeLayer(java.lang.Object a0)",
        "java.lang.Object getModelManager()",
    ],
    "com.mojang.blaze3d.vertex.PoseStack$Pose": [
        "java.lang.Object pose()",
        "java.lang.Object normal()",
    ],
    "net.minecraftforge.network.NetworkRegistry$ChannelBuilder": [
        "static net.minecraftforge.network.NetworkRegistry.ChannelBuilder named(net.minecraft.resources.ResourceLocation a0)",
        "net.minecraftforge.network.NetworkRegistry.ChannelBuilder networkProtocolVersion(java.util.function.Supplier a0)",
        "net.minecraftforge.network.NetworkRegistry.ChannelBuilder clientAcceptedVersions(java.util.function.Predicate a0)",
        "net.minecraftforge.network.NetworkRegistry.ChannelBuilder serverAcceptedVersions(java.util.function.Predicate a0)",
        "net.minecraftforge.network.simple.SimpleChannel simpleChannel()",
    ],
    "net.minecraftforge.network.simple.SimpleChannel$MessageBuilder": [
        "net.minecraftforge.network.simple.SimpleChannel.MessageBuilder<T0> encoder(net.minecraftforge.network.simple.SimpleChannel.Encoder<T0> a0)",
        "net.minecraftforge.network.simple.SimpleChannel.MessageBuilder<T0> decoder(net.minecraftforge.network.simple.SimpleChannel.Decoder<T0> a0)",
        "net.minecraftforge.network.simple.SimpleChannel.MessageBuilder<T0> consumerMainThread(net.minecraftforge.network.simple.SimpleChannel.Handler<T0> a0)",
        "net.minecraftforge.network.simple.SimpleChannel add()",
    ],
    "net.minecraftforge.network.PacketDistributor$PacketTarget": [],
    "net.minecraft.commands.Commands$LiteralArgumentBuilder": [
        "net.minecraft.commands.Commands.LiteralArgumentBuilder requires(net.minecraft.commands.Commands.SourcePredicate a0)",
        "net.minecraft.commands.Commands.LiteralArgumentBuilder executes(net.minecraft.commands.Commands.CommandAction a0)",
        "net.minecraft.commands.Commands.LiteralArgumentBuilder then(java.lang.Object a0)",
        "net.minecraft.commands.Commands.LiteralArgumentBuilder suggests(net.minecraft.commands.Commands.SuggestionAction a0)"],
    "net.minecraft.commands.Commands$RequiredArgumentBuilder": [
        "net.minecraft.commands.Commands.RequiredArgumentBuilder requires(net.minecraft.commands.Commands.SourcePredicate a0)",
        "net.minecraft.commands.Commands.RequiredArgumentBuilder executes(net.minecraft.commands.Commands.CommandAction a0)",
        "net.minecraft.commands.Commands.RequiredArgumentBuilder then(java.lang.Object a0)",
        "net.minecraft.commands.Commands.RequiredArgumentBuilder suggests(net.minecraft.commands.Commands.SuggestionAction a0)"],
    "net.minecraft.world.item.CreativeModeTab$DisplayItemsGenerator": [],
    "net.minecraft.world.item.CreativeModeTab$Output": [
        "void accept(net.minecraft.world.item.Item a0)",
        "void accept(net.minecraft.world.level.block.Block a0)"],
}

# Nested types that must exist inside particular outer stubs.
CURATED_NESTED = {
    "com.mojang.blaze3d.vertex.PoseStack": ["Pose"],
    "net.minecraft.world.level.block.state.BlockBehaviour": ["Properties"],
    "net.minecraft.world.item.Item": ["Properties"],
    "net.minecraft.client.gui.components.Button": ["Builder"],
    "net.minecraftforge.network.NetworkEvent": ["Context"],
    "net.minecraft.client.renderer.entity.EntityRendererProvider": ["Context"],
    "net.minecraftforge.event.TickEvent": ["ServerTickEvent", "ClientTickEvent"],
    "net.minecraft.world.entity.EntityType": ["Builder"],
    "net.minecraft.world.level.block.entity.BlockEntityType": ["Builder"],
    "net.minecraft.world.level.block.state.StateDefinition": ["Builder"],
    "net.minecraft.world.entity.ai.attributes.AttributeSupplier": ["Builder"],
    "net.minecraft.world.item.CreativeModeTab": [
        "Builder", "DisplayItemsGenerator", "Output"],
    "net.minecraftforge.event.entity.player.PlayerInteractEvent": ["EntityInteract"],
    "net.minecraftforge.event.entity.player.PlayerEvent": ["PlayerLoggedInEvent"],
    "net.minecraftforge.client.event.EntityRenderersEvent": ["RegisterRenderers"],
    "net.minecraftforge.network.NetworkRegistry": ["ChannelBuilder"],
    "net.minecraftforge.network.simple.SimpleChannel": ["MessageBuilder",
        "Encoder", "Decoder", "Handler"],
    "net.minecraftforge.network.PacketDistributor": ["PacketTarget"],
    "net.minecraft.commands.Commands": [
        "LiteralArgumentBuilder", "RequiredArgumentBuilder",
        "SourcePredicate", "CommandAction", "SuggestionAction"],
}

# Nested enum types with the constants the mod references (EnumSet.of needs a
# real enum, a plain class with static fields is not enough).
CURATED_NESTED_ENUMS = {
    "net.minecraft.core.Direction$Axis": ["X", "Y", "Z"],
    "net.minecraft.world.entity.ai.goal.Goal$Flag": ["MOVE", "JUMP", "LOOK", "TARGET"],
    "net.minecraftforge.event.TickEvent$Phase": ["START", "END"],
    "com.mojang.blaze3d.platform.InputConstants$Type": ["KEYSYM", "SCANCODE", "MOUSE"],
    "net.minecraftforge.fml.common.Mod$EventBusSubscriber$Bus": ["MOD", "FORGE"],
    "net.minecraft.world.item.CreativeModeTab$Row": ["TOP", "BOTTOM"],
}

# Top-level enum shells (fqn -> constants), e.g. annotation attribute types.
CURATED_TOP_ENUMS = {
    "net.minecraftforge.api.distmarker.Dist": ["CLIENT", "DEDICATED_SERVER"],
    "net.minecraft.ChatFormatting": [
        "BLACK", "DARK_BLUE", "DARK_GREEN", "DARK_AQUA", "DARK_RED",
        "DARK_PURPLE", "GOLD", "GRAY", "DARK_GRAY", "BLUE", "GREEN",
        "AQUA", "RED", "LIGHT_PURPLE", "YELLOW", "WHITE", "OBFUSCATED",
        "BOLD", "STRIKETHROUGH", "UNDERLINE", "ITALIC", "RESET"],
}

# Type parameters added to class/interface declarations (fqn -> "<T>").
CURATED_GENERICS = {
    "net.minecraft.world.level.block.entity.BlockEntityTicker": "<T>",
    "net.minecraft.world.level.block.state.StateDefinition$Builder": "<B, S>",
    "net.minecraft.world.entity.EntityType$Builder": "<T>",
    "net.minecraft.world.level.block.entity.BlockEntityType$Builder": "<T>",
    "net.minecraft.world.entity.EntityType$EntityFactory": "<T>",
    "net.minecraft.world.level.block.entity.BlockEntityType$BlockEntitySupplier": "<T>",
    "net.minecraft.core.RegistryObject": "<T0>",
    "net.minecraftforge.network.simple.SimpleChannel$MessageBuilder": "<T0>",
    "net.minecraftforge.network.simple.SimpleChannel$Encoder": "<T>",
    "net.minecraftforge.network.simple.SimpleChannel$Decoder": "<T>",
    "net.minecraftforge.network.simple.SimpleChannel$Handler": "<T>",
    "net.minecraft.client.renderer.entity.EntityRenderer": "<T0>",
    "net.minecraft.client.renderer.entity.MobRenderer": "<T0>",
    "net.minecraft.client.renderer.entity.LivingEntityRenderer": "<T0, T1>",
    "net.minecraft.client.renderer.entity.HumanoidMobRenderer": "<T0, T1>",
}

# Whole-method replacements for generic inference (owner simple -> [declarations]).
CURATED_GENERIC_METHODS = {
    "Screen": ["public <T> T addRenderableWidget(T a0) { return null; }",
               "public <T> T addRenderableOnly(T a0) { return null; }"],
    "SynchedEntityData": [
        "public <T> T get(net.minecraft.network.syncher.EntityDataAccessor<T> a0) { return null; }",
        "public <T> void set(net.minecraft.network.syncher.EntityDataAccessor<T> a0, T a1) {}",
        "public <T> void define(net.minecraft.network.syncher.EntityDataAccessor<T> a0, T a1) {}",
        "public static <T> net.minecraft.network.syncher.EntityDataAccessor<T> defineId("
        "java.lang.Object a0, java.lang.Object a1) { return null; }"],
    "BlockState": [
        "public <T> T getValue(java.lang.Object a0) { return null; }"],
    "RegistryObject": ["public T0 get() { return null; }"],
    "SimpleChannel": [
        "public <MSG> net.minecraftforge.network.simple.SimpleChannel.MessageBuilder<MSG> "
        "messageBuilder(java.lang.Class<MSG> a0, int a1) { return null; }"],
    "EntityType": [
        "public T0 create(net.minecraft.world.level.Level a0) { return null; }"],
    "DistExecutor": [
        "public static void unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist a0, "
        "java.util.function.Supplier<java.lang.Runnable> a1) {}"],
    "DimensionDataStorage": [
        "public <T> T computeIfAbsent("
        "java.util.function.Function<net.minecraft.nbt.CompoundTag, T> a0, "
        "java.util.function.Supplier<T> a1, java.lang.String a2) { return null; }"],
}

SIMPLE_TO_FQN = {}

# ------------------------------------------------------------- type helpers --

def jtype(t):
    if t is None:
        return "java.lang.Object"
    t = re.sub(r"<.*>", "", t.strip().replace(" ", ""))
    if t in PRIMITIVES or t == "void":
        return t
    if t == "String":
        return "java.lang.String"
    if t.startswith(("net.", "com.mojang.", "java.", "javax.", "org.")):
        return t
    if t in KNOWN_FQN:
        return KNOWN_FQN[t]
    if t in JDK_FQN:
        return JDK_FQN[t]
    return "java.lang.Object"


def default_val(t):
    if t == "void":
        return ""
    if t == "boolean":
        return "false"
    if t in ("byte", "short", "int", "char"):
        return "0"
    if t == "long":
        return "0L"
    if t == "float":
        return "0.0F"
    if t == "double":
        return "0.0"
    return "null"


def build_params(args, typed=False):
    if not args.strip():
        return ""
    out = []
    for i, p in enumerate(args.split(",")):
        p = p.replace("final ", "").strip()
        toks = p.split()
        typ = " ".join(toks[:-1]) if len(toks) > 1 else (toks[0] if toks else "Object")
        typ = typ.replace("? extends ", "").replace("? super ", "").replace("?", "Object")
        typ = re.sub(r"<.*>", "", typ).strip() or "Object"
        if typed:
            typ = jtype(typ)
        elif typ not in PRIMITIVES and typ != "String":
            typ = "Object"
        out.append(f"{typ} a{i}")
    return ", ".join(out)


def infer_return(method, block):
    if method in RETURN_HINTS:
        return RETURN_HINTS[method]
    for line in block.splitlines():
        s = line.strip()
        am = re.match(r"(?:final\s+)?([A-Z]\w[\w.]*)(?:<[^>]*>)?\s+\w+\s*=\s*", s)
        if am and method + "(" in s:
            return am.group(1)
        if re.match(r"(if|while)\s*\(.*" + re.escape(method) + r"\(", s):
            return "boolean"
    if method.startswith(("is", "has", "can", "should")):
        return "boolean"
    return "Object"


def infer_field_type(name):
    hints = {
        "x": "double", "y": "double", "z": "double",
        "xo": "double", "yo": "double", "zo": "double",
        "xRot": "float", "yRot": "float", "zRot": "float",
        "yHeadRot": "float", "tickCount": "int", "id": "int",
        "fireTicks": "int", "isClientSide": "boolean", "onGround": "boolean",
        "noPhysics": "boolean", "hasImpulse": "boolean",
        "horizontalCollision": "boolean", "verticalCollision": "boolean",
        "removed": "boolean",
        "entityData": "net.minecraft.network.syncher.SynchedEntityData",
        "random": "net.minecraft.util.RandomSource",
        "goalSelector": "net.minecraft.world.entity.ai.goal.GoalSelector",
        "navigation": "net.minecraft.world.entity.ai.navigation.PathNavigation",
        "deltaMovement": "net.minecraft.world.phys.Vec3",
        "position": "net.minecraft.core.BlockPos",
        "blockPosition": "net.minecraft.core.BlockPos",
        "level": "net.minecraft.world.level.Level",
        "server": "net.minecraft.server.MinecraftServer",
        "player": "net.minecraft.world.entity.player.Player",
        "screen": "net.minecraft.client.gui.screens.Screen",
        "shadowRadius": "float", "scale": "float", "active": "boolean",
        "phase": "int", "width": "int", "height": "int",
    }
    return hints.get(name, "Object")


def field_decl(ftype, name, visibility="public"):
    static = "static " if name.upper() == name and name.isalpha() is False or name.isupper() else ""
    return f"{visibility} {static}{ftype} {name};"


# --------------------------------------------------------------- file utils --

def fqn_to_path(fqn):
    pkg, _, simple = fqn.rpartition(".")
    return os.path.join(STUBS, pkg.replace(".", "/"), simple + ".java")


def index_stubs():
    SIMPLE_TO_FQN.clear()
    for p in glob.glob(os.path.join(STUBS, "**", "*.java"), recursive=True):
        fqn = p.replace(STUBS + "/", "").replace("/", ".")
        if fqn.endswith(".java"):
            fqn = fqn[:-5]
        SIMPLE_TO_FQN.setdefault(fqn.rpartition(".")[2], fqn)


def find_mod_file(simple):
    hits = glob.glob(os.path.join(MOD, "**", simple + ".java"), recursive=True)
    return hits[0] if hits else None


def inject_member(path, member):
    if not path:
        return
    src = open(path).read()
    if member.strip() in src:
        return
    nm = re.search(r"\b(\w+)\s*\(([^)]*)\)", member)
    field_nm = re.search(r"\s(\w+);", member)
    if nm:
        token, args = nm.group(1), nm.group(2)
        arity = 0 if not args.strip() else args.count(",") + 1
        existing = re.findall(r"\b" + re.escape(token) + r"\s*\(([^)]*)\)", src)
        if "..." in args:
            # Varargs coexists with fixed overloads; only a prior varargs blocks.
            if any("..." in decl for decl in existing):
                return
        else:
            def _first_type(params):
                params = params.strip()
                if not params:
                    return ""
                toks = params.split(",")[0].strip().split()
                return (toks[0] if toks else "").rpartition(".")[2]
            wanted_type = _first_type(args)
            for decl in existing:
                d_arity = 0 if not decl.strip() else decl.count(",") + 1
                if d_arity == arity and _first_type(decl) == wanted_type:
                    return
    elif field_nm and re.search(r"\b" + re.escape(field_nm.group(1)) + r"\s*;", src):
        return
    idx = src.rfind("}")
    src = src[:idx] + "    " + member.strip() + "\n" + src[idx:]
    open(path, "w").write(src)


def make_generic(path, nparams):
    src = open(path).read()
    m = re.search(r"public (class|interface|enum) (\w+)(<[^>]*>)?", src)
    if not m or m.group(3):
        return
    params = ",".join(f"T{i}" for i in range(nparams))
    decl = f"{m.group(1)} {m.group(2)}<{params}>"
    open(path, "w").write(src[:m.start()] + "public " + decl + src[m.end():])


def make_static(path, name, is_method):
    src = open(path).read()
    if is_method:
        new = re.sub(r"public ((?!static)[^;{=]*\b" + re.escape(name) + r"\s*\()",
                     r"public static \1", src, count=1)
    else:
        new = re.sub(r"public ((?!static)[^;=]*\b" + re.escape(name) + r"\s*;)",
                     r"public static \1", src, count=1)
    if new != src:
        open(path, "w").write(new)


def convert_kind(path, kind):
    src = open(path).read()
    pkg = re.search(r"package ([\w.]+);", src).group(1)
    name_m = re.search(r"(?:class|interface) (\w+)", src)
    if not name_m:
        return
    name = name_m.group(1)
    if kind == "annotation" and "@interface" not in src:
        open(path, "w").write(
            f"package {pkg};\n\nimport java.lang.annotation.*;\n\n"
            f"@Retention(RetentionPolicy.RUNTIME)\n@Target({{ElementType.TYPE, ElementType.METHOD, "
            f"ElementType.FIELD, ElementType.PARAMETER, ElementType.CONSTRUCTOR, ElementType.ANNOTATION_TYPE}})\n"
            f"public @interface {name} {{\n    String value() default \"\";\n}}\n")
    elif kind == "interface" and "public interface" not in src:
        open(path, "w").write(f"package {pkg};\n\npublic interface {name} {{\n}}\n")


def ensure_shell(fqn, kind="class"):
    if fqn is None:
        return None
    # Fold dotted references to an already-existing class into nested form so
    # Outer.Inner never creates a colliding Outer/Inner.java package member.
    fqn = split_nested(fqn)
    if fqn.startswith("com.mcsport") or fqn.rpartition(".")[2] in (
            "Object", "String", "Integer", "Boolean", "Double", "Long", "Float", "Byte", "Short"):
        return None
    if "$" in fqn:
        outer_fqn, _, nested = fqn.rpartition("$")
        outer_path = ensure_shell(outer_fqn)
        if not outer_path:
            return None
        src = open(outer_path).read()
        has_nested = re.search(r"@interface " + re.escape(nested) + r"\b", src) or \
            re.search(r"\bclass " + re.escape(nested) + r"\b", src) or \
            re.search(r"\benum " + re.escape(nested) + r"\b", src)
        if kind == "annotation" and not re.search(r"@interface " + re.escape(nested) + r"\b", src) \
                and re.search(r"\bclass " + re.escape(nested) + r"\b", src):
            src = re.sub(r"    public static class " + re.escape(nested) + r"\s*\{\s*\}",
                         "    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)\n"
                         "    public @interface " + nested + " {\n"
                         "        java.lang.String value() default \"\";\n"
                         "        java.lang.String modid() default \"\";\n"
                         "        java.lang.String[] bus() default {};\n    }", src, count=1)
            open(outer_path, "w").write(src)
            return outer_path
        if not has_nested:
            if kind == "annotation":
                decl = ("    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)\n"
                        f"    public @interface {nested} {{\n"
                        "        java.lang.String value() default \"\";\n"
                        "        java.lang.String modid() default \"\";\n"
                        "        java.lang.String[] bus() default {};\n    }}\n")
            elif kind == "interface":
                decl = f"    public interface {nested} {{\n    }}\n"
            elif kind == "enum":
                decl = f"    public enum {nested} {{\n    }}\n"
            else:
                decl = f"    public static class {nested} {{\n    }}\n"
            idx = src.rfind("}")
            open(outer_path, "w").write(src[:idx] + decl + src[idx:])
        return outer_path
    path = fqn_to_path(fqn)
    if os.path.exists(path):
        return path
    os.makedirs(os.path.dirname(path), exist_ok=True)
    pkg, _, simple = fqn.rpartition(".")
    if kind == "interface":
        body = f"package {pkg};\n\npublic interface {simple} {{\n}}\n"
    elif kind == "annotation":
        body = (f"package {pkg};\n\nimport java.lang.annotation.*;\n\n"
                f"@Retention(RetentionPolicy.RUNTIME)\n@Target({{ElementType.TYPE, ElementType.METHOD, "
                f"ElementType.FIELD, ElementType.PARAMETER, ElementType.CONSTRUCTOR, ElementType.ANNOTATION_TYPE}})\n"
                f"public @interface {simple} {{\n    String value() default \"\";\n}}\n")
    elif kind == "enum":
        body = f"package {pkg};\n\npublic enum {simple} {{\n}}\n"
    else:
        ext = f" extends {SUPERCLASS[fqn]}" if SUPERCLASS.get(fqn) else ""
        body = f"package {pkg};\n\npublic class {simple}{ext} {{\n}}\n"
    open(path, "w").write(body)
    return path


# ------------------------------------------------------------- resolution ---

def import_map(diag):
    out = {}
    if not diag:
        return out
    path = diag["path"] if isinstance(diag, dict) else diag[0]
    if not os.path.exists(path):
        return out
    for line in open(path):
        m = re.match(r"import (?:static )?([\w.]+);", line.strip())
        if m:
            out[m.group(1).rpartition(".")[2]] = m.group(1)
    return out


def split_nested(fqn):
    if "$" in fqn:
        return fqn
    parts = fqn.split(".")
    for cut in range(len(parts) - 1, 0, -1):
        if os.path.exists(fqn_to_path(".".join(parts[:cut]))):
            return ".".join(parts[:cut]) + "$" + "$".join(parts[cut:])
    return fqn


def resolve_from_usage(simple, block, diag):
    imap = import_map(diag)
    for line in block.splitlines():
        for m in re.finditer(r"([A-Z]\w+(?:\.[A-Z]\w+)*)\." + re.escape(simple) + r"\b", line):
            chain = m.group(1).split(".")
            base = imap.get(chain[0]) or KNOWN_FQN.get(chain[0])
            if not base:
                continue
            parts = [p for p in chain[1:] + [simple] if p]
            return base + "$" + "$".join(parts)
    return None


def guess_fqn(simple, diag):
    if not simple or not simple[0].isupper():
        return None
    if diag:
        src_path = diag["path"] if isinstance(diag, dict) else diag[0]
        if os.path.exists(src_path):
            for line in open(src_path):
                ls = line.strip()
                mm = re.match(r"import (?:static )?([\w.]+)\." + re.escape(simple) + r";", ls)
                if mm:
                    return split_nested(mm.group(1) + "." + simple)
                if ls == "import " + simple + ";":
                    return simple
    if simple in KNOWN_FQN:
        return KNOWN_FQN[simple]
    if "." in simple:
        return split_nested(simple)
    return "net.minecraft.world." + simple


def resolve_type_path(simple, diag):
    if not SIMPLE_TO_FQN:
        index_stubs()
    fqn = SIMPLE_TO_FQN.get(simple)
    if not fqn:
        fqn = guess_fqn(simple, diag)
        created = ensure_shell(fqn)
        index_stubs()
        return created
    if "$" in fqn:
        return ensure_shell(fqn)
    return fqn_to_path(fqn)


def stub_parent_of(mod_simple, diag):
    mod_file = find_mod_file(mod_simple)
    if not mod_file:
        return None
    parents = re.findall(r"(?:extends|implements)\s+([\w.<>,\s]+?)(?:\s*\{|,|$)",
                         open(mod_file).read(), re.M)
    for chunk in parents:
        for n in re.findall(r"[A-Z]\w+", chunk):
            if find_mod_file(n):
                deeper = stub_parent_of(n, diag)
                if deeper:
                    return deeper
                continue
            p = resolve_type_path(n, diag)
            if p and "/stubs/" in p:
                return p
    return None


def effective_owner(type_name, diag):
    if not type_name:
        return None
    # Dotted declared type such as NetworkEvent.Context -> nested stub.
    if "." in type_name:
        imap = import_map(diag)
        root, _, nested = type_name.partition(".")
        base = imap.get(root) or KNOWN_FQN.get(root)
        if base:
            return ensure_shell(base + "$" + nested.replace(".", "$"))
    if find_mod_file(type_name):
        return stub_parent_of(type_name, diag)
    path = resolve_type_path(type_name, diag)
    return path if path and "/stubs/" in path else None


def resolve_local_type(var_name, diag):
    if not diag:
        return None
    path = diag["path"] if isinstance(diag, dict) else diag[0]
    if not os.path.exists(path):
        return None
    src = open(path).read()
    m = re.search(r"\b([A-Z]\w+)(?:<[^>]*>)?\s+" + re.escape(var_name) + r"\s*=", src)
    if m:
        return m.group(1)
    for pm in re.finditer(r"\(([^)]*)\)", src):
        for part in pm.group(1).split(","):
            toks = part.strip().replace("final ", "").split()
            if len(toks) >= 2 and toks[-1] == var_name:
                return re.sub(r"<.*>", "", toks[-2]).strip()
    fm = re.search(r"for\s*\(\s*(?:final\s+)?([A-Z]\w+)[\w.<>,\s]*?\s"
                   + re.escape(var_name) + r"\s*:", src)
    if fm:
        return fm.group(1)
    return None


def infer_field_owner(block, field_name, diag):
    qualifier = None
    for line in block.splitlines():
        m = re.search(r"(\w+)\." + re.escape(field_name) + r"\b", line)
        if m:
            qualifier = m.group(1)
            break
    if not qualifier:
        return None
    direct = resolve_type_path(qualifier, diag)
    if direct and "/stubs/" in direct:
        return direct
    local_t = resolve_local_type(qualifier, diag)
    if local_t:
        p = effective_owner(local_t, diag)
        if p and "/stubs/" in p:
            return p
    return None


def _owner_path_from_line(line, diag):
    """Resolve the type that declares the member used on a source line."""
    m = re.search(r"([A-Za-z_]\w*)\.[A-Za-z_]\w*\s*[(;]", line)
    if not m:
        return None
    qualifier = m.group(1)
    if qualifier[0].isupper():
        return resolve_type_path(qualifier, diag)
    local_t = resolve_local_type(qualifier, diag)
    return effective_owner(local_t, diag) if local_t else None


def tighten_result(to_type, block, diag, actions):
    target = jtype(to_type.replace("[]", ""))
    for line in block.splitlines():
        mm = re.search(r"\.(\w+)\s*\(", line)
        if mm:
            method = mm.group(1)
            owner = _owner_path_from_line(line, diag)
            candidates = [owner] if owner else \
                glob.glob(os.path.join(STUBS, "**", "*.java"), recursive=True)
            for p in candidates:
                if not p or not os.path.exists(p):
                    continue
                s = open(p).read()
                # First concrete type wins: only tighten methods still typed
                # Object so competing call sites cannot flip the type back.
                pat = r"(public (?:static )?)java\.lang\.Object (" + re.escape(method) + r"\s*\()"
                if re.search(pat, s):
                    open(p, "w").write(re.sub(pat, r"\g<1>" + target + r" \2", s, count=1))
                    actions["tighten"] += 1
                    return
            continue
        fm = re.search(r"([A-Za-z_]\w*)\.([A-Z_][A-Z0-9_]*)\b", line)
        if fm:
            owner = _owner_path_from_line(line, diag)
            if owner:
                s = open(owner).read()
                new = re.sub(r"(public (?:static )?)(?:[\w.]+) (" + fm.group(2) + r"\s*;)",
                             r"\g<1>" + target + r" \2", s, count=1)
                if new != s:
                    open(owner, "w").write(new)
                    actions["tighten_field"] += 1
                    return


def align_return(method, diag, actions):
    mod_file = diag["path"]
    src = open(mod_file).read()
    dm = re.search(r"public (?:static )?([\w.]+)(?:<[^>]*>)?\s+" + re.escape(method)
                   + r"\s*\([^)]*\)", src)
    if not dm:
        return
    ret = jtype(dm.group(1))
    mod_simple = re.search(r"/(\w+)\.java", mod_file).group(1)
    parent = stub_parent_of(mod_simple, diag)
    if not parent:
        return
    s = open(parent).read()
    new = re.sub(r"(public (?:static )?)java\.lang\.Object (" + re.escape(method) + r"\s*\()",
                 r"\g<1>" + ret + r" \2", s, count=1)
    if new != s:
        open(parent, "w").write(new)
        actions["align_return"] += 1


def make_functional(block, diag, actions):
    text = "\n".join(block.splitlines())
    target_fqn = None
    sam = "apply"
    if "addListener" in text:
        target_fqn = "net.minecraftforge.eventbus.api.IEventBus"
        sam = "addListener"
    elif "registerAboveAll" in text:
        sam = "render"
        for line in open(diag["path"]):
            mm = re.search(r"import ([\w.]*IGuiOverlay);", line)
            if mm:
                target_fqn = mm.group(1)
    elif "createTickerHelper" in text:
        target_fqn = "net.minecraft.world.level.block.entity.BlockEntityTicker"
        sam = "tick"
    if not target_fqn:
        return
    pkg, _, name = target_fqn.rpartition(".")
    ensure_shell(target_fqn)
    path = fqn_to_path(target_fqn)
    # A single varargs SAM keeps the interface functional while accepting
    # lambdas/method references of any arity.
    open(path, "w").write(
        f"package {pkg};\n\npublic interface {name} {{\n"
        f"    void {sam}(java.lang.Object... args);\n}}\n")
    actions["functional"] += 1


def add_overridable(method, args, mod_type, diag, actions):
    mod_file = find_mod_file(mod_type)
    if not mod_file:
        return
    parents = re.findall(r"(?:extends|implements)\s+([\w.<>,\s]+?)(?:\s*\{|,|$)",
                         open(mod_file).read(), re.M)
    names = []
    for chunk in parents:
        names += re.findall(r"[A-Z]\w+", chunk)
    params = build_params(args, typed=True)
    ret = "void" if method in ("tick", "init", "render", "defineSynchedData",
                                "readAdditionalSaveData", "addAdditionalSaveData",
                                "registerGoals") else "java.lang.Object"
    if method in ("isPushable", "canBeCollidedWith", "isPickable", "shouldBeSaved",
                  "removeWhenFarAway", "canUse", "canContinueToUse",
                  "propagatesSkylightDown", "canBeLeashed"):
        ret = "boolean"
    if method == "removeWhenFarAway":
        params, ret = "double a0", "boolean"
    for n in names:
        p = effective_owner(n, diag)
        if p and "/stubs/" in p:
            inject_member(p, f"public {ret} {method}({params}) {{ return {default_val(ret)}; }}")
            actions["override"] += 1
            return


# --------------------------------------------------------------- sweeps -----

def sweep_referenced_types():
    pat = re.compile(r"\b((?:net|com)\.(?:minecraft|mojang|minecraftforge)\.[A-Za-z0-9_.]*[A-Z]\w*)")
    seen = set()
    for p in glob.glob(os.path.join(STUBS, "**", "*.java"), recursive=True):
        for m in pat.finditer(open(p).read()):
            fqn = m.group(1).rstrip(".")
            if fqn in seen:
                continue
            seen.add(fqn)
            if "$" not in fqn and not os.path.exists(fqn_to_path(fqn)):
                ensure_shell(fqn)


def drop_stray_stubs():
    """Remove stub shells whose simple name matches a real mod class: they can
    only confuse resolution (e.g. a guessed net.minecraft.world.NpcPlayerModel)."""
    mod_names = set()
    mod_root = os.path.join(HARNESS, "modcopy")
    for dp, _, files in os.walk(mod_root):
        for fn in files:
            if fn.endswith(".java"):
                mod_names.add(fn[:-5])
    for p in glob.glob(os.path.join(STUBS, "**", "*.java"), recursive=True):
        if os.path.basename(p)[:-5] in mod_names:
            os.remove(p)


def materialize_nested_from_sources():
    """Every PascalCase Outer.Inner reference in mod sources needs a static
    nested class in the Outer stub; inferred same-named fields are removed."""
    index = {}
    for p in glob.glob(os.path.join(STUBS, "**", "*.java"), recursive=True):
        index.setdefault(os.path.basename(p)[:-5], p)
    pat = re.compile(r"\b([A-Z][A-Za-z0-9]*)\.([A-Z][A-Za-z0-9]*)\b")
    todo = set()
    for dp, _, files in os.walk(os.path.join(HARNESS, "modcopy")):
        for fn in files:
            if not fn.endswith(".java"):
                continue
            src = open(os.path.join(dp, fn)).read()
            for outer, inner in pat.findall(src):
                # ALL_CAPS members are enum/field constants, not nested types.
                if inner.isupper() or outer not in index:
                    continue
                todo.add((outer, inner))
    for outer, inner in sorted(todo):
        path = index[outer]
        s = open(path).read()
        if re.search(r"\b(class|interface|enum|@interface) " + re.escape(inner) + r"\b", s):
            continue
        s = re.sub(r"[ \t]*public [\w.$<>]+ " + re.escape(inner) + r";\n?", "", s)
        idx = s.rfind("}")
        s = s[:idx] + f"    public static class {inner} {{\n    }}\n" + s[idx:]
        open(path, "w").write(s)


def find_nested_bounds(s, name):
    """(body_start, body_end) offsets of a type named `name` at any depth."""
    m = re.search(r"\b(?:class|interface|enum|@interface) "
                  + re.escape(name) + r"\b[^{]*\{", s)
    if not m:
        return None
    depth, i = 1, m.end()
    while i < len(s) and depth:
        if s[i] == "{":
            depth += 1
        elif s[i] == "}":
            depth -= 1
        i += 1
    return m.end(), i - 1


def materialize_nested_enums():
    """Create curated nested enums, walking multi-level Outer$Inner$Leaf chains."""
    for fqn, constants in CURATED_NESTED_ENUMS.items():
        parts = fqn.split("$")
        path = ensure_shell(parts[0])
        if not path:
            continue
        s = open(path).read()
        chain = parts[1:]
        # Idempotency: once the leaf enum exists the chain is complete.
        if re.search(r"\benum " + re.escape(chain[-1]) + r"\b", s):
            continue
        # Nested types inside an @interface are implicitly static; the explicit
        # 'static' modifier is illegal there.
        in_annotation = "@interface" in s
        for depth, name in enumerate(chain):
            is_last = depth == len(chain) - 1
            # Always clear an inferred same-named field and an empty placeholder
            # class first; a populated existing type is left untouched.
            s = re.sub(r"[ \t]*public [\w.$<>]+ " + re.escape(name) + r";\n?", "", s)
            s = re.sub(r"    public (?:static )?(?:final )?(?:class|interface) "
                       + re.escape(name) + r"\s*\{\s*\}\n?", "", s)
            if re.search(r"\b(?:class|interface|enum) " + re.escape(name) + r"\b", s):
                continue
            indent = "    " * (depth + 1)
            static_kw = "" if in_annotation else "static "
            if is_last:
                member = (f"{indent}public {static_kw}enum {name} {{\n{indent}    "
                          + ", ".join(constants) + f"\n{indent}}}\n")
            else:
                member = f"{indent}public {static_kw}class {name} {{\n{indent}}}\n"
            if depth == 0:
                idx = s.rfind("}")
                s = s[:idx] + member + s[idx:]
            else:
                bounds = find_nested_bounds(s, chain[depth - 1])
                if not bounds:
                    break
                s = s[:bounds[1]] + member + s[bounds[1]:]
        open(path, "w").write(s)


def materialize_top_enums():
    """Wholesale-rewrite curated top-level enums (annotation attributes etc.)."""
    for fqn, constants in CURATED_TOP_ENUMS.items():
        pkg, _, name = fqn.rpartition(".")
        path = fqn_to_path(fqn)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        open(path, "w").write(
            f"package {pkg};\n\npublic enum {name} {{\n    "
            + ", ".join(constants) + "\n}\n")


def drop_duplicate_nested():
    """Remove empty nested classes shadowing a same-named interface/enum."""
    for p in glob.glob(os.path.join(STUBS, "**", "*.java"), recursive=True):
        s = open(p).read()
        orig = s
        for m in set(re.findall(r"public (?:interface|enum) (\w+)", s)):
            s = re.sub(r"    public static class " + m + r"\s*\{\s*\}\n?", "", s)
        if s != orig:
            open(p, "w").write(s)


def drop_bad_constant_classes():
    """Empty ALL_CAPS nested classes are mis-generated enum-constant shells."""
    for p in glob.glob(os.path.join(STUBS, "**", "*.java"), recursive=True):
        s = open(p).read()
        s2 = re.sub(r"    public static class [A-Z][A-Z0-9_]+\s*\{\s*\}\n?",
                    "", s)
        if s2 != s:
            open(p, "w").write(s2)


def materialize_nested_annotations():
    """Convert placeholder nested classes into annotation interfaces."""
    for fqn, members in CURATED_NESTED_ANNOTATIONS.items():
        outer, _, nested = fqn.rpartition("$")
        ensure_shell(fqn)
        path = fqn_to_path(outer)
        s = open(path).read()
        if re.search(r"@interface\s+" + re.escape(nested) + r"\b", s):
            continue
        m = re.search(r"public (?:static )?(?:final )?class "
                      + re.escape(nested) + r"\b[^{]*\{", s)
        if not m:
            continue
        s = s[:m.start()] + "public @interface " + nested + " {" + s[m.end():]
        bounds = find_nested_bounds(s, nested)
        if bounds:
            body = s[bounds[0]:bounds[1]]
            additions = "".join(
                f"        {ftype} {mname}() default {default};\n"
                for ftype, mname, default in members
                if not re.search(r"\b" + re.escape(mname) + r"\s*\(", body))
            if additions:
                s = s[:bounds[1]] + additions + s[bounds[1]:]
        open(path, "w").write(s)


def materialize_nested_fields():
    """Inject curated fields into a particular nested class body."""
    for fqn, fields in CURATED_NESTED_FIELDS.items():
        outer, _, nested = fqn.rpartition("$")
        path = ensure_shell(fqn)
        if not path:
            continue
        s = open(path).read()
        bounds = find_nested_bounds(s, nested)
        if not bounds:
            continue
        body = s[bounds[0]:bounds[1]]
        additions = "".join(
            f"        public {ftype} {fname};\n"
            for ftype, fname in fields
            if not re.search(r"\b" + re.escape(fname) + r"\s*;", body))
        if additions:
            open(path, "w").write(s[:bounds[1]] + additions + s[bounds[1]:])


def _remove_member(s, name, is_method):
    """Drop a top-level (4-space indented) field or method named `name`."""
    lines = s.splitlines(keepends=True)
    out = []
    i = 0
    while i < len(lines):
        line = lines[i]
        starts = (line.startswith("    ") and not line.startswith("        ")
                  and re.search(r"\b" + re.escape(name) + r"\s*[;(]", line))
        if starts:
            if is_method and "{" not in line:
                # Walk forward until the line carrying the body opening brace.
                while i < len(lines) and "{" not in lines[i]:
                    i += 1
            depth = line.count("{") - line.count("}")
            i += 1
            if is_method:
                while i < len(lines) and depth > 0:
                    depth += lines[i].count("{") - lines[i].count("}")
                    i += 1
            else:
                # Field: consume through the terminating semicolon line.
                while i < len(lines) and ";" not in lines[i - 1]:
                    i += 1
            continue
        out.append(line)
        i += 1
    return "".join(out)


# Subclasses that must keep the superclass type argument (raw supers erase
# generic method signatures and cause name clashes).
CURATED_SUPER_GENERICS = {
    "HumanoidModel": ("net.minecraft.client.model.EntityModel",
                      "net.minecraft.client.model.EntityModel<T0>"),
    "MobRenderer": ("net.minecraft.client.renderer.entity.EntityRenderer",
                    "net.minecraft.client.renderer.entity.EntityRenderer<T0>"),
    "LivingEntityRenderer": ("net.minecraft.client.renderer.entity.EntityRenderer",
                             "net.minecraft.client.renderer.entity.EntityRenderer<T0>"),
    "HumanoidMobRenderer": ("net.minecraft.client.renderer.entity.MobRenderer",
                            "net.minecraft.client.renderer.entity.MobRenderer<T0>"),
}


def materialize_curated_decls(path):
    s = open(path).read()
    cls = re.search(r"public (?:static )?(?:abstract )?(?:class|interface) (\w+)", s)
    if not cls:
        return
    is_interface = "public interface" in s or "@interface" in s
    if cls.group(1) in CURATED_SUPER_GENERICS:
        raw, generic = CURATED_SUPER_GENERICS[cls.group(1)]
        s2 = re.sub(r"extends " + re.escape(raw) + r"(?!<)",
                    "extends " + generic, s, count=1)
        if s2 != s:
            s = s2
            changed = True
        else:
            changed = False
    else:
        changed = False
    if cls.group(1) not in CURATED_DECLS:
        if changed:
            open(path, "w").write(s)
        return
    for decl in CURATED_DECLS[cls.group(1)]:
        name_m = re.search(r"\b(\w+)\s*[;(]", decl)
        name = name_m.group(1)
        if re.search(r"\b" + re.escape(name) + r"\s*\(", decl) and name == cls.group(1):
            continue  # constructors are handled elsewhere
        if decl.strip() in s:
            continue
        is_method = "(" in decl
        s = _remove_member(s, name, is_method)
        member = decl.strip()
        if is_interface and is_method and not member.startswith("default "):
            member = "default " + member
        idx = s.rfind("}")
        s = s[:idx] + "    " + member + "\n" + s[idx:]
        changed = True
    if changed:
        open(path, "w").write(s)


def apply_curated_generics():
    """Add type parameters to curated generic class/interface declarations."""
    for fqn, params in CURATED_GENERICS.items():
        if "$" in fqn:
            outer, _, name = fqn.rpartition("$")
            path = fqn_to_path(outer)
        else:
            path = fqn_to_path(fqn)
            name = fqn.rpartition(".")[2]
        if not os.path.exists(path):
            continue
        s = open(path).read()
        pat = (r"(public (?:static )?(?:class|interface) "
               + re.escape(name) + r")(?!\s*<)")
        s2 = re.sub(pat, lambda m: m.group(1) + params, s, count=1)
        if s2 != s:
            open(path, "w").write(s2)


def normalize_stubs():
    drop_stray_stubs()
    materialize_top_enums()
    drop_bad_constant_classes()
    for outer, nested_names in CURATED_NESTED.items():
        for nested in nested_names:
            ensure_shell(outer + "$" + nested)
    materialize_nested_from_sources()
    drop_bad_constant_classes()
    materialize_nested_annotations()
    materialize_nested_enums()
    materialize_nested_fields()
    materialize_curated_interfaces()
    drop_duplicate_nested()
    apply_curated_generics()
    for p in glob.glob(os.path.join(STUBS, "**", "*.java"), recursive=True):
        s = open(p).read()
        orig = s
        cls = re.search(r"public (?:class|enum|abstract class) (\w+)", s)
        is_interface = "public interface" in s or "@interface" in s
        is_enum = bool(re.search(r"public enum \w+", s))
        cls_name = re.search(r"public (?:class|interface|enum) (\w+)", s)
        cls_name = cls_name.group(1) if cls_name else ""
        if cls and not is_interface and not is_enum:
            name = cls.group(1)
            if not re.search(r"\b" + re.escape(name) + r"\s*\(", s):
                idx = s.find("{")
                s = s[:idx + 1] + f"\n    public {name}() {{}}\n" + s[idx + 1:]
        if "distanceToSqr" in s:
            s = re.sub(r"public [\w.]+ distanceToSqr\(([^)]*)\)\s*\{[^}]*\}",
                       r"public double distanceToSqr(\1) { return 0.0; }", s)
        # Primitive-returning methods must never return null / a wrong literal.
        for prim, lit in (("int", "0"), ("boolean", "false"), ("float", "0.0F"),
                          ("double", "0.0"), ("byte", "0"), ("short", "0"),
                          ("long", "0L"), ("char", "0")):
            s = re.sub(r"(public (?:static )?" + prim + r" \w+\([^)]*\)\s*\{\s*return )[^;}]+(;\s*\})",
                       r"\g<1>" + lit + r"\2", s)
        # Keep identity methods covariant across the entity stub hierarchy.
        if "getUUID" in s:
            s = re.sub(r"public [\w.]+ getUUID\(([^)]*)\)\s*\{[^}]*\}",
                       r"public java.util.UUID getUUID(\1) { return null; }", s)
        # addFreshEntity is boolean across Level/ServerLevel.
        if "addFreshEntity" in s:
            s = re.sub(r"public [\w.]+ addFreshEntity\(([^)]*)\)\s*\{[^}]*\}",
                       r"public boolean addFreshEntity(\1) { return false; }", s)
        # Collection-returning lookups need a type variable so for-each and
        # generic call sites infer the element type instead of raw Object.
        for generic_meth in ("getEntitiesOfClass",):
            if generic_meth in s:
                s = re.sub(r"public [\w.<>]+ (" + generic_meth
                           + r"\([^)]*\))\s*\{[^}]*\}",
                           r"public <T> java.util.List<T> "
                           + generic_meth
                           + r"(java.lang.Class<T> a0, java.lang.Object a1) { return null; }", s)
        # Mth overloads must keep their exact primitive return types; inferred
        # stubs collapse clamp(float,...) to double and sin/cos to Object.
        if cls_name == "Mth":
            s = re.sub(r"public static [\w.]+ clamp\(int[^)]*\)\s*\{[^}]*\}",
                       "public static int clamp(int a0, int a1, int a2) { return 0; }", s)
            s = re.sub(r"public static [\w.]+ clamp\(float[^)]*\)\s*\{[^}]*\}",
                       "public static float clamp(float a0, float a1, float a2) { return 0.0F; }", s)
            s = re.sub(r"public static [\w.]+ clamp\(double[^)]*\)\s*\{[^}]*\}",
                       "public static double clamp(double a0, double a1, double a2) { return 0.0; }", s)
            s = re.sub(r"public static [\w.]+ (sin|cos)\(float[^)]*\)\s*\{[^}]*\}",
                       r"public static float \1(float a0) { return 0.0F; }", s)
        # Curated generic methods that must preserve the caller's type variable.
        for owner, decls in CURATED_GENERIC_METHODS.items():
            if owner != cls_name:
                continue
            for decl in decls:
                meth = decl.split("(")[0].split()[-1]
                # Collapse every inferred overload to one canonical generic.
                s, n = re.subn(r"[ \t]*public (?:static )?[\w.$<>\[\], ?]* "
                               + meth + r"\([^)]*\)\s*\{[^}]*\}\n?",
                               "", s)
                if n:
                    idx = s.rfind("}")
                    s = s[:idx] + "    " + decl + "\n" + s[idx:]
        # Void methods must not return a value.
        s = re.sub(r"(public (?:static )?void \w+\([^)]*\)\s*\{\s*)return [^;}]+;",
                   r"\1", s)
        s = s.replace("java.lang.Object[] bus() default {};",
                      "java.lang.String[] bus() default {};")
        s = s.replace("java.lang.Object[] value2() default {};",
                      "java.lang.String[] value2() default {};")
        # Enforce authoritative curated return types on already-generated methods.
        # Bare (global) keys first so owner-qualified entries win on conflicts.
        ordered_returns = sorted(METHOD_RETURN.items(),
                                 key=lambda kv: 0 if "." not in kv[0] else 1)
        for key, ret in ordered_returns:
            if "." in key:
                owner, meth = key.split(".", 1)
                if owner != cls_name:
                    continue
            else:
                meth = key
            # Bare global keys must not rewrite methods inside nested classes;
            # those are owned by CURATED_NESTED_METHODS / FUNCTIONAL_FQN.
            nested_ranges = []
            if "." not in key:
                for nm in re.finditer(r"    public (?:static )?(?:final )?"
                                      r"(?:class|interface|enum) (\w+)", s):
                    nb = find_nested_bounds(s, nm.group(1))
                    if nb:
                        nested_ranges.append(nb)

            def _rewrite(mm, r=ret):
                if any(a <= mm.start() < b for a, b in nested_ranges):
                    return mm.group(0)
                return mm.group(1) + r + " " + mm.group(2) \
                    + f" return {default_val(r)}; }}"
            s = re.sub(r"(public (?:static )?)[\w.$]+ (" + re.escape(meth)
                       + r"\s*\([^)]*\)\s*\{)[^}]*\}",
                       _rewrite, s, count=1)
        # Make every method public so overrides never reduce visibility.
        s = re.sub(r"^    (?!public |private |protected )(?!static )"
                   r"([\w.]+(?:<[^>]*>)?\s+\w+\s*\([^)]*\)\s*\{)",
                   r"    public \1", s, flags=re.M)
        # Registry holder fields are all the same type as the holder class name.
        if cls_name == "Blocks":
            s = re.sub(r"(public static )[\w.]+ (\w+;)",
                       r"\1net.minecraft.world.level.block.Block \2", s)
        if cls_name == "Items":
            s = re.sub(r"(public static )[\w.]+ (\w+;)",
                       r"\1net.minecraft.world.item.Item \2", s)
        if cls_name == "SoundEvents":
            s = re.sub(r"(public static )[\w.]+ (\w+;)",
                       r"\1net.minecraft.core.Holder \2", s)
        # Drop stray multi-Object overloads left by inference for these curated
        # block-state hooks; the canonical 1-arg declaration must be the only one.
        if cls_name in ("Block", "BlockBehaviour", "HorizontalDirectionalBlock"):
            s = re.sub(r"[ \t]*public [\w.$<>\[\], ?]*createBlockStateDefinition"
                       r"\(\s*java\.lang\.Object[^)]*\)\s*\{[^}]*\}\n?", "", s)
            s = re.sub(r"[ \t]*public [\w.$<>\[\], ?]*getTicker"
                       r"\(\s*java\.lang\.Object[^)]*\)\s*\{[^}]*\}\n?", "", s)
        for owner, fields in CURATED_STATIC_FIELDS.items():
            if owner != cls_name:
                continue
            for ftype, fname in fields:
                s = re.sub(r"public (?:static )?[\w.$<>]+ " + re.escape(fname) + r";",
                           f"public static {ftype} {fname};", s, count=1)
                if not re.search(r"\b" + re.escape(fname) + r"\s*;", s):
                    idx = s.rfind("}")
                    s = (s[:idx] + f"    public static {ftype} {fname};\n"
                         + s[idx:])
        # Java source references nested types with '.', never the binary '$'.
        s = s.replace("$", ".")
        if s != orig:
            open(p, "w").write(s)
        apply_curated(p)
        materialize_curated_decls(p)
        add_varargs_ctor(p)


def apply_curated(path):
    s = open(path).read()
    for cls_name, members in CURATED_METHODS.items():
        if not re.search(r"(?:class|interface|enum) " + cls_name + r"\b", s):
            continue
        for decl in members:
            is_static = decl.startswith("static ")
            body_decl = decl[len("static "):] if is_static else decl
            ret, rest = body_decl.split(" ", 1)
            name = rest.split("(", 1)[0]
            arity = rest.split("(", 1)[1].count(",") + 1 if "(" in rest and rest.split("(", 1)[1].strip(" )") else 0
            existing = re.findall(r"\b" + re.escape(name) + r"\s*\(([^)]*)\)", s)
            if "..." in rest:
                # Varargs overloads coexist with fixed-arity ones; only skip
                # when another varargs declaration is already present.
                if any("..." in e for e in existing):
                    continue
            else:
                def first_type(params):
                    params = params.strip()
                    if not params:
                        return ""
                    first = params.split(",")[0].strip().split()
                    return (first[0] if first else "").rpartition(".")[2]
                wanted = first_type(rest.split("(", 1)[1].rsplit(")", 1)[0])
                same_sig = [e for e in existing
                            if (0 if not e.strip() else e.count(",") + 1) == arity
                            and first_type(e) == wanted]
                if same_sig:
                    # Curated declarations are authoritative: remove every
                    # inferred same-signature method, then inject one canonical.
                    prefix = "public static " if is_static else "public "
                    full = f"{prefix}{ret} {rest} {{ return {default_val(ret)}; }}"
                    s2, n = re.subn(r"[ \t]*public (?:static )?[\w.$<>\[\], ?]*\b"
                                    + re.escape(name) + r"\s*\([^)]*\)\s*\{[^}]*\}\n?",
                                    "", s)
                    if n:
                        s = s2
                        idx = s.rfind("}")
                        s = s[:idx] + "    " + full + "\n" + s[idx:]
                        open(path, "w").write(s)
                    else:
                        inject_member(path, full)
                        s = open(path).read()
                    continue
            prefix = "public static " if is_static else "public "
            inject_member(path, f"{prefix}{ret} {rest} {{ return {default_val(ret)}; }}")
            s = open(path).read()
    for cls_name, fields in CURATED_FIELDS.items():
        if not re.search(r"class " + cls_name + r"\b", s):
            continue
        for ftype, fname in fields:
            # Overwrite any wrongly-typed existing field with the curated type.
            new_s = re.sub(r"(public|protected|private)?\s*(?:static\s+)?[\w.$]+\s+"
                           + re.escape(fname) + r"\s*;",
                           f"public {ftype} {fname};", s, count=1)
            if new_s != s:
                s = new_s
                open(path, "w").write(s)
            elif not re.search(r"\b" + re.escape(fname) + r"\s*;", s):
                inject_member(path, f"public {ftype} {fname};")
                s = open(path).read()


def add_varargs_ctor(path):
    s = open(path).read()
    if "public interface" in s or "@interface" in s or " enum " in s:
        return
    # Curated functional-parameter constructors must survive untouched.
    pkg = re.search(r"package ([\w.]+);", s)
    cls = re.search(r"public (?:static )?(?:abstract )?class (\w+)", s)
    if pkg and cls and pkg.group(1) + "." + cls.group(1) in NO_VARARGS_CTOR:
        return
    cls = re.search(r"public (?:static )?(?:abstract )?class (\w+)", s)
    if not cls:
        return
    name = cls.group(1)
    # Drop fixed-arity constructors: the varargs ctor accepts every call and
    # avoids primitive/reference overload mismatches (e.g. double vs int args).
    s = re.sub(r"    public " + re.escape(name) + r"\([^)]*[A-Za-z][^)]*\)\s*\{\s*\}\n?",
               "", s)
    if "Object... a" not in s:
        idx = s.find("{")
        s = s[:idx + 1] + f"\n    public {name}(java.lang.Object... a) {{}}\n" + s[idx + 1:]
    open(path, "w").write(s)
    # Stubs must never shadow real java.lang types.
    import shutil
    jl = os.path.join(STUBS, "java")
    if os.path.isdir(jl):
        shutil.rmtree(jl)


# ------------------------------------------------------------- diagnostics --

def run_ecj():
    sources = sorted(glob.glob(os.path.join(MOD, "**", "*.java"), recursive=True))
    stubs = sorted(glob.glob(os.path.join(STUBS, "**", "*.java"), recursive=True))
    os.makedirs(OUT, exist_ok=True)
    args = [JAVA, "-jar", ECJ, "-source", "17", "-target", "17", "-nowarn",
            "-proc:none", "-d", OUT, "-cp", OUT] + sources + stubs
    p = subprocess.run(args, capture_output=True, text=True)
    return p.stderr + p.stdout


def parse(raw):
    out = []
    for b in (x.strip() for x in raw.split("----------") if x.strip()):
        lines = b.splitlines()
        m = re.search(r"ERROR in (.+?) \(at line (\d+)\)", lines[0]) if lines else None
        if m:
            out.append({"path": m.group(1), "line": int(m.group(2)),
                        "msg": lines[-1].strip(), "block": b})
    return out


def handle(d, msg, block, actions):
    m = re.search(r"([\w.]+) is not an annotation type", msg)
    if m:
        name = m.group(1)
        if "." in name:
            fqn = resolve_from_usage(name.split(".")[-1], block, d)
            if not fqn:
                imap = import_map(d)
                base = imap.get(name.split(".")[0]) or KNOWN_FQN.get(name.split(".")[0])
                if base:
                    fqn = base + "$" + "$".join(name.split(".")[1:])
            if fqn:
                ensure_shell(fqn, "annotation")
                actions["annotation"] += 1
        else:
            path = resolve_type_path(name, d)
            if path:
                convert_kind(path, "annotation")
                actions["annotation"] += 1
        return
    if "cannot be a superinterface" in msg or "must be an interface" in msg:
        m = re.search(r"The type (\w+) cannot be a superinterface", msg)
        if m:
            path = resolve_type_path(m.group(1), d)
            if path:
                convert_kind(path, "interface")
                actions["interface"] += 1
        return
    m = re.search(r"The type (\w+) is not generic; it cannot be parameterized with arguments <([^>]*)>", msg)
    if m:
        path = resolve_type_path(m.group(1), d)
        if path:
            make_generic(path, max(1, m.group(2).count(",") + 1))
            actions["generic"] += 1
        return
    m = re.search(r"(\w+) cannot be resolved to a type", msg)
    if m:
        simple = m.group(1)
        fqn = resolve_from_usage(simple, block, d) or guess_fqn(simple, d)
        # Properties is an inherited nested type: Item subclasses see
        # Item.Properties, block subclasses see BlockBehaviour.Properties.
        if simple == "Properties" and os.path.exists(d["path"]):
            head = open(d["path"]).read(2000)
            if re.search(r"extends\s+[\w.]*Item\b", head) or "/item/" in d["path"]:
                fqn = "net.minecraft.world.item.Item$Properties"
        if fqn and ensure_shell(fqn):
            actions["new_shell"] += 1
        return
    m = re.search(r"The method (\w+)\(([^)]*)\) is undefined for the type (\w+)", msg)
    if m:
        method, args, type_name = m.group(1), m.group(2), m.group(3)
        path = effective_owner(type_name, d)
        # Calls whose receiver erased to Object (generic RegistryObject.get(),
        # raw collections) are routed to a universal value marker stub.
        if not path or type_name == "Object":
            ensure_shell("net.minecraftforge.registries.RegistryValue")
            path = fqn_to_path("net.minecraftforge.registries.RegistryValue")
        if path:
            owner_simple = re.search(r"/(\w+)\.java$", path).group(1)
            ret = jtype(METHOD_RETURN.get(f"{owner_simple}.{method}",
                                          METHOD_RETURN.get(method,
                                                            infer_return(method, block))))
            inject_member(path, f"public {ret} {method}({build_params(args)}) "
                                f"{{ return {default_val(ret)}; }}")
            actions["method"] += 1
        return
    m = re.search(r"The constructor (\w+)\(([^)]*)\) is undefined", msg)
    if m:
        type_name, args = m.group(1), m.group(2)
        if not find_mod_file(type_name):
            path = effective_owner(type_name, d)
            if path:
                ctor = re.search(r"/(\w+)\.java$", path).group(1)
                inject_member(path, f"public {ctor}({build_params(args)}) {{}}")
                actions["ctor"] += 1
        return
    m = re.search(r"(\w+) cannot be resolved or is not a field", msg)
    if m:
        field_name = m.group(1)
        owner = infer_field_owner(block, field_name, d)
        if owner:
            inject_member(owner, field_decl(jtype(infer_field_type(field_name)), field_name))
            actions["field"] += 1
        else:
            mod_simple = re.search(r"/(\w+)\.java", d["path"]).group(1)
            parent = stub_parent_of(mod_simple, d)
            if parent:
                inject_member(parent, field_decl(jtype(infer_field_type(field_name)),
                                                  field_name, "protected"))
                actions["inherited_field"] += 1
        return
    m = re.search(r"The method (\w+)\(([^)]*)\) of type (\w+) must override or implement a supertype method", msg)
    if m:
        add_overridable(m.group(1), m.group(2), m.group(3), d, actions)
        return
    m = re.search(r"Cannot make a static reference to the non-static (field|method) "
                  r"(\w+)(?:\(([^)]*)\))? from the type (\w+)", msg)
    if m:
        kind, name, _, type_name = m.group(1), m.group(2), m.group(3), m.group(4)
        path = effective_owner(type_name, d)
        if path:
            make_static(path, name, kind == "method")
            actions["static"] += 1
        return
    m = re.search(r"(\w+) cannot be resolved to a variable", msg)
    if m:
        name = m.group(1)
        mod_simple = re.search(r"/(\w+)\.java", d["path"]).group(1)
        parent = stub_parent_of(mod_simple, d)
        if parent:
            inject_member(parent, field_decl(jtype(infer_field_type(name)), name, "protected"))
            actions["inherited_field"] += 1
        return
    m = re.search(r"Type mismatch: cannot convert from ([\w.]+) to ([\w.<>\[\]]+)", msg)
    if m:
        tighten_result(m.group(2), block, d, actions)
        return
    # A primitive-typed stub method is wrongly chained; force it back to Object.
    m = re.search(r"Cannot invoke (\w+)\([^)]*\) on the primitive type (\w+)", msg)
    if m:
        called = m.group(1)
        for line in block.splitlines():
            mm = re.search(r"(\w+)\." + re.escape(called) + r"\(", line)
            if not mm:
                continue
            owner_t = resolve_local_type(mm.group(1), d)
            target = effective_owner(owner_t, d) if owner_t else None
            for p in ([target] if target else
                      glob.glob(os.path.join(STUBS, "**", "*.java"), recursive=True)):
                if not p:
                    continue
                s = open(p).read()
                new = re.sub(r"public (?:static )?(?:byte|short|int|long|float|double|boolean|char) ("
                             + re.escape(mm.group(1)) + r"\s*\()",
                             r"public java.lang.Object \1", s, count=1)
                if new != s:
                    open(p, "w").write(new)
                    actions["unprime"] += 1
                    return
        return
    # Overridden method return type disagrees with the stub supertype: mirror
    # the mod method's actual declared return type onto the parent stub.
    m = re.search(r"The return type is incompatible with [\w.]+\.(\w+)\(", msg)
    if m:
        align_return(m.group(1), d, actions)
        return
    # Lambda/method-reference target must be a functional interface.
    if "must be a functional interface" in msg:
        make_functional(block, d, actions)
        return
    # Subtype casts require a real extends relationship in the stubs.
    m = re.search(r"Cannot cast from (\w+) to (\w+)", msg)
    if m:
        src_t, dst_t = m.group(1), m.group(2)
        src_p = resolve_type_path(src_t, d)
        dst_fqn = KNOWN_FQN.get(dst_t) or guess_fqn(dst_t, d)
        if src_p and dst_fqn:
            ensure_shell(dst_fqn)
            s = open(src_p).read()
            if " extends " not in s.split("{", 1)[0]:
                s = s.replace("public class " + src_t,
                              "public class " + src_t + " extends " + dst_fqn, 1)
                open(src_p, "w").write(s)
                actions["cast_hierarchy"] += 1
        return
    m = re.search(r"The method (\w+)\(([^)]*)\) in the type (\w+) is not applicable for the arguments \(([^)]*)\)", msg)
    if m:
        name, _, type_name, actual = m.group(1), m.group(2), m.group(3), m.group(4)
        path = effective_owner(type_name, d)
        if path:
            inject_member(path, f"public java.lang.Object {name}({build_params(actual)}) {{ return null; }}")
            actions["overload"] += 1
        return
    m = re.search(r"The import ([\w.]+) cannot be resolved", msg)
    if m:
        fqn = m.group(1)
        if fqn.rpartition(".")[2][:1].isupper():
            ensure_shell(fqn)
            actions["import_shell"] += 1
        return


def main():
    index_stubs()
    diags = parse(run_ecj())
    mod_diags = [d for d in diags if "/modcopy/" in d["path"]]
    actions = defaultdict(int)
    failures = []
    for d in mod_diags:
        try:
            handle(d, d["msg"], d["block"], actions)
        except Exception as e:  # keep converging even if one rule misfires
            failures.append((d["msg"][:70], str(e)[:50]))
    sweep_referenced_types()
    normalize_stubs()
    if failures:
        print("failures:", len(failures), failures[:3])
    print("actions:", dict(actions), "remaining_mod_errors:", len(mod_diags))


if __name__ == "__main__":
    main()
