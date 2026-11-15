package de.nr137.syncedshulkerboxes.group;

import net.minecraft.world.Container;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/**
 * What a player sees when opening one placed member: the group's shared contents, while the lid, sound and reach
 * are those of the box they opened. Several players at several members all work on the same shared container, like
 * players at one chest, so nothing can be taken twice.
 */
public final class BoxView implements Container {
	private final Container shared;
	private final ShulkerBoxBlockEntity box;

	public BoxView(Group group, ShulkerBoxBlockEntity box) {
		this.shared = group.contents();
		this.box = box;
	}

	@Override
	public int getContainerSize() {
		return shared.getContainerSize();
	}

	@Override
	public boolean isEmpty() {
		return shared.isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		return shared.getItem(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		return shared.removeItem(slot, count);
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return shared.removeItemNoUpdate(slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		shared.setItem(slot, stack);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return shared.canPlaceItem(slot, stack);
	}

	@Override
	public void setChanged() {
		shared.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(box, player);
	}

	@Override
	public void clearContent() {
		shared.clearContent();
	}

	@Override
	public void startOpen(ContainerUser user) {
		box.startOpen(user);
	}

	@Override
	public void stopOpen(ContainerUser user) {
		box.stopOpen(user);
	}
}
