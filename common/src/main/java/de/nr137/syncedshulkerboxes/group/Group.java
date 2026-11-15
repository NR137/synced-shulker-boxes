package de.nr137.syncedshulkerboxes.group;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Linked boxes with one shared inventory. The members are kept in the order they joined; the first one still there is
 * the anchor, which also holds a copy of the contents in vanilla's own box contents (see docs/design.md).
 */
public final class Group {
	private final UUID id;
	private final List<UUID> members;
	private final Contents contents;
	private final Groups owner;
	/** The name given when the group was made, which boxes joining later get by default; null: none. */
	private @Nullable String name;
	/** Letters for codes: Crockford's base 32, without I, L, O and U, which are easy to confuse. */
	private static final String CODE_LETTERS = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
	/** Whether the anchor has the current contents; false after every change until the anchor is written. */
	private boolean anchorWritten;
	/** Raised on every change; placed boxes compare it to update comparators. */
	private int version;
	/** The members in order, for the tooltip; read from network threads. */
	private volatile List<UUID> membersSnapshot;
	/** The contents as sent to clients (tooltips); read from network threads. */
	private volatile ItemContainerContents snapshot = ItemContainerContents.EMPTY;

	Group(Groups owner, UUID id, @Nullable String name, List<UUID> members, List<ItemStack> items, boolean anchorWritten) {
		this.owner = owner;
		this.name = name;
		this.id = id;
		this.members = new ArrayList<>(members);
		this.membersSnapshot = List.copyOf(members);
		this.contents = new Contents();
		for (int slot = 0; slot < items.size() && slot < ShulkerBoxBlockEntity.CONTAINER_SIZE; slot++) {
			contents.getItems().set(slot, items.get(slot));
		}
		this.anchorWritten = anchorWritten;
		this.snapshot = ItemContainerContents.fromItems(contents.getItems());
	}

	public UUID id() {
		return id;
	}

	/** The group's short id shown to players ("K7QF"): the first 20 bits of its UUID, unique among a world's groups. */
	public String code() {
		return code(id);
	}

	public static String code(UUID id) {
		long bits = id.getMostSignificantBits() >>> 44;
		StringBuilder code = new StringBuilder();
		for (int i = 3; i >= 0; i--) {
			code.append(CODE_LETTERS.charAt((int) (bits >>> (5 * i)) & 31));
		}
		return code.toString();
	}

	public @Nullable String name() {
		return name;
	}

	public List<UUID> members() {
		return List.copyOf(members);
	}

	/** The number of boxes; safe on any thread. */
	public int size() {
		return membersSnapshot.size();
	}

	/**
	 * A box's place in the group, 1 for the oldest (the anchor), 0 if it isn't a member; safe on any thread. Boxes move
	 * up when an older one is destroyed, so "Box 2/3" always fits.
	 */
	public int position(UUID member) {
		return membersSnapshot.indexOf(member) + 1;
	}

	public UUID anchor() {
		return members.getFirst();
	}

	public boolean isAnchor(UUID member) {
		return !members.isEmpty() && members.getFirst().equals(member);
	}

	public boolean contains(UUID member) {
		return members.contains(member);
	}

	/** The shared inventory every member opens. */
	public SimpleContainer contents() {
		return contents;
	}

	public ItemContainerContents snapshot() {
		return snapshot;
	}

	public int version() {
		return version;
	}

	public boolean anchorWritten() {
		return anchorWritten;
	}

	public void markAnchorWritten() {
		if (!anchorWritten) {
			anchorWritten = true;
			owner.setDirty();
		}
	}

	void addMember(UUID member) {
		members.add(member);
		membersSnapshot = List.copyOf(members);
		owner.setDirty();
	}

	/** Removes a destroyed member; the next oldest becomes the anchor if it was the anchor. */
	void removeMember(UUID member) {
		boolean wasAnchor = isAnchor(member);
		members.remove(member);
		membersSnapshot = List.copyOf(members);
		if (wasAnchor) {
			anchorWritten = false;
		}
		owner.setDirty();
	}

	List<ItemStack> items() {
		return contents.getItems();
	}

	private void changed() {
		version++;
		anchorWritten = false;
		snapshot = ItemContainerContents.fromItems(contents.getItems());
		owner.changed(this);
	}

	/** The shared inventory: like a shulker box's, no shulker boxes inside. */
	private final class Contents extends SimpleContainer {
		Contents() {
			super(ShulkerBoxBlockEntity.CONTAINER_SIZE);
		}

		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return stack.getItem().canFitInsideContainerItems();
		}

		@Override
		public void setChanged() {
			super.setChanged();
			changed();
		}
	}
}
