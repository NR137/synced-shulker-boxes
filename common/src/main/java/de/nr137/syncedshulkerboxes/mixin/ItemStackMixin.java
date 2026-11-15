package de.nr137.syncedshulkerboxes.mixin;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import de.nr137.syncedshulkerboxes.Boxes;

/**
 * Item stacks sent over the network: a linked box shows its group's contents (Boxes.forClient). Wraps the codecs
 * vanilla builds for every item stack in a packet; decoding is unchanged.
 */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
	@Inject(method = "createOptionalStreamCodec", at = @At("RETURN"), cancellable = true)
	private static void syncedShulkerBoxes$wrap(StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> patchCodec,
			CallbackInfoReturnable<StreamCodec<RegistryFriendlyByteBuf, ItemStack>> cir) {
		StreamCodec<RegistryFriendlyByteBuf, ItemStack> vanilla = cir.getReturnValue();
		cir.setReturnValue(new StreamCodec<>() {
			@Override
			public ItemStack decode(RegistryFriendlyByteBuf input) {
				return vanilla.decode(input);
			}

			@Override
			public void encode(RegistryFriendlyByteBuf output, ItemStack stack) {
				vanilla.encode(output, Boxes.forClient(stack));
			}
		});
	}
}
