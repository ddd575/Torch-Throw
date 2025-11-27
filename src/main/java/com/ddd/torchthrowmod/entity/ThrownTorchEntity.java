package com.ddd.torchthrowmod.entity;

import com.ddd.torchthrowmod.ModEntities;
import com.ddd.torchthrowmod.compat.TorchCompatManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class ThrownTorchEntity extends ThrowableItemProjectile {

    private ItemStack torchItem;

    public ThrownTorchEntity(EntityType<? extends ThrownTorchEntity> entityType, Level level) {
        super(entityType, level);
        this.torchItem = new ItemStack(Items.TORCH);
    }

    public ThrownTorchEntity(Level level, LivingEntity shooter, ItemStack torchItem) {
        super(ModEntities.THROWN_TORCH.get(), shooter, level);
        this.torchItem = (torchItem != null && !torchItem.isEmpty()) ?
                torchItem.copyWithCount(1) : new ItemStack(Items.TORCH);
    }

    @Override
    protected Item getDefaultItem() {
        return getTorchItem().getItem();
    }

    /**
     * 获取用于渲染的物品堆栈 - 始终返回普通火把
     */
    public ItemStack getItem() {
        // 始终返回普通火把用于渲染
        return new ItemStack(Items.TORCH);
    }

    /**
     * 获取实际投掷的火把物品（用于逻辑处理）
     */
    public ItemStack getTorchItem() {
        if (torchItem == null || torchItem.isEmpty()) {
            torchItem = new ItemStack(Items.TORCH);
        }
        return torchItem;
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);

        if (!this.level().isClientSide) {
            if (result.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockResult = (BlockHitResult) result;
                this.placeTorch(blockResult);
            } else if (result.getType() == HitResult.Type.ENTITY) {
                EntityHitResult entityResult = (EntityHitResult) result;
                this.igniteEntity(entityResult.getEntity());
            }

            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);

        if (!this.level().isClientSide) {
            Entity target = result.getEntity();
            if (target instanceof LivingEntity) {
                Vec3 knockbackDirection = target.position().subtract(this.position()).normalize();
                double knockbackStrength = 0.5;
                target.setDeltaMovement(
                        target.getDeltaMovement().add(
                                knockbackDirection.x * knockbackStrength,
                                0.3,
                                knockbackDirection.z * knockbackStrength
                        )
                );
                target.hurtMarked = true;
            }
        }
    }

    private void placeTorch(BlockHitResult result) {
        Level level = this.level();
        BlockPos hitPos = result.getBlockPos();
        Direction direction = result.getDirection();
        BlockPos placePos = hitPos.relative(direction);

        // 首先尝试在原始位置放置
        if (tryPlaceTorchWithCompat(level, hitPos, direction, placePos)) {
            return;
        }

        // 如果原始位置放置失败，搜索击中方块及其周围一圈（3x3x3区域）
        if (searchAndPlaceNearby(level, hitPos, direction)) {
            return;
        }

        // 如果附近也没有合适位置，掉落火把物品
        spawnTorchItem(level, placePos);
    }

    /**
     * 搜索击中面及其相邻的面（不包括对侧方向）的合适位置并尝试放置火把
     */
    private boolean searchAndPlaceNearby(Level level, BlockPos hitPos, Direction hitDirection) {
        // 获取击中面的对侧方向（需要排除的方向）
        Direction oppositeDirection = hitDirection.getOpposite();

        // 搜索击中面及其相邻的面（2D平面搜索，不包括对侧方向）
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    // 跳过击中位置本身
                    if (x == 0 && y == 0 && z == 0) continue;

                    BlockPos searchPos = hitPos.offset(x, y, z);

                    // 检查这个位置是否在击中面的对侧方向
                    if (isPositionInOppositeDirection(hitPos, searchPos, hitDirection, oppositeDirection)) {
                        continue; // 跳过对侧方向的方块
                    }

                    // 检查位置是否为空
                    if (!level.isEmptyBlock(searchPos)) continue;

                    // 尝试在所有可能的方向上放置火把
                    for (Direction dir : Direction.values()) {
                        BlockPos adjacentPos = searchPos.relative(dir.getOpposite());

                        // 检查相邻方块是否可放置火把
                        if (tryPlaceTorchWithCompat(level, adjacentPos, dir, searchPos)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 检查位置是否在击中面的对侧方向
     */
    private boolean isPositionInOppositeDirection(BlockPos hitPos, BlockPos searchPos, Direction hitDirection, Direction oppositeDirection) {
        // 计算从击中位置到搜索位置的向量
        int dx = searchPos.getX() - hitPos.getX();
        int dy = searchPos.getY() - hitPos.getY();
        int dz = searchPos.getZ() - hitPos.getZ();

        // 检查这个向量是否与对侧方向一致
        switch (oppositeDirection) {
            case DOWN: return dy < 0;
            case UP: return dy > 0;
            case NORTH: return dz < 0;
            case SOUTH: return dz > 0;
            case WEST: return dx < 0;
            case EAST: return dx > 0;
            default: return false;
        }
    }

    /**
     * 使用兼容管理器尝试放置火把
     */
    private boolean tryPlaceTorchWithCompat(Level level, BlockPos hitPos, Direction direction, BlockPos placePos) {

        BlockState state = level.getBlockState(placePos);

        if (!state.is(Blocks.SNOW)) {
            if (!state.isAir()) {
                return false;
            }
        }

        ItemStack torchStack = getTorchItem();
        Block torchBlock = TorchCompatManager.getTorchBlock(torchStack);
        if (torchBlock == null) {
            return false;
        }

        // 处理灯笼
        if (isLantern(torchBlock) && direction == Direction.DOWN) {
            BlockState lanternState = torchBlock.defaultBlockState();
            if (lanternState.hasProperty(LanternBlock.HANGING)) {
                lanternState = lanternState.setValue(LanternBlock.HANGING, true);
            }

            if (lanternState.canSurvive(level, placePos)) {
                level.setBlock(placePos, lanternState, 3);
                level.playSound(null, placePos, SoundEvents.LANTERN_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                return true;
            }
        }

        // 尝试放置倒置火把
        if (direction == Direction.DOWN) {
            Block ceilingTorchBlock = TorchCompatManager.getCeilingTorchBlock(torchStack);
            if (ceilingTorchBlock != null) {
                BlockState ceilingState = ceilingTorchBlock.defaultBlockState();
                if (ceilingState.canSurvive(level, placePos)) {
                    level.setBlock(placePos, ceilingState, 3);
                    level.playSound(null, placePos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                    return true;
                }
            }
        }

        // 尝试放置普通火把或墙上火把
        BlockState torchState = getTorchStateForDirection(direction, torchBlock);
        if (torchState != null && torchState.canSurvive(level, placePos)) {
            level.setBlock(placePos, torchState, 3);
            level.playSound(null, placePos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            return true;
        }

        return false;
    }

    /**
     * 检查是否是灯笼
     */
    private boolean isLantern(Block block) {
        if (block == null) return false;
        ResourceLocation blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        return blockId != null && blockId.getPath().contains("lantern");
    }

    /**
     * 根据碰撞方向获取合适的火把状态
     */
    private BlockState getTorchStateForDirection(Direction direction, Block torchBlock) {
        if (torchBlock == null) {
            return null;
        }

        if (direction == Direction.UP) {
            return torchBlock.defaultBlockState();
        }

        if (direction != Direction.DOWN) {
            Block wallTorchBlock = TorchCompatManager.getWallTorchBlock(getTorchItem());
            if (wallTorchBlock != null) {
                BlockState wallState = wallTorchBlock.defaultBlockState();
                if (wallState.hasProperty(net.minecraft.world.level.block.WallTorchBlock.FACING)) {
                    return wallState.setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, direction);
                }
                return wallState;
            }
        }

        return null;
    }

    /**
     * 在指定位置生成火把掉落物
     */
    private void spawnTorchItem(Level level, BlockPos pos) {
        if (!level.isClientSide) {
            ItemStack torchStack = getTorchItem();
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 1.0;
            double z = pos.getZ() + 0.5;

            ItemStack singleTorch = torchStack.copy();
            singleTorch.setCount(1);
            ItemEntity torchItemEntity = new ItemEntity(level, x, y, z, singleTorch);

            torchItemEntity.setDeltaMovement(
                    (level.random.nextDouble() - 0.5) * 0.1,
                    level.random.nextDouble() * 0.1,
                    (level.random.nextDouble() - 0.5) * 0.1
            );

            torchItemEntity.setDefaultPickUpDelay();
            level.addFreshEntity(torchItemEntity);

            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.2F,
                    (level.random.nextFloat() - level.random.nextFloat()) * 1.4F + 2.0F);
        }
    }

    /**
     * 点燃实体
     */
    private void igniteEntity(Entity entity) {
        if (entity instanceof LivingEntity) {
            entity.setRemainingFireTicks(100);
            this.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.5F, 1.0F);

            if (!this.level().isClientSide && this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                for (int i = 0; i < 5; i++) {
                    double x = entity.getX() + (this.random.nextDouble() - 0.5) * entity.getBbWidth();
                    double y = entity.getY() + this.random.nextDouble() * entity.getBbHeight();
                    double z = entity.getZ() + (this.random.nextDouble() - 0.5) * entity.getBbWidth();

                    serverLevel.sendParticles(
                            ParticleTypes.FLAME,
                            x, y, z,
                            1, 0, 0, 0, 0
                    );
                }
            }
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            // 使用普通火把的粒子效果
            SimpleParticleType particleType = ParticleTypes.FLAME;

            for (int i = 0; i < 2; ++i) {
                this.level().addParticle(
                        particleType,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.1,
                        this.getY() + 0.1,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.1,
                        0, 0, 0
                );
            }

            this.level().addParticle(ParticleTypes.SMOKE,
                    this.getX(), this.getY(), this.getZ(),
                    0, 0, 0);
        }
    }
}