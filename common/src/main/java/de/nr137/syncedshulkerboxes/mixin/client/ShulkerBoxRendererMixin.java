package de.nr137.syncedshulkerboxes.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.renderer.blockentity.state.ShulkerBoxRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import de.nr137.syncedshulkerboxes.Boxes;
import de.nr137.syncedshulkerboxes.client.AmbientParticles;
import de.nr137.syncedshulkerboxes.client.ClientGlint;
import de.nr137.syncedshulkerboxes.client.GlintRenderType;

/**
 * Draws synced boxes, placed or as items, with the glint (ClientGlint): vanilla's model in one pass with
 * GlintRenderType.
 */
@Mixin(ShulkerBoxRenderer.class)
abstract class ShulkerBoxRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/level/block/entity/ShulkerBoxBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/ShulkerBoxRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
		at = @At("TAIL"))
	private void syncedShulkerBoxes$extract(ShulkerBoxBlockEntity box, ShulkerBoxRenderState state, float partialTicks, Vec3 camera,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress, CallbackInfo ci) {
		boolean synced = Boxes.isSynced(box);
		((ClientGlint.Holder) state).syncedShulkerBoxes$setGlint(synced);
		if (synced) {
			AmbientParticles.maybeSpawn(box);
		}
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/ShulkerBoxRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("HEAD"))
	private void syncedShulkerBoxes$placedStart(ShulkerBoxRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera, CallbackInfo ci) {
		ClientGlint.set(((ClientGlint.Holder) state).syncedShulkerBoxes$glint());
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/ShulkerBoxRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At("RETURN"))
	private void syncedShulkerBoxes$placedEnd(ShulkerBoxRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera, CallbackInfo ci) {
		ClientGlint.set(false);
	}

	/** The box's own drawing call, with the glint if ClientGlint says so. */
	@Redirect(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIFLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Lnet/minecraft/client/resources/model/sprite/SpriteId;I)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;IIILnet/minecraft/client/resources/model/sprite/SpriteId;Lnet/minecraft/client/resources/model/sprite/SpriteGetter;I)V"))
	private void syncedShulkerBoxes$glint(SubmitNodeCollector collector, Model<Object> model, Object state, PoseStack poseStack, int lightCoords,
			int overlayCoords, int tint, SpriteId sprite, SpriteGetter sprites, int outlineColor) {
		if (!ClientGlint.active()) {
			collector.submitModel(model, state, poseStack, lightCoords, overlayCoords, tint, sprite, sprites, outlineColor);
			return;
		}
		// Vanilla's model and texture, drawn with the glint in the same pass (GlintRenderType): cutout, so the gaps in
		// the lid's lower edge and the base's teeth that fill them stay as they are. A separate glint layer, or a solid
		// glint type, ignores those holes and made the band where lid and base overlap flicker.
		collector.submitModel(model, state, poseStack, GlintRenderType.of(sprite.atlasLocation()), lightCoords, overlayCoords,
			tint, sprites.get(sprite), outlineColor);
	}
}
