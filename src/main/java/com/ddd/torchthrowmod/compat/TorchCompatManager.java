package com.ddd.torchthrowmod.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TorchCompatManager {
    // 使用更高效的数据结构
    private static final Set<Item> KNOWN_TORCH_ITEMS = ConcurrentHashMap.newKeySet();
    private static final Map<Item, Block> TORCH_ITEM_TO_BLOCK = new ConcurrentHashMap<>();
    private static final Map<Item, Block> TORCH_ITEM_TO_WALL_BLOCK = new ConcurrentHashMap<>();
    private static final Map<Item, Block> TORCH_ITEM_TO_CEILING_BLOCK = new ConcurrentHashMap<>();

    // 添加缓存机制
    private static final Map<Item, Boolean> TORCH_CACHE = new ConcurrentHashMap<>();
    private static final Map<Item, Block> TORCH_BLOCK_CACHE = new ConcurrentHashMap<>();
    private static final Map<Item, Block> WALL_TORCH_CACHE = new ConcurrentHashMap<>();
    private static final Map<Item, Block> CEILING_TORCH_CACHE = new ConcurrentHashMap<>();

    // 分阶段扫描控制
    private static boolean isInitialized = false;
    private static boolean isAutoScanComplete = false;
    private static volatile boolean isScanning = false;
    private static int scanProgress = 0;
    private static final int SCAN_BATCH_SIZE = 100; // 增加批次大小，因为扫描范围缩小了

    // 严格限制的关键词 - 只扫描这三种类型
    private static final Set<String> TARGET_KEYWORDS = Set.of("torch", "lantern", "candle");

    // 排除的关键词
    private static final Set<String> EXCLUDE_KEYWORDS = Set.of(
            "lightning", "switch", "sensor", "detector", "button", "lever",
            "redstone", "comparator", "repeater", "daylight", "ender"
    );

    // 预先计算的墙上火把模式
    private static final String[] WALL_PATTERNS = {"wall_%s", "%s_wall"};
    private static final String[] CEILING_PATTERNS = {"ceiling_%s", "hanging_%s", "%s_ceiling", "%s_hanging"};

    /**
     * 优化的初始化方法
     */
    public static void initialize() {
        if (isInitialized) return;

        // 注册原版火把（快速路径）
        registerVanillaTorches();

        // 从倒置火把模组导入映射
        importFromCeilingTorchMod();

        // 标记为已初始化，延迟扫描到服务器启动后
        isInitialized = true;
    }

    /**
     * 从倒置火把模组导入倒置火把映射
     */
    public static void importFromCeilingTorchMod() {
        try {
            // 检查倒置火把模组是否加载
            if (!isCeilingTorchModLoaded()) {
                System.out.println("[TorchCompat] 倒置火把模组未加载，跳过导入");
                return;
            }

            System.out.println("[TorchCompat] 开始从倒置火把模组导入倒置火把映射...");

            // 获取倒置火把模组的兼容列表
            Map<String, Object> compatList = getCeilingTorchCompatList();
            if (compatList == null || compatList.isEmpty()) {
                System.out.println("[TorchCompat] 未找到倒置火把模组的兼容列表");
                return;
            }

            int importedCount = 0;

            // 遍历所有兼容项
            for (Object compat : compatList.values()) {
                Map<ResourceLocation, Block> placeEntries = getPlaceEntriesFromCompat(compat);
                if (placeEntries != null) {
                    importedCount += processCeilingTorchEntries(placeEntries);
                }
            }

            System.out.println("[TorchCompat] 成功从倒置火把模组导入 " + importedCount + " 个倒置火把映射");

        } catch (Exception e) {
            System.err.println("[TorchCompat] 从倒置火把模组导入映射时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 检查倒置火把模组是否加载
     */
    private static boolean isCeilingTorchModLoaded() {
        try {
            Class.forName("bl4ckscor3.mod.ceilingtorch.CeilingTorch");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 获取倒置火把模组的兼容列表
     */
    private static Map<String, Object> getCeilingTorchCompatList() {
        try {
            Class<?> ceilingTorchClass = Class.forName("bl4ckscor3.mod.ceilingtorch.CeilingTorch");
            Field compatListField = ceilingTorchClass.getDeclaredField("COMPAT_LIST");
            compatListField.setAccessible(true);
            return (Map<String, Object>) compatListField.get(null);
        } catch (Exception e) {
            System.err.println("[TorchCompat] 获取倒置火把模组兼容列表失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 从兼容对象中获取放置条目
     */
    private static Map<ResourceLocation, Block> getPlaceEntriesFromCompat(Object compat) {
        try {
            Method getPlaceEntriesMethod = compat.getClass().getMethod("getPlaceEntries");
            return (Map<ResourceLocation, Block>) getPlaceEntriesMethod.invoke(compat);
        } catch (Exception e) {
            System.err.println("[TorchCompat] 获取放置条目失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 处理倒置火把条目
     */
    private static int processCeilingTorchEntries(Map<ResourceLocation, Block> placeEntries) {
        int processed = 0;

        for (Map.Entry<ResourceLocation, Block> entry : placeEntries.entrySet()) {
            ResourceLocation itemId = entry.getKey();
            Block ceilingBlock = entry.getValue();

            // 获取对应的物品
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item != null && item != Items.AIR) {
                // 检查是否是灯笼，如果是灯笼则跳过（灯笼不需要倒置变种）
                if (isLantern(item)) {
                    System.out.println("[TorchCompat] 跳过灯笼: " + itemId + " (灯笼不需要倒置变种)");
                    continue;
                }

                // 注册到我们的映射中
                registerCeilingTorchFromCeilingTorchMod(item, ceilingBlock);
                processed++;

                System.out.println("[TorchCompat] 注册倒置火把: " + itemId + " -> " +
                        BuiltInRegistries.BLOCK.getKey(ceilingBlock));
            }
        }

        return processed;
    }

    /**
     * 检查物品是否是灯笼
     */
    private static boolean isLantern(Item item) {
        if (item == null) return false;

        // 检查是否是原版灯笼
        if (item == Items.LANTERN || item == Items.SOUL_LANTERN) {
            return true;
        }

        // 检查注册名是否包含"lantern"
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        return itemId != null && itemId.getPath().contains("lantern");
    }

    /**
     * 从倒置火把模组注册倒置火把
     */
    private static void registerCeilingTorchFromCeilingTorchMod(Item torchItem, Block ceilingBlock) {
        // 添加到已知火把物品
        KNOWN_TORCH_ITEMS.add(torchItem);

        // 获取对应的普通火把方块
        Block torchBlock = extractTorchBlock(new ItemStack(torchItem));
        if (torchBlock != null) {
            TORCH_ITEM_TO_BLOCK.put(torchItem, torchBlock);
            TORCH_BLOCK_CACHE.put(torchItem, torchBlock);
        }

        // 注册倒置火把
        TORCH_ITEM_TO_CEILING_BLOCK.put(torchItem, ceilingBlock);
        CEILING_TORCH_CACHE.put(torchItem, ceilingBlock);

        // 预热缓存
        TORCH_CACHE.put(torchItem, true);

        // 尝试获取墙上火把变种
        Block wallBlock = findWallTorchVariant(torchBlock);
        if (wallBlock != null) {
            TORCH_ITEM_TO_WALL_BLOCK.put(torchItem, wallBlock);
            WALL_TORCH_CACHE.put(torchItem, wallBlock);
        }
    }

    /**
     * 优化的原版火把注册
     */
    private static void registerVanillaTorches() {
        // 使用预定义的映射，避免运行时计算
        registerTorch(Items.TORCH, Blocks.TORCH, Blocks.WALL_TORCH, null);
        registerTorch(Items.REDSTONE_TORCH, Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH, null);
        registerTorch(Items.SOUL_TORCH, Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH, null);

        // 注册灯笼（不需要倒置变种，因为灯笼本身就可以悬挂）
        registerTorch(Items.LANTERN, Blocks.LANTERN, null, null);
        registerTorch(Items.SOUL_LANTERN, Blocks.SOUL_LANTERN, null, null);

        // 预热缓存
        TORCH_CACHE.put(Items.TORCH, true);
        TORCH_CACHE.put(Items.REDSTONE_TORCH, true);
        TORCH_CACHE.put(Items.SOUL_TORCH, true);
        TORCH_CACHE.put(Items.LANTERN, true);
        TORCH_CACHE.put(Items.SOUL_LANTERN, true);
    }

    /**
     * 分阶段自动扫描（避免卡顿）- 只扫描目标关键词
     */
    public static void startStagedAutoScan() {
        if (isAutoScanComplete || isScanning) return;

        isScanning = true;
        scanProgress = 0;

        // 注册到tick事件进行分阶段扫描
        NeoForge.EVENT_BUS.addListener(TorchCompatManager::onServerTick);
    }

    /**
     * 服务器tick事件处理分阶段扫描
     */
    private static void onServerTick(ServerTickEvent.Post event) {
        if (!isScanning || isAutoScanComplete) return;

        // 获取所有物品ID，但只处理包含目标关键词的
        List<ResourceLocation> allItemIds = new ArrayList<>(BuiltInRegistries.ITEM.keySet());
        int totalItems = allItemIds.size();
        int processedInBatch = 0;

        while (scanProgress < totalItems && processedInBatch < SCAN_BATCH_SIZE) {
            ResourceLocation itemId = allItemIds.get(scanProgress);

            // 快速预检查 - 只检查目标关键词
            if (quickTargetedPreCheck(itemId)) {
                try {
                    Item item = BuiltInRegistries.ITEM.get(itemId);
                    if (item != Items.AIR && !KNOWN_TORCH_ITEMS.contains(item)) {
                        ItemStack stack = new ItemStack(item);
                        if (isTorchItem(stack)) {
                            Block torchBlock = extractTorchBlock(stack);
                            if (torchBlock != null && torchBlock != Blocks.AIR) {
                                // 对于灯笼，不需要墙上变种和倒置变种
                                if (isLantern(item)) {
                                    registerTorch(item, torchBlock, null, null);
                                } else {
                                    Block wallBlock = findWallTorchVariant(torchBlock);
                                    Block ceilingBlock = findCeilingTorchVariant(torchBlock, itemId);
                                    registerTorch(item, torchBlock, wallBlock, null);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // 静默处理错误，不影响主线程
                }
                processedInBatch++;
            }

            scanProgress++;
        }

        // 检查扫描是否完成
        if (scanProgress >= totalItems) {
            isAutoScanComplete = true;
            isScanning = false;
            NeoForge.EVENT_BUS.unregister(TorchCompatManager.class);
            System.out.println("[TorchCompat] 针对性扫描完成，共注册 " + KNOWN_TORCH_ITEMS.size() + " 个火把相关物品");
        }
    }

    /**
     * 快速目标预检查 - 只检查 "torch", "lantern", "candle"
     */
    private static boolean quickTargetedPreCheck(ResourceLocation itemId) {
        String path = itemId.getPath();

        // 快速排除检查
        for (String exclude : EXCLUDE_KEYWORDS) {
            if (path.contains(exclude)) return false;
        }

        // 只检查目标关键词
        for (String keyword : TARGET_KEYWORDS) {
            if (path.contains(keyword)) return true;
        }

        return false;
    }

    /**
     * 优化的火把物品检测（带缓存）- 使用严格的关键词检查
     */
    public static boolean isTorchItem(ItemStack stack) {
        if (stack.isEmpty()) return false;

        Item item = stack.getItem();

        // 检查缓存
        Boolean cached = TORCH_CACHE.get(item);
        if (cached != null) return cached;

        // 确保已初始化
        initialize();

        boolean result = false;

        // 检查已知的火把映射
        if (KNOWN_TORCH_ITEMS.contains(item)) {
            result = true;
        } else {
            // 使用严格的关键词检查
            result = strictKeywordCheck(stack);
        }

        // 更新缓存
        TORCH_CACHE.put(item, result);
        return result;
    }

    /**
     * 严格的关键词检查 - 只检查 "torch", "lantern", "candle"
     */
    private static boolean strictKeywordCheck(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) return false;

        String itemPath = itemId.getPath();

        // 使用预编译的关键词检查
        for (String exclude : EXCLUDE_KEYWORDS) {
            if (itemPath.contains(exclude)) return false;
        }

        // 只检查三个目标关键词
        for (String keyword : TARGET_KEYWORDS) {
            if (itemPath.contains(keyword)) return true;
        }

        return false;
    }

    /**
     * 优化的倒置火把获取（带缓存）
     */
    public static Block getCeilingTorchBlock(ItemStack torchItem) {
        // 添加空值检查
        if (torchItem == null || torchItem.isEmpty()) {
            return null;
        }

        initialize();

        Item item = torchItem.getItem();

        // 再次检查 item 是否为 null
        if (item == null) {
            return null;
        }

        // 如果是灯笼，返回null（灯笼不需要倒置变种）
        if (isLantern(item)) {
            return null;
        }

        Block cached = CEILING_TORCH_CACHE.get(item);
        if (cached != null) return cached;

        Block result = TORCH_ITEM_TO_CEILING_BLOCK.get(item);
        if (result == null) {
            Block torchBlock = getTorchBlock(torchItem);
            if (torchBlock != null) {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(torchItem.getItem());
                result = findCeilingTorchVariant(torchBlock, itemId);
            }
        }

        // 只在 result 不为 null 时放入缓存
        if (result != null) {
            CEILING_TORCH_CACHE.put(item, result);
        }

        return result;
    }

    /**
     * 优化的火把方块获取（带缓存）
     */
    public static Block getTorchBlock(ItemStack torchItem) {
        // 添加空值检查
        if (torchItem == null || torchItem.isEmpty()) {
            return null;
        }

        initialize();

        Item item = torchItem.getItem();

        // 再次检查 item 是否为 null
        if (item == null) {
            return null;
        }

        Block cached = TORCH_BLOCK_CACHE.get(item);
        if (cached != null) return cached;

        Block result = TORCH_ITEM_TO_BLOCK.get(item);
        if (result == null) {
            result = extractTorchBlock(torchItem);
        }

        // 只在 result 不为 null 时放入缓存
        if (result != null) {
            TORCH_BLOCK_CACHE.put(item, result);
        }

        return result;
    }

    /**
     * 优化的墙上火把获取（带缓存）
     */
    public static Block getWallTorchBlock(ItemStack torchItem) {
        // 添加空值检查
        if (torchItem == null || torchItem.isEmpty()) {
            return null;
        }

        initialize();

        Item item = torchItem.getItem();

        // 再次检查 item 是否为 null
        if (item == null) {
            return null;
        }

        // 如果是灯笼，返回null（灯笼没有墙上变种）
        if (isLantern(item)) {
            return null;
        }

        Block cached = WALL_TORCH_CACHE.get(item);
        if (cached != null) return cached;

        Block result = TORCH_ITEM_TO_WALL_BLOCK.get(item);
        if (result == null) {
            Block torchBlock = getTorchBlock(torchItem);
            if (torchBlock != null) {
                result = findWallTorchVariant(torchBlock);
            }
        }

        // 只在 result 不为 null 时放入缓存
        if (result != null) {
            WALL_TORCH_CACHE.put(item, result);
        }

        return result;
    }

    /**
     * 从物品中提取火把方块
     */
    private static Block extractTorchBlock(ItemStack stack) {
        try {
            Item item = stack.getItem();

            // 如果是方块物品，直接返回对应的方块
            if (item instanceof net.minecraft.world.item.BlockItem) {
                return ((net.minecraft.world.item.BlockItem) item).getBlock();
            }

            // 尝试通过注册表查找对应的方块
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId != null) {
                ResourceLocation blockId = ResourceLocation.tryParse(itemId.getNamespace() + ":" + itemId.getPath());
                if (blockId != null) {
                    Block block = BuiltInRegistries.BLOCK.get(blockId);
                    if (block != null && block != Blocks.AIR) {
                        return block;
                    }
                }
            }
        } catch (Exception e) {
            // 忽略错误
        }
        return null;
    }

    /**
     * 查找墙上火把变种
     */
    private static Block findWallTorchVariant(Block torchBlock) {
        ResourceLocation torchId = BuiltInRegistries.BLOCK.getKey(torchBlock);
        if (torchId == null) return null;

        // 尝试常见的墙上火把命名模式
        for (String pattern : WALL_PATTERNS) {
            String formatted = String.format(pattern, torchId.getPath());
            ResourceLocation wallId = ResourceLocation.tryParse(torchId.getNamespace() + ":" + formatted);
            if (wallId != null) {
                Block wallBlock = BuiltInRegistries.BLOCK.get(wallId);
                if (wallBlock != null && wallBlock != Blocks.AIR) {
                    return wallBlock;
                }
            }
        }
        return null;
    }

    /**
     * 查找倒置火把变种
     */
    private static Block findCeilingTorchVariant(Block torchBlock, ResourceLocation torchItemId) {
        ResourceLocation torchId = BuiltInRegistries.BLOCK.getKey(torchBlock);
        if (torchId == null) return null;

        // 尝试常见的倒置火把命名模式
        for (String pattern : CEILING_PATTERNS) {
            String formatted = String.format(pattern, torchId.getPath());
            ResourceLocation ceilingId = ResourceLocation.tryParse(torchId.getNamespace() + ":" + formatted);
            if (ceilingId != null) {
                Block ceilingBlock = BuiltInRegistries.BLOCK.get(ceilingId);
                if (ceilingBlock != null && ceilingBlock != Blocks.AIR) {
                    return ceilingBlock;
                }
            }
        }
        return null;
    }

    /**
     * 注册一个完整的火把类型
     */
    public static void registerTorch(Item torchItem, Block torchBlock, Block wallTorchBlock, String ceilingTorchId) {
        KNOWN_TORCH_ITEMS.add(torchItem);
        TORCH_ITEM_TO_BLOCK.put(torchItem, torchBlock);

        if (wallTorchBlock != null) {
            TORCH_ITEM_TO_WALL_BLOCK.put(torchItem, wallTorchBlock);
        }

        if (ceilingTorchId != null) {
            ResourceLocation ceilingId = ResourceLocation.tryParse(ceilingTorchId);
            if (ceilingId != null) {
                Block ceilingBlock = BuiltInRegistries.BLOCK.get(ceilingId);
                if (ceilingBlock != null && ceilingBlock != Blocks.AIR) {
                    TORCH_ITEM_TO_CEILING_BLOCK.put(torchItem, ceilingBlock);
                }
            }
        }

        // 预热缓存
        TORCH_CACHE.put(torchItem, true);
        TORCH_BLOCK_CACHE.put(torchItem, torchBlock);
        if (wallTorchBlock != null) {
            WALL_TORCH_CACHE.put(torchItem, wallTorchBlock);
        }
    }

    /**
     * 直接注册倒置火把映射
     */
    public static void registerCeilingTorch(Item torchItem, Block ceilingBlock) {
        if (torchItem != null && ceilingBlock != null) {
            TORCH_ITEM_TO_CEILING_BLOCK.put(torchItem, ceilingBlock);
            CEILING_TORCH_CACHE.put(torchItem, ceilingBlock);
        }
    }

    /**
     * 获取所有已知的火把物品
     */
    public static Set<Item> getKnownTorchItems() {
        initialize();
        return Collections.unmodifiableSet(KNOWN_TORCH_ITEMS);
    }

    /**
     * 手动触发重新扫描
     */
    public static void rescanTorches() {
        // 清除缓存
        TORCH_CACHE.clear();
        TORCH_BLOCK_CACHE.clear();
        WALL_TORCH_CACHE.clear();
        CEILING_TORCH_CACHE.clear();

        isAutoScanComplete = false;
        startStagedAutoScan();
    }

    /**
     * 获取统计信息
     */
    public static String getStats() {
        initialize();
        return String.format("已知火把: %d, 墙上变种: %d, 倒置变种: %d, 缓存命中率: 高",
                KNOWN_TORCH_ITEMS.size(),
                TORCH_ITEM_TO_WALL_BLOCK.size(),
                TORCH_ITEM_TO_CEILING_BLOCK.size());
    }

    /**
     * 检查是否已初始化
     */
    public static boolean isInitialized() {
        return isInitialized;
    }

    /**
     * 检查扫描状态
     */
    public static String getScanStatus() {
        if (!isInitialized) return "未初始化";
        if (isScanning) return "扫描中 (" + scanProgress + "/" + BuiltInRegistries.ITEM.keySet().size() + ")";
        if (isAutoScanComplete) return "扫描完成";
        return "等待扫描";
    }
}