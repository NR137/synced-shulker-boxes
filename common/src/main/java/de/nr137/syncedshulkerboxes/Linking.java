package de.nr137.syncedshulkerboxes;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import de.nr137.syncedshulkerboxes.group.Group;
import de.nr137.syncedshulkerboxes.group.Groups;

/**
 * Linking at the anvil (docs/design.md, "Getting one"):
 * <ul>
 * <li>two synced boxes, both empty and not linked: a new group, the left box is the older one;</li>
 * <li>a linked box (left) and an empty synced box that is not linked (right): the right one joins the left one's
 * group, up to the maximum.</li>
 * </ul>
 * Both boxes come out: the left one as the anvil's result, the right one is put back into its slot, changed.
 */
public final class Linking {
	private Linking() {
	}

	/**
	 * What taking the anvil's result does. {@code result} is the box from the second slot, linked, which the player
	 * takes; {@code left} is the box from the first slot as it goes back into its slot.
	 */
	public record Plan(ItemStack left, ItemStack result, int cost, UUID group, @Nullable String name,
			@Nullable UUID newGroupFirst, UUID joining) {
		public boolean createsGroup() {
			return newGroupFirst != null;
		}
	}

	/** The plan for the anvil's two inputs, if they are a linking case this player may do. */
	public static Optional<Plan> plan(ServerPlayer player, ItemStack left, ItemStack right, @Nullable String name) {
		Optional<SyncedBox> leftMark = SyncedBox.of(left);
		Optional<SyncedBox> rightMark = SyncedBox.of(right);
		if (leftMark.isEmpty() || rightMark.isEmpty() || rightMark.get().isLinked() || !SyncedBox.isEmptyBox(right)
				|| left.getCount() != 1 || right.getCount() != 1) {
			return Optional.empty();
		}
		if (!Permission.LINK.check(player)) {
			player.sendSystemMessage(Component.literal("You may not link synced shulker boxes."), true);
			return Optional.empty();
		}
		Config config = SyncedShulkerBoxes.config();
		int costPerBox = Math.max(1, config.linkCostLevels());
		UUID joining = UUID.randomUUID();
		ItemStack leftResult = left.copy();
		ItemStack result = right.copy();
		// The anvil's name field starts with the first box's name; left unchanged, the client sends no name.
		String typed = StringUtil.isBlank(name) ? null : name.strip();
		if (leftMark.get().link().isPresent()) {
			// Adding a box: only the new one (second slot) changes. It gets a name typed now, or else the first box's
			// name (or the group's name).
			SyncedBox.Link link = leftMark.get().link().get();
			Optional<Group> group = Groups.get(player.level().getServer()).group(link.group())
				.filter(found -> found.contains(link.member()));
			if (group.isEmpty()) {
				return Optional.empty();
			}
			if (group.get().members().size() >= config.maxGroupSize()) {
				player.sendSystemMessage(Component.literal("This group already has " + config.maxGroupSize() + " boxes."), true);
				return Optional.empty();
			}
			SyncedBox.link(result, new SyncedBox.Link(link.group(), joining));
			Component leftName = left.get(DataComponents.CUSTOM_NAME);
			Component newName = typed != null ? Component.literal(typed)
				: leftName != null ? leftName : group.get().name() != null ? Component.literal(group.get().name()) : null;
			if (newName != null) {
				result.set(DataComponents.CUSTOM_NAME, newName);
			}
			return Optional.of(new Plan(leftResult, result, costPerBox, link.group(), null, null, joining));
		}
		if (!SyncedBox.isEmptyBox(left)) {
			return Optional.empty();
		}
		// A new group: a typed name goes onto both boxes and becomes the group's name.
		UUID group = Groups.get(player.level().getServer()).newGroupId();
		UUID first = UUID.randomUUID();
		SyncedBox.link(leftResult, new SyncedBox.Link(group, first));
		SyncedBox.link(result, new SyncedBox.Link(group, joining));
		if (typed != null) {
			leftResult.set(DataComponents.CUSTOM_NAME, Component.literal(typed));
			result.set(DataComponents.CUSTOM_NAME, Component.literal(typed));
		}
		return Optional.of(new Plan(leftResult, result, 2 * costPerBox, group, typed, first, joining));
	}

	/** Taking the result: the group is created or joined. Returns false if that is no longer possible. */
	public static boolean apply(ServerPlayer player, Plan plan) {
		Groups groups = Groups.get(player.level().getServer());
		if (plan.createsGroup()) {
			groups.create(plan.group(), plan.name(), plan.newGroupFirst(), plan.joining());
			return true;
		}
		Optional<Group> group = groups.group(plan.group());
		if (group.isEmpty() || group.get().members().size() >= SyncedShulkerBoxes.config().maxGroupSize()) {
			return false;
		}
		groups.addMember(group.get(), plan.joining());
		return true;
	}

	/**
	 * The smithing table's result for a synced box: only an empty shulker box without our mark may become one, and
	 * only for players with the permission.
	 */
	public static boolean maySmith(ServerPlayer player, ItemStack base) {
		return SyncedBox.of(base).isEmpty() && SyncedBox.isEmptyBox(base) && Permission.CRAFT.check(player);
	}
}
