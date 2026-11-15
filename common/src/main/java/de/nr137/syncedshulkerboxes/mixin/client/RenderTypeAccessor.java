package de.nr137.syncedshulkerboxes.mixin.client;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** RenderType.create isn't public; GlintRenderType builds the glint render type for synced boxes with it. */
@Mixin(RenderType.class)
public interface RenderTypeAccessor {
	@Invoker("create")
	static RenderType syncedShulkerBoxes$create(String name, RenderSetup setup) {
		throw new AssertionError();
	}
}
