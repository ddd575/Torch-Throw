package com.ddd.torchthrowmod;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(TorchThrowMod.MODID)
public class TorchThrowMod {
    public static final String MODID = "torch_throw_mod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TorchThrowMod(IEventBus modEventBus) {
        // 注册实体
        ModEntities.REGISTER.register(modEventBus);

        // 注册事件监听器
        NeoForge.EVENT_BUS.register(new PlayerInteractHandler());

        // 注册网络包事件
        modEventBus.addListener(this::registerPackets);

        LOGGER.info("Torch Throw Mod loaded!");

        // 使用延迟初始化，避免在构造函数中执行复杂操作
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
    }

    private void registerPackets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        final net.neoforged.neoforge.network.registration.PayloadRegistrar registrar = event.registrar("1.0");

        // 使用正确的网络包注册方式
        registrar.playToServer(
                ThrowTorchPacket.TYPE,
                ThrowTorchPacket.STREAM_CODEC,
                ThrowTorchPacket::handle
        );
    }

    private void onServerStarting(net.neoforged.neoforge.event.server.ServerStartingEvent event) {
        // 在服务器启动时初始化火把兼容管理器
        try {
            com.ddd.torchthrowmod.compat.TorchCompatManager.initialize();
            com.ddd.torchthrowmod.compat.TorchCompatManager.startStagedAutoScan();
            LOGGER.info("火把兼容管理器初始化完成，开始针对性扫描");
        } catch (Exception e) {
            LOGGER.error("火把兼容管理器初始化失败", e);
        }
    }
}