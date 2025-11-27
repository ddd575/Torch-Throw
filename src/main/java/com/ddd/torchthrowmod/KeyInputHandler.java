package com.ddd.torchthrowmod;

import com.ddd.torchthrowmod.compat.TorchCompatManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TorchThrowMod.MODID, value = Dist.CLIENT)
public class KeyInputHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        // 检查是否按下了配置的投掷火把键
        if (player != null && minecraft.screen == null &&
                ModKeyBindings.THROW_TORCH_KEY.consumeClick()) {

            // 从背包中查找火把
            ItemStack torchStack = findTorchInInventory(player);
            if (!torchStack.isEmpty()) {
                // 发送网络包到服务端，执行投掷逻辑
                PacketDistributor.sendToServer(new ThrowTorchPacket(torchStack));

                // 客户端播放挥动动作
                player.swing(player.getUsedItemHand());
            }
        }
    }

    /**
     * 在背包中按顺序查找火把
     */
    private static ItemStack findTorchInInventory(Player player) {
        // 遍历所有物品栏槽位（0-35：主背包，36-39：快捷栏，40：副手）
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && TorchCompatManager.isTorchItem(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}