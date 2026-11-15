package de.nr137.syncedshulkerboxes.mixin.client;

import net.minecraft.client.renderer.blockentity.state.ShulkerBoxRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import de.nr137.syncedshulkerboxes.client.ClientGlint;

/** Whether a placed box is drawn with the glint (ClientGlint). */
@Mixin(ShulkerBoxRenderState.class)
abstract class ShulkerBoxRenderStateMixin implements ClientGlint.Holder {
	@Unique
	private boolean syncedShulkerBoxes$glint;

	@Override
	public boolean syncedShulkerBoxes$glint() {
		return syncedShulkerBoxes$glint;
	}

	@Override
	public void syncedShulkerBoxes$setGlint(boolean glint) {
		syncedShulkerBoxes$glint = glint;
	}
}
