package de.nr137.syncedshulkerboxes.client;

import java.util.function.Function;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import de.nr137.syncedshulkerboxes.mixin.client.RenderTypeAccessor;

/**
 * The render type for synced boxes with the glint: the box's texture and the glint in one pass. Vanilla has no such
 * type for cutout entity models (the shulker box texture has holes): its entity glint types are solid and would fill
 * them, and the armor's cutout glint type is faint (a coarse, slow pattern). This one is the armor's cutout pipeline
 * with the item glint texture and movement of vanilla's entity glint, like an enchanted sword.
 */
public final class GlintRenderType {
	private static final Function<Identifier, RenderType> TYPES = Util.memoize(texture -> RenderTypeAccessor.syncedShulkerBoxes$create(
		"synced_shulker_boxes_glint",
		RenderSetup.builder(RenderPipelines.ARMOR_CUTOUT_NO_CULL_GLINT)
			.withTexture("Sampler0", texture)
			.withTexture("GlintSampler", ItemFeatureRenderer.ENCHANTED_GLINT_ITEM)
			.setTextureTransform(TextureTransform.ENTITY_GLINT_TEXTURING)
			.useLightmap()
			.useOverlay()
			.affectsCrumbling()
			.setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
			.createRenderSetup()));

	private GlintRenderType() {
	}

	public static RenderType of(Identifier atlas) {
		return TYPES.apply(atlas);
	}
}
