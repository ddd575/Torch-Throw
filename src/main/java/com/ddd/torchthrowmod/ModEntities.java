// ModEntities.java
package com.ddd.torchthrowmod;

import com.ddd.torchthrowmod.entity.ThrownTorchEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER =
            DeferredRegister.create(Registries.ENTITY_TYPE, TorchThrowMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<ThrownTorchEntity>> THROWN_TORCH =
            REGISTER.register("thrown_torch",
                    () -> EntityType.Builder.<ThrownTorchEntity>of(ThrownTorchEntity::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("thrown_torch"));
}