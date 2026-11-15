package de.nr137.syncedshulkerboxes.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.ShulkerBoxSpecialRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import de.nr137.syncedshulkerboxes.client.ClientGlint;

/** A shulker box item: vanilla gets the glint flag here and ignores it (MC-69683); it is passed on (ClientGlint). */
@Mixin(ShulkerBoxSpecialRenderer.class)
abstract class ShulkerBoxSpecialRendererMixin {
	@Inject(method = "submit", at = @At("HEAD"))
	private void syncedShulkerBoxes$start(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords,
			boolean hasFoil, int outlineColor, CallbackInfo ci) {
		ClientGlint.set(hasFoil);
	}

	@Inject(method = "submit", at = @At("RETURN"))
	private void syncedShulkerBoxes$end(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords,
			boolean hasFoil, int outlineColor, CallbackInfo ci) {
		ClientGlint.set(false);
	}
}
