package de.nr137.syncedshulkerboxes.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import de.nr137.syncedshulkerboxes.Boxes;

/** A destroyed linked box drops nothing while its group has other members (Boxes.onDestroyed). */
@Mixin(BlockItem.class)
abstract class BlockItemMixin {
	@Inject(method = "onDestroyed", at = @At("HEAD"), cancellable = true)
	private void syncedShulkerBoxes$destroyed(ItemEntity entity, CallbackInfo ci) {
		if (Boxes.onDestroyed(entity)) {
			ci.cancel();
		}
	}
}
