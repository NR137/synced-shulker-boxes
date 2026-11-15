package de.nr137.syncedshulkerboxes.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import de.nr137.syncedshulkerboxes.Boxes;

/** A box item that falls into the void or is killed (/kill) is gone for good, as in vanilla (Boxes.onLost). */
@Mixin(Entity.class)
abstract class EntityMixin {
	@Inject(method = "onBelowWorld", at = @At("HEAD"))
	private void syncedShulkerBoxes$void(CallbackInfo ci) {
		if ((Object) this instanceof ItemEntity item) {
			Boxes.onLost(item);
		}
	}

	@Inject(method = "kill", at = @At("HEAD"))
	private void syncedShulkerBoxes$killed(ServerLevel level, CallbackInfo ci) {
		if ((Object) this instanceof ItemEntity item) {
			Boxes.onLost(item);
		}
	}
}
