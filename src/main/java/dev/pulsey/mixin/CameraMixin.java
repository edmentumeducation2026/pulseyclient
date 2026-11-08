package dev.pulsey.mixin;

import dev.pulsey.module.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yRot, float xRot);

    /** After vanilla has placed the camera, move it to the freecam position/rotation. */
    @Inject(method = "setup", at = @At("TAIL"))
    private void pulsey$freecam(CallbackInfo ci) {
        Freecam fc = Freecam.get();
        if (fc == null || !fc.isActive()) return;
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 p = fc.renderPos(pt);
        setPosition(p.x, p.y, p.z);
        setRotation(fc.yaw(), fc.pitch());
    }

    /** Treat the camera as detached so your own player model renders and the first-person hand hides. */
    @Inject(method = "isDetached", at = @At("HEAD"), cancellable = true)
    private void pulsey$detached(CallbackInfoReturnable<Boolean> cir) {
        Freecam fc = Freecam.get();
        if (fc != null && fc.isActive()) cir.setReturnValue(true);
    }
}
