package de.nr137.syncedshulkerboxes.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import de.nr137.syncedshulkerboxes.Boxes;

/** Placed boxes: the shared menu, no automation, the mark sent to clients (see Boxes). */
@Mixin(ShulkerBoxBlockEntity.class)
abstract class ShulkerBoxBlockEntityMixin extends BlockEntity implements Boxes.CacheHolder {
	private ShulkerBoxBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Unique
	private static final int[] NO_SLOTS = new int[0];

	@Unique
	private Boxes.@Nullable Cached syncedShulkerBoxes$cached;

	@Override
	public Boxes.@Nullable Cached syncedShulkerBoxes$cached() {
		return syncedShulkerBoxes$cached;
	}

	@Override
	public void syncedShulkerBoxes$setCached(Boxes.Cached cached) {
		syncedShulkerBoxes$cached = cached;
	}

	@Inject(method = "createMenu", at = @At("HEAD"), cancellable = true)
	private void syncedShulkerBoxes$createMenu(int containerId, Inventory inventory, CallbackInfoReturnable<AbstractContainerMenu> cir) {
		AbstractContainerMenu menu = Boxes.createMenu((ShulkerBoxBlockEntity) (Object) this, containerId, inventory);
		if (menu != null) {
			cir.setReturnValue(menu == Boxes.blockedMenu() ? null : menu);
		}
	}

	@Inject(method = "getSlotsForFace", at = @At("HEAD"), cancellable = true)
	private void syncedShulkerBoxes$noAutomation(Direction direction, CallbackInfoReturnable<int[]> cir) {
		if (Boxes.blocksAutomation((ShulkerBoxBlockEntity) (Object) this)) {
			cir.setReturnValue(NO_SLOTS);
		}
	}

	/** Vanilla sends clients nothing about a placed shulker box; a synced one sends its mark (Boxes.updateTag). */
	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return Boxes.isSynced((ShulkerBoxBlockEntity) (Object) this) ? ClientboundBlockEntityDataPacket.create(this) : null;
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return Boxes.updateTag((ShulkerBoxBlockEntity) (Object) this, registries);
	}
}
