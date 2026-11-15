package de.nr137.syncedshulkerboxes;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/**
 * Our mark on a shulker box, in its {@code custom_data} (which vanilla keeps and ignores):
 * {@code {synced_shulker_boxes: {}}} for a synced box that is not linked yet, and
 * {@code {synced_shulker_boxes: {group: "<uuid>", member: "<uuid>"}}} once it is linked. A placed box keeps the
 * mark in its block entity's components and gives it back to the item when it is broken.
 */
public record SyncedBox(Optional<Link> link) {
	public static final String KEY = SyncedShulkerBoxes.MOD_ID;

	/** A linked box: its group, and its own id, which says where it is in the group's order. */
	public record Link(UUID group, UUID member) {
	}

	public boolean isLinked() {
		return link.isPresent();
	}

	/** The mark of a stack, if it is a shulker box with our mark. */
	public static Optional<SyncedBox> of(ItemStack stack) {
		if (!isShulkerBox(stack)) {
			return Optional.empty();
		}
		return read(stack);
	}

	/** The mark of a placed box. */
	public static Optional<SyncedBox> of(ShulkerBoxBlockEntity box) {
		return read(box.components());
	}

	public static Optional<SyncedBox> read(DataComponentGetter components) {
		CustomData data = components.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return Optional.empty();
		}
		CompoundTag tag = data.copyTag();
		if (!(tag.get(KEY) instanceof CompoundTag mark)) {
			return Optional.empty();
		}
		Optional<UUID> group = mark.getString("group").flatMap(SyncedBox::uuid);
		Optional<UUID> member = mark.getString("member").flatMap(SyncedBox::uuid);
		if (group.isPresent() && member.isPresent()) {
			return Optional.of(new SyncedBox(Optional.of(new Link(group.get(), member.get()))));
		}
		return Optional.of(new SyncedBox(Optional.empty()));
	}

	/** Marks a stack as a linked member of a group. */
	public static void link(ItemStack stack, Link link) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag mark = new CompoundTag();
			mark.putString("group", link.group().toString());
			mark.putString("member", link.member().toString());
			tag.put(KEY, mark);
		});
	}

	/**
	 * A new synced box, not linked yet: what the smithing recipe makes (data/.../recipe/synced_shulker_box.json). Only
	 * the mark is saved; the glint and the name are added when the box is sent to a client (Boxes.forClient), so after
	 * removing the mod it is an ordinary shulker box.
	 */
	public static ItemStack newUnlinked() {
		ItemStack stack = new ItemStack(net.minecraft.world.item.Items.SHULKER_BOX);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(KEY, new CompoundTag()));
		return stack;
	}

	/**
	 * The name clients see, with the box's colour: "Syncable Red Shulker Box" from the smithing table, "Synced Red
	 * Shulker Box" once linked (its place in the group is in the tooltip).
	 */
	public static net.minecraft.network.chat.Component displayName(ItemStack stack, SyncedBox mark) {
		return net.minecraft.network.chat.Component.literal(mark.isLinked() ? "Synced " : "Syncable ")
			.append(net.minecraft.network.chat.Component.translatable(stack.getItem().getDescriptionId()));
	}

	/**
	 * The colour of the name: the box's own colour, lightened so dark ones (black, blue, brown) stay readable on the
	 * tooltip's dark background. Undyed boxes: the shulker's purple.
	 */
	public static int nameColor(ItemStack stack) {
		net.minecraft.world.item.DyeColor dye = stack.getItem() instanceof BlockItem item && item.getBlock() instanceof ShulkerBoxBlock box
			? box.getColor() : null;
		int rgb = (dye == null ? net.minecraft.world.item.DyeColor.PURPLE : dye).getTextureDiffuseColor() & 0xFFFFFF;
		int red = (rgb >> 16) & 0xFF;
		int green = (rgb >> 8) & 0xFF;
		int blue = rgb & 0xFF;
		red += (255 - red) * 35 / 100;
		green += (255 - green) * 35 / 100;
		blue += (255 - blue) * 35 / 100;
		return (red << 16) | (green << 8) | blue;
	}

	public static boolean isShulkerBox(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof BlockItem item && item.getBlock() instanceof ShulkerBoxBlock;
	}

	/** Whether a stack's own (saved) contents are empty. */
	public static boolean isEmptyBox(ItemStack stack) {
		ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		return contents.nonEmptyItemCopyStream().findAny().isEmpty();
	}

	private static Optional<UUID> uuid(String text) {
		try {
			return Optional.of(UUID.fromString(text));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
