package dev.pulsey.mixin;

import dev.pulsey.module.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    /** Mouse look rotates the freecam instead of your real player. */
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void pulsey$turn(double yRot, double xRot, CallbackInfo ci) {
        Freecam fc = Freecam.get();
        if (fc != null && fc.isActive() && (Object) this == Minecraft.getInstance().player) {
            fc.turn(yRot, xRot);
            ci.cancel();
        }
    }
}
