package de.nr137.syncedshulkerboxes.mixin;

import net.minecraft.world.inventory.RemoteSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import de.nr137.syncedshulkerboxes.Boxes;

/**
 * After a click, the client reports a hash of each slot as it sees it: for a synced box with the glint, name and
 * contents only clients get (Boxes.forClient). The server compares that with its own stack; without this, the two
 * never match, every click sends the slots again and items seem to show up twice for a moment.
 */
@Mixin(RemoteSlot.Synchronized.class)
abstract class RemoteSlotMixin {
	@ModifyArg(method = "matches", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/network/HashedStack;matches(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/network/HashedPatchMap$HashGenerator;)Z"),
		index = 0)
	private ItemStack syncedShulkerBoxes$asTheClientSeesIt(ItemStack local) {
		return Boxes.forClient(local);
	}
}
