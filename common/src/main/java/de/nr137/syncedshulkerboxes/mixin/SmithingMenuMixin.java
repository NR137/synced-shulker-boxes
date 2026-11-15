package de.nr137.syncedshulkerboxes.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import de.nr137.syncedshulkerboxes.Linking;
import de.nr137.syncedshulkerboxes.SyncedBox;

/** The smithing recipe for synced boxes takes only empty boxes without our mark, for players with the permission. */
@Mixin(SmithingMenu.class)
abstract class SmithingMenuMixin extends ItemCombinerMenu {
	private SmithingMenuMixin(@Nullable MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access,
			ItemCombinerMenuSlotDefinition slots) {
		super(type, id, inventory, access, slots);
	}

	@Inject(method = "createResult", at = @At("TAIL"))
	private void syncedShulkerBoxes$createResult(CallbackInfo ci) {
		ItemStack result = resultSlots.getItem(0);
		if (!(player instanceof ServerPlayer serverPlayer) || SyncedBox.of(result).isEmpty()) {
			return;
		}
		if (!Linking.maySmith(serverPlayer, inputSlots.getItem(SmithingMenu.BASE_SLOT))) {
			resultSlots.setItem(0, ItemStack.EMPTY);
		}
	}
}
