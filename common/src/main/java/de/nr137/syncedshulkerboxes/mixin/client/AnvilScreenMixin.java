package de.nr137.syncedshulkerboxes.mixin.client;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import de.nr137.syncedshulkerboxes.SyncedBox;

/**
 * Only on clients with the mod: the anvil's name field. Two unlinked synced boxes start with "Synced Shulker Box" (the
 * name of the group about to be made), and a new text always shows from its start (vanilla keeps the field scrolled
 * where a longer text left it).
 */
@Mixin(AnvilScreen.class)
abstract class AnvilScreenMixin {
	@Shadow
	private EditBox name;

	@Inject(method = "slotChanged", at = @At("TAIL"))
	private void syncedShulkerBoxes$name(AbstractContainerMenu menu, int slotIndex, ItemStack stack, CallbackInfo ci) {
		if (slotIndex != 0 && slotIndex != 1) {
			return;
		}
		ItemStack first = menu.getSlot(0).getItem();
		ItemStack second = menu.getSlot(1).getItem();
		boolean bothUnlinked = SyncedBox.of(first).filter(mark -> !mark.isLinked()).isPresent()
			&& SyncedBox.of(second).filter(mark -> !mark.isLinked()).isPresent() && !first.has(DataComponents.CUSTOM_NAME);
		if (bothUnlinked && name.getValue().equals(first.getHoverName().getString())) {
			name.setValue("Synced Shulker Box");
		}
		if (slotIndex == 0 || bothUnlinked) {
			name.moveCursorToStart(false);
			name.moveCursorToEnd(false);
		}
	}
}
