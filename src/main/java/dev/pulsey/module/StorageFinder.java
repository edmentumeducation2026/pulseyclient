package dev.pulsey.module;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import dev.pulsey.setting.Setting;

public class StorageFinder extends BlockEspModule {
    private final Kind chest = kind("Chests", 0xFFAA00, true);
    private final Kind trapped = kind("Trapped Chests", 0xFF4040, true);
    private final Kind ender = kind("Ender Chests", 0xB44CFF, false);
    private final Kind barrel = kind("Barrels", 0xD9A066, true);
    private final Kind shulker = kind("Shulker Boxes", 0xFF6FD8, true);
    private final Kind dropper = kind("Droppers", 0x40D9FF, true);
    private final Kind dispenser = kind("Dispensers", 0x5C7CFF, false);
    private final Kind hopper = kind("Hoppers", 0xB0B0B0, false);

    private final Setting.Bool mergeDoubles;

    public StorageFinder() {
        super("StorageFinder", "Outlines visible chests, shulker boxes, droppers and other storage.");
        mergeDoubles = add(new Setting.Bool("Merge Double Chests", true));
        initCommon(0xFFAA00, 0.0);
    }

    @Override
    protected Kind classify(BlockEntity be) {
        // Order matters: subclasses before their parents.
        if (be instanceof TrappedChestBlockEntity) return trapped;
        if (be instanceof ChestBlockEntity) return chest;
        if (be instanceof EnderChestBlockEntity) return ender;
        if (be instanceof BarrelBlockEntity) return barrel;
        if (be instanceof ShulkerBoxBlockEntity) return shulker;
        if (be instanceof DropperBlockEntity) return dropper;
        if (be instanceof DispenserBlockEntity) return dispenser;
        if (be instanceof HopperBlockEntity) return hopper;
        return null;
    }

    @Override
    protected AABB boxFor(BlockEntity be, Kind kind) {
        BlockState st = be.getBlockState();
        BlockPos pos = be.getBlockPos();
        if (mergeDoubles.value && st.getBlock() instanceof ChestBlock && st.hasProperty(ChestBlock.TYPE)) {
            ChestType type = st.getValue(ChestBlock.TYPE);
            if (type != ChestType.SINGLE) {
                if (type == ChestType.RIGHT) return null; // the LEFT half draws the merged box
                BlockPos other = pos.relative(ChestBlock.getConnectedDirection(st));
                return new AABB(pos).minmax(new AABB(other));
            }
        }
        return new AABB(pos);
    }
}
