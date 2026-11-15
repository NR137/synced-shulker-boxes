package de.nr137.syncedshulkerboxes.mixin;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import de.nr137.syncedshulkerboxes.Linking;

/** Linking synced boxes at the anvil (Linking). */
@Mixin(AnvilMenu.class)
abstract class AnvilMenuMixin extends ItemCombinerMenu {
	@Shadow
	@Final
	private DataSlot cost;
	@Shadow
	private int repairItemCountCost;
	@Shadow
	private @Nullable String itemName;
	@Shadow
	private boolean onlyRenaming;

	@Unique
	private Linking.@Nullable Plan syncedShulkerBoxes$plan;
	/** The plan when the result is taken; vanilla's onTake clears the slots, which computes a new (empty) result. */
	@Unique
	private Linking.@Nullable Plan syncedShulkerBoxes$taking;
	/** Whether the anvil's whole state is to be sent again at the next broadcast. */
	@Unique
	private boolean syncedShulkerBoxes$resend;

	/**
	 * The client works out the anvil's result itself too and finds none for two shulker boxes (after putting a box in,
	 * after typing a name). The server's result and cost don't change then, so vanilla wouldn't send them again: they
	 * are sent once the click or rename is fully handled.
	 */
	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		if (syncedShulkerBoxes$resend) {
			syncedShulkerBoxes$resend = false;
			sendAllDataToRemote();
		}
	}

	private AnvilMenuMixin(@Nullable MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access,
			ItemCombinerMenuSlotDefinition slots) {
		super(type, id, inventory, access, slots);
	}

	@Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
	private void syncedShulkerBoxes$createResult(CallbackInfo ci) {
		syncedShulkerBoxes$plan = null;
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		Optional<Linking.Plan> plan = Linking.plan(serverPlayer, inputSlots.getItem(0), inputSlots.getItem(1), itemName);
		if (plan.isPresent()) {
			syncedShulkerBoxes$plan = plan.get();
			repairItemCountCost = 0;
			// Left over from an earlier result (e.g. only the first box in, with a name): it would keep vanilla's
			// onTake from using up the second slot, so the second box could be taken again.
			onlyRenaming = false;
			cost.set(plan.get().cost());
			resultSlots.setItem(0, plan.get().result());
			syncedShulkerBoxes$resend = true;
			ci.cancel();
		}
	}

	@Inject(method = "onTake", at = @At("HEAD"))
	private void syncedShulkerBoxes$beforeTake(Player taker, ItemStack carried, CallbackInfo ci) {
		syncedShulkerBoxes$taking = syncedShulkerBoxes$plan;
	}

	@Inject(method = "onTake", at = @At("TAIL"))
	private void syncedShulkerBoxes$onTake(Player taker, ItemStack carried, CallbackInfo ci) {
		Linking.Plan plan = syncedShulkerBoxes$taking;
		syncedShulkerBoxes$taking = null;
		if (plan != null && taker instanceof ServerPlayer serverPlayer && Linking.apply(serverPlayer, plan)) {
			// The player took the second box, linked; the anvil used up the first one, which goes back into its slot.
			// The name field now shows the first box's name, as it does for any item put in: left as typed, vanilla
			// would offer to rename the first box (or, empty, to remove its name), showing a copy of it as the next
			// result.
			itemName = plan.left().getHoverName().getString();
			inputSlots.setItem(1, ItemStack.EMPTY);
			inputSlots.setItem(0, plan.left());
		}
	}
}
