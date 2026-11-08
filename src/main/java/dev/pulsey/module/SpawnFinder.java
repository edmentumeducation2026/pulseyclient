package dev.pulsey.module;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;

public class SpawnFinder extends BlockEspModule {
    private final Kind spawner = kind("Spawners", 0x3DFF7A, true);
    private final Kind trial = kind("Trial Spawners", 0xFFC83D, true);

    public SpawnFinder() {
        super("SpawnFinder", "Outlines visible mob spawners and trial spawners.");
        initCommon(0x3DFF7A, 1.0);
    }

    @Override
    protected Kind classify(BlockEntity be) {
        if (be instanceof SpawnerBlockEntity) return spawner;
        if (be instanceof TrialSpawnerBlockEntity) return trial;
        return null;
    }
}
