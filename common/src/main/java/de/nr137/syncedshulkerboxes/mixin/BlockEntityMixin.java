package de.nr137.syncedshulkerboxes.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import de.nr137.syncedshulkerboxes.Boxes;

/**
 * Which shulker boxes are loaded on the server (Boxes.onServerTick looks after them). Kept here and not in the box's
 * own tick: performance mods (Lithium) let closed shulker boxes sleep, so their ticks can't be relied on.
 */
@Mixin(BlockEntity.class)
abstract class BlockEntityMixin {
	@Shadow
	protected @Nullable Level level;

	@Inject(method = "setLevel", at = @At("TAIL"))
	private void syncedShulkerBoxes$loaded(Level newLevel, CallbackInfo ci) {
		if ((Object) this instanceof ShulkerBoxBlockEntity box && newLevel instanceof ServerLevel serverLevel) {
			Boxes.loaded(serverLevel, box);
		}
	}

	@Inject(method = "clearRemoved", at = @At("TAIL"))
	private void syncedShulkerBoxes$reloaded(CallbackInfo ci) {
		if ((Object) this instanceof ShulkerBoxBlockEntity box && level instanceof ServerLevel serverLevel) {
			Boxes.loaded(serverLevel, box);
		}
	}
}
