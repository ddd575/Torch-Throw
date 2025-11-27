package com.ddd.torchthrowmod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = TorchThrowMod.MODID, value = Dist.CLIENT)
public class TorchThrowModClient {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.THROWN_TORCH.get(),
                context -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(context, 1.0F, true));
    }
}