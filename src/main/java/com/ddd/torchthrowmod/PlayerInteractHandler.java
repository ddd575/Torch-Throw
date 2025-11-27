// PlayerInteractHandler.java
package com.ddd.torchthrowmod;

import com.ddd.torchthrowmod.compat.TorchCompatManager;
import com.ddd.torchthrowmod.entity.ThrownTorchEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class PlayerInteractHandler {

    private static final int COOLDOWN_TICKS = 10; // 2秒冷却时间 (20 ticks/秒 * 2秒 = 40 ticks)

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        ItemStack itemStack = player.getItemInHand(hand);

        // 检查玩家是否手持火把并且不在冷却状态
        if (isTorchItem(itemStack) && !player.getCooldowns().isOnCooldown(itemStack.getItem())) {
            // 取消原版右键放置行为
            event.setCancellationResult(InteractionResult.FAIL);

            if (!player.level().isClientSide) {
                // 创建投掷火把实体 - 传递实际的物品堆栈
                ItemStack thrownStack = itemStack.copy();
                thrownStack.setCount(1); // 只投掷一个

                ThrownTorchEntity torchEntity = new ThrownTorchEntity(player.level(), player, thrownStack);

                // 设置投掷位置（从玩家眼睛位置出发）
                Vec3 eyePos = player.getEyePosition();
                torchEntity.setPos(eyePos.x, eyePos.y - 0.1, eyePos.z);

                // 设置投掷方向和速度
                Vec3 lookVec = player.getLookAngle();
                float speed = 1.2F; // 固定速度
                torchEntity.shoot(lookVec.x, lookVec.y, lookVec.z, speed, 1.0F);

                // 添加到世界
                player.level().addFreshEntity(torchEntity);

                // 消耗火把（可选）
                if (!player.isCreative()) {
                    itemStack.shrink(1);
                }

                // 设置2秒冷却时间
                player.getCooldowns().addCooldown(itemStack.getItem(), COOLDOWN_TICKS);

                // 播放投掷音效
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.5F, 0.4F / (player.getRandom().nextFloat() * 0.4F + 0.8F));
            }

            // 播放投掷动作
            player.swing(hand);
        }
    }

    /**
     * 检查物品是否是火把
     */
    private boolean isTorchItem(ItemStack stack) {
        return TorchCompatManager.isTorchItem(stack);
    }
}