package de.nr137.syncedshulkerboxes.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import de.nr137.syncedshulkerboxes.Boxes;

/**
 * A box item that despawns (5 minutes on the ground) is gone for good, as in vanilla: it leaves its group, and the
 * group's items are lost if it was the last box (Boxes.onLost). Picking up (empty stack) is no loss.
 */
@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {
	@Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;discard()V", ordinal = 1))
	private void syncedShulkerBoxes$despawn(CallbackInfo ci) {
		Boxes.onLost((ItemEntity) (Object) this);
	}
}
