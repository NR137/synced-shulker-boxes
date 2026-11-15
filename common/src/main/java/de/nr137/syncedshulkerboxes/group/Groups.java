package de.nr137.syncedshulkerboxes.group;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;
import de.nr137.syncedshulkerboxes.SyncedShulkerBoxes;

/**
 * All groups of a world, saved as {@code <world>/data/synced_shulker_boxes/groups.dat}: a file vanilla doesn't know
 * and ignores. The items in it carry the data version they were saved with and are updated by the game's data fixer
 * when a newer Minecraft loads them, like the items in the world itself.
 */
public final class Groups extends SavedData {
	public static final SavedDataType<Groups> TYPE = new SavedDataType<>(SyncedShulkerBoxes.id("groups"), Groups::new,
		new Codec<>() {
			@Override
			public <T> DataResult<Pair<Groups, T>> decode(DynamicOps<T> ops, T input) {
				return DataResult.success(Pair.of(load(new Dynamic<>(ops, input)), ops.empty()));
			}

			@Override
			public <T> DataResult<T> encode(Groups groups, DynamicOps<T> ops, T prefix) {
				return DataResult.success(groups.save(ops));
			}
		},
		// Our own structure has nothing to fix; this type has no fixes for it either. The items are fixed in load().
		DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** The contents of every group by id, for the network threads that encode item stacks (tooltips). */
	private static final Map<UUID, Group> LIVE = new ConcurrentHashMap<>();

	private final Map<UUID, Group> groups = new LinkedHashMap<>();
	/** Groups changed since the last end of a server tick. */
	private final Set<Group> changed = new LinkedHashSet<>();

	public Groups() {
	}

	public static Groups get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** The group's contents for an item being sent to a client; safe on any thread. */
	public static Optional<ItemContainerContents> liveSnapshot(UUID group) {
		Group found = LIVE.get(group);
		return found == null ? Optional.empty() : Optional.of(found.snapshot());
	}

	/** A group by id for network threads: only its snapshot and size may be read there. */
	public static Optional<Group> live(UUID group) {
		return Optional.ofNullable(LIVE.get(group));
	}

	/** Forgets the live groups when the server stops (singleplayer can open another world in the same game). */
	public static void clearLive() {
		LIVE.clear();
	}

	/** A new group id whose code (Group.code) no group of this world has yet. */
	public UUID newGroupId() {
		java.util.Set<String> taken = new java.util.HashSet<>();
		groups.values().forEach(group -> taken.add(group.code()));
		UUID id;
		do {
			id = UUID.randomUUID();
		} while (taken.contains(Group.code(id)));
		return id;
	}

	public Optional<Group> group(UUID id) {
		return Optional.ofNullable(groups.get(id));
	}

	public Collection<Group> all() {
		return List.copyOf(groups.values());
	}

	public Group create(UUID id, @Nullable String name, UUID first, UUID second) {
		Group group = new Group(this, id, name, List.of(first, second), List.of(), true);
		add(group);
		setDirty();
		return group;
	}

	public void addMember(Group group, UUID member) {
		group.addMember(member);
	}

	/**
	 * A member was destroyed. Returns the group's contents if it was the last member: the group is gone then, and its
	 * items drop where the box was.
	 */
	public Optional<List<ItemStack>> removeMember(Group group, UUID member) {
		group.removeMember(member);
		if (!group.members().isEmpty()) {
			return Optional.empty();
		}
		List<ItemStack> items = new ArrayList<>(group.contents().removeAllItems());
		groups.remove(group.id());
		LIVE.remove(group.id());
		changed.remove(group);
		setDirty();
		return Optional.of(items);
	}

	/** Removes a group with all its contents (admin command). */
	public void delete(Group group) {
		groups.remove(group.id());
		LIVE.remove(group.id());
		changed.remove(group);
		setDirty();
	}

	/** The groups changed since the last call. */
	public List<Group> takeChanged() {
		List<Group> result = List.copyOf(changed);
		changed.clear();
		return result;
	}

	void changed(Group group) {
		changed.add(group);
		setDirty();
	}

	private void add(Group group) {
		groups.put(group.id(), group);
		LIVE.put(group.id(), group);
	}

	// --- Saving and loading ---

	private <T> T save(DynamicOps<T> ops) {
		List<T> list = new ArrayList<>();
		for (Group group : groups.values()) {
			List<T> items = new ArrayList<>();
			List<ItemStack> stacks = group.items();
			for (int slot = 0; slot < stacks.size(); slot++) {
				ItemStack stack = stacks.get(slot);
				if (stack.isEmpty()) {
					continue;
				}
				int at = slot;
				ItemStack.CODEC.encodeStart(ops, stack)
					.resultOrPartial(error -> SyncedShulkerBoxes.LOGGER.error("Can't save an item of group {}: {}", group.id(), error))
					.ifPresent(encoded -> items.add(ops.createMap(Map.of(
						ops.createString("slot"), ops.createInt(at),
						ops.createString("item"), encoded))));
			}
			Map<T, T> fields = new java.util.LinkedHashMap<>(Map.of(
				ops.createString("id"), ops.createString(group.id().toString()),
				ops.createString("members"), ops.createList(group.members().stream().map(member -> ops.createString(member.toString()))),
				ops.createString("anchor_written"), ops.createBoolean(group.anchorWritten()),
				ops.createString("items"), ops.createList(items.stream())));
			if (group.name() != null) {
				fields.put(ops.createString("name"), ops.createString(group.name()));
			}
			list.add(ops.createMap(fields));
		}
		return ops.createMap(Map.of(
			ops.createString("data_version"), ops.createInt(currentDataVersion()),
			ops.createString("groups"), ops.createList(list.stream())));
	}

	private static <T> Groups load(Dynamic<T> root) {
		Groups result = new Groups();
		int dataVersion = root.get("data_version").asInt(currentDataVersion());
		for (Dynamic<T> entry : root.get("groups").asList(dynamic -> dynamic)) {
			Optional<UUID> id = uuid(entry.get("id").asString(""));
			List<UUID> members = entry.get("members").asList(member -> uuid(member.asString(""))).stream()
				.flatMap(Optional::stream).toList();
			if (id.isEmpty() || members.isEmpty()) {
				SyncedShulkerBoxes.LOGGER.warn("Skipping a group without id or members in groups.dat");
				continue;
			}
			List<ItemStack> items = new ArrayList<>();
			for (int slot = 0; slot < 27; slot++) {
				items.add(ItemStack.EMPTY);
			}
			for (Dynamic<T> item : entry.get("items").asList(dynamic -> dynamic)) {
				int slot = item.get("slot").asInt(-1);
				if (slot < 0 || slot >= items.size()) {
					continue;
				}
				Dynamic<T> fixed = DataFixers.getDataFixer()
					.update(References.ITEM_STACK, item.get("item").orElseEmptyMap(), dataVersion, currentDataVersion());
				ItemStack.CODEC.parse(fixed)
					.resultOrPartial(error -> SyncedShulkerBoxes.LOGGER.error("Can't load an item of group {}: {}", id.get(), error))
					.ifPresent(stack -> items.set(slot, stack));
			}
			String name = entry.get("name").asString("");
			result.add(new Group(result, id.get(), name.isBlank() ? null : name, members, items,
				entry.get("anchor_written").asBoolean(false)));
		}
		return result;
	}

	private static int currentDataVersion() {
		return SharedConstants.getCurrentVersion().dataVersion().version();
	}

	private static Optional<UUID> uuid(String text) {
		try {
			return Optional.of(UUID.fromString(text));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
