package com.ddd.torchthrowmod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ToggleThrowManager {

    // 存储启用了投掷功能的玩家
    private static final Set<UUID> PLAYERS_WITH_THROW_ENABLED = new HashSet<>();

    /**
     * 切换玩家的投掷状态
     */
    public static void toggleThrowForPlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        ItemStack heldItem = player.getMainHandItem();

        if (heldItem.isEmpty()) {
            // 玩家手中没有物品
            player.sendSystemMessage(Component.translatable("message.torchthrowmod.no_item"));
            return;
        }

        boolean wasEnabled = PLAYERS_WITH_THROW_ENABLED.contains(playerId);

        if (wasEnabled) {
            // 禁用投掷：恢复原版右键放置行为
            PLAYERS_WITH_THROW_ENABLED.remove(playerId);
            player.sendSystemMessage(Component.translatable("message.torchthrowmod.throw_disabled"));
        } else {
            // 启用投掷：右键投掷火把，取消原版放置行为
            PLAYERS_WITH_THROW_ENABLED.add(playerId);
            player.sendSystemMessage(Component.translatable("message.torchthrowmod.throw_enabled"));
        }
    }

    /**
     * 检查玩家是否启用了投掷功能
     */
    public static boolean isThrowEnabledForPlayer(ServerPlayer player) {
        return PLAYERS_WITH_THROW_ENABLED.contains(player.getUUID());
    }

    /**
     * 检查特定物品是否可以投掷
     */
    public static boolean canThrowItem(ServerPlayer player, ItemStack itemStack) {
        // 首先检查玩家是否启用了投掷功能
        if (!isThrowEnabledForPlayer(player)) {
            return false;
        }

        // 然后检查物品是否是火把
        return com.ddd.torchthrowmod.compat.TorchCompatManager.isTorchItem(itemStack);
    }
}