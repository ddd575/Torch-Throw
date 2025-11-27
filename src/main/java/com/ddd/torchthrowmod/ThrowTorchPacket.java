package com.ddd.torchthrowmod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ThrowTorchPacket(ItemStack torchStack) implements CustomPacketPayload {

    // 定义 Type
    public static final Type<ThrowTorchPacket> TYPE = new Type<>(ResourceLocation.parse(TorchThrowMod.MODID + ":throw_torch"));

    // 使用正确的 StreamCodec 创建方式
    public static final StreamCodec<FriendlyByteBuf, ThrowTorchPacket> STREAM_CODEC = new StreamCodec<FriendlyByteBuf, ThrowTorchPacket>() {
        @Override
        public ThrowTorchPacket decode(FriendlyByteBuf buf) {
            return new ThrowTorchPacket(buf);
        }

        @Override
        public void encode(FriendlyByteBuf buf, ThrowTorchPacket packet) {
            packet.write(buf);
        }
    };

    public ThrowTorchPacket(FriendlyByteBuf buf) {
        this(ItemStack.STREAM_CODEC.decode((RegistryFriendlyByteBuf) buf));
    }

    public void write(FriendlyByteBuf buf) {
        ItemStack.STREAM_CODEC.encode((RegistryFriendlyByteBuf) buf, torchStack);
    }

    @Override
    public Type<ThrowTorchPacket> type() {
        return TYPE;
    }

    public static void handle(ThrowTorchPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                // 在服务端执行投掷逻辑
                throwTorchFromInventory(serverPlayer, packet.torchStack);
            }
        });
    }

    /**
     * 从背包投掷火把 - 服务端逻辑
     */
    private static void throwTorchFromInventory(ServerPlayer player, ItemStack torchStack) {
        if (!player.level().isClientSide) {
            // 创建投掷火把实体
            com.ddd.torchthrowmod.entity.ThrownTorchEntity torchEntity =
                    new com.ddd.torchthrowmod.entity.ThrownTorchEntity(player.level(), player, torchStack);

            // 设置投掷位置和方向
            torchEntity.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
            torchEntity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.2F, 1.0F);

            // 添加到世界
            player.level().addFreshEntity(torchEntity);

            // 消耗火把（非创造模式）
            if (!player.isCreative()) {
                // 找到并消耗背包中的火把
                consumeTorchFromInventory(player, torchStack.getItem());
            }

            // 设置冷却时间
            player.getCooldowns().addCooldown(torchStack.getItem(), 10); // 0.5秒冷却
        }
    }

    /**
     * 从背包中消耗火把
     */
    private static void consumeTorchFromInventory(ServerPlayer player, net.minecraft.world.item.Item torchItem) {
        // 遍历背包找到火把并消耗
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == torchItem) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                }
                break;
            }
        }
    }
}