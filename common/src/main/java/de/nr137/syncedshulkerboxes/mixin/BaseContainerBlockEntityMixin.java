package de.nr137.syncedshulkerboxes.mixin;

import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import de.nr137.syncedshulkerboxes.Boxes;

/** The title of a linked box's screen: its group's name, like a named chest's (Boxes.title). */
@Mixin(BaseContainerBlockEntity.class)
abstract class BaseContainerBlockEntityMixin {
	@Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
	private void syncedShulkerBoxes$title(CallbackInfoReturnable<Component> cir) {
		if ((Object) this instanceof ShulkerBoxBlockEntity box) {
			Optional<Component> title = Boxes.title(box);
			title.ifPresent(cir::setReturnValue);
		}
	}
}
