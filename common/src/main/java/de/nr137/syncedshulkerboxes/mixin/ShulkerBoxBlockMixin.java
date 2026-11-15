package de.nr137.syncedshulkerboxes.mixin;

import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import de.nr137.syncedshulkerboxes.Boxes;

/** Comparators next to a linked box read the group's shared contents; a broken box keeps its mark. */
@Mixin(ShulkerBoxBlock.class)
abstract class ShulkerBoxBlockMixin {
	@Inject(method = "getAnalogOutputSignal", at = @At("HEAD"), cancellable = true)
	private void syncedShulkerBoxes$signal(BlockState state, Level level, BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir) {
		if (level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box) {
			OptionalInt signal = Boxes.analogSignal(box);
			if (signal.isPresent()) {
				cir.setReturnValue(signal.getAsInt());
			}
		}
	}

	@Inject(method = "getDrops", at = @At("RETURN"))
	private void syncedShulkerBoxes$keepMark(BlockState state, LootParams.Builder params, CallbackInfoReturnable<List<ItemStack>> cir) {
		Boxes.keepMark(params.getOptionalParameter(LootContextParams.BLOCK_ENTITY), cir.getReturnValue());
	}
}
