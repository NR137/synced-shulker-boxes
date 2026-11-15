package de.nr137.syncedshulkerboxes;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import de.nr137.syncedshulkerboxes.group.BoxView;
import de.nr137.syncedshulkerboxes.group.Group;
import de.nr137.syncedshulkerboxes.group.Groups;

/** What the mixins call: everything synced boxes do differently from vanilla shulker boxes. */
public final class Boxes {
	private Boxes() {
	}

	// --- Placed boxes ---

	/** What a placed box remembers about its mark, read again only when its components change. */
	public record Cached(DataComponentMap components, Optional<SyncedBox> mark, int seenVersion) {
		Optional<SyncedBox.Link> link() {
			return mark.flatMap(SyncedBox::link);
		}
	}

	/** Implemented by the mixin into ShulkerBoxBlockEntity. */
	public interface CacheHolder {
		@Nullable Cached syncedShulkerBoxes$cached();

		void syncedShulkerBoxes$setCached(Cached cached);
	}

	private static Cached cached(ShulkerBoxBlockEntity box) {
		CacheHolder holder = (CacheHolder) box;
		Cached cached = holder.syncedShulkerBoxes$cached();
		if (cached == null || cached.components() != box.components()) {
			Cached previous = cached;
			cached = new Cached(box.components(), SyncedBox.of(box), -1);
			holder.syncedShulkerBoxes$setCached(cached);
			// Clients with the mod draw placed synced boxes with the glint and learn of the mark from the block's data
			// (updateTag). Placing a box sends that in the same tick anyway; this covers later changes (/data). Only the
			// block's data, never the block itself, and only while the box exists: re-sending the block made a mined
			// box reappear on the miner's screen.
			if (box.getLevel() instanceof ServerLevel level && previous != null && !box.isRemoved()
					&& level.getBlockEntity(box.getBlockPos()) == box) {
				net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet =
					net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(box);
				for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(
						net.minecraft.world.level.ChunkPos.containing(box.getBlockPos()), false)) {
					player.connection.send(packet);
				}
			}
		}
		return cached;
	}

	/** Whether a placed box is synced (linked or not); also on clients, from the mark the server sends along. */
	public static boolean isSynced(ShulkerBoxBlockEntity box) {
		return cached(box).mark().isPresent();
	}

	/**
	 * What clients get about a placed box: vanilla sends nothing; a synced box sends its mark (in its components, like a
	 * saved block), so clients with the mod can draw it with the glint. Vanilla clients take it in and ignore it.
	 */
	public static net.minecraft.nbt.CompoundTag updateTag(ShulkerBoxBlockEntity box, net.minecraft.core.HolderLookup.Provider registries) {
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		net.minecraft.world.item.component.CustomData data = box.components().get(DataComponents.CUSTOM_DATA);
		if (data != null && SyncedBox.of(box).isPresent()) {
			DataComponentMap components = DataComponentMap.builder().set(DataComponents.CUSTOM_DATA, data).build();
			DataComponentMap.CODEC.encodeStart(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), components)
				.ifSuccess(encoded -> tag.put("components", encoded));
		}
		return tag;
	}

	public static Optional<SyncedBox.Link> link(ShulkerBoxBlockEntity box) {
		return cached(box).link();
	}

	/** The group of a placed box, if it is a linked member of a group that exists (server only). */
	public static Optional<Group> group(ShulkerBoxBlockEntity box) {
		if (!(box.getLevel() instanceof ServerLevel level)) {
			return Optional.empty();
		}
		return link(box).flatMap(link -> Groups.get(level.getServer()).group(link.group())
			.filter(group -> group.contains(link.member())));
	}

	/** Shulker boxes loaded on the server, by level; boxes removed or unloaded since are dropped at the next tick. */
	private static final java.util.Map<ServerLevel, java.util.Set<ShulkerBoxBlockEntity>> LOADED = new java.util.HashMap<>();

	public static void loaded(ServerLevel level, ShulkerBoxBlockEntity box) {
		LOADED.computeIfAbsent(level, ignored -> java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>())).add(box);
	}

	static void clearLoaded() {
		LOADED.clear();
	}

	/** Every server tick, for every loaded shulker box (not the box's own tick, see BlockEntityMixin). */
	private static void tickLoaded(MinecraftServer server) {
		for (var entry : LOADED.entrySet()) {
			ServerLevel level = entry.getKey();
			for (java.util.Iterator<ShulkerBoxBlockEntity> it = entry.getValue().iterator(); it.hasNext(); ) {
				ShulkerBoxBlockEntity box = it.next();
				if (box.isRemoved() || box.getLevel() != level) {
					it.remove();
				} else {
					onTick(level, box.getBlockPos(), box);
				}
			}
		}
	}

	/**
	 * A loaded placed box, every server tick: the anchor gets the current contents, the other members stay empty in
	 * vanilla terms, comparators next to it hear about changes, and synced boxes show their particles.
	 */
	public static void onTick(ServerLevel level, BlockPos pos, ShulkerBoxBlockEntity box) {
		if (cached(box).mark().isPresent()) {
			ambient(level, box);
		}
		Optional<SyncedBox.Link> link = link(box);
		if (link.isEmpty()) {
			return;
		}
		Optional<Group> found = Groups.get(level.getServer()).group(link.get().group())
			.filter(group -> group.contains(link.get().member()));
		if (found.isEmpty()) {
			return;
		}
		Group group = found.get();
		if (group.isAnchor(link.get().member())) {
			if (!group.anchorWritten()) {
				write(box, group.contents().getItems());
				group.markAnchorWritten();
			}
		} else if (!box.isEmpty()) {
			// Only the anchor holds a copy; anything here is stale (e.g. a former anchor's copy).
			write(box, NonNullList.withSize(box.getContainerSize(), ItemStack.EMPTY));
		}
		CacheHolder holder = (CacheHolder) box;
		Cached cached = holder.syncedShulkerBoxes$cached();
		if (cached != null && cached.seenVersion() != group.version()) {
			holder.syncedShulkerBoxes$setCached(new Cached(cached.components(), cached.mark(), group.version()));
			level.updateNeighbourForOutputSignal(pos, box.getBlockState().getBlock());
		}
	}

	private static void write(ShulkerBoxBlockEntity box, List<ItemStack> items) {
		for (int slot = 0; slot < box.getContainerSize(); slot++) {
			ItemStack stack = slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
			box.setItem(slot, stack.copy());
		}
	}

	/** The menu when a player opens a placed box; null: the vanilla menu (not a linked member). */
	public static @Nullable AbstractContainerMenu createMenu(ShulkerBoxBlockEntity box, int containerId, Inventory inventory) {
		Optional<Group> group = group(box);
		if (group.isEmpty() || !(inventory.player instanceof ServerPlayer player)) {
			return null;
		}
		if (!Permission.OPEN.check(player)) {
			player.sendSystemMessage(Component.literal("You may not open synced shulker boxes."), true);
			return blockedMenu();
		}
		showOpening(player, box);
		return new ShulkerBoxMenu(containerId, inventory, new BoxView(group.get(), box));
	}

	/**
	 * The title of a linked box's screen, like a named chest's: the group's name ("Base", or "Synced Shulker Box" without
	 * one), the box's place in the group and the group's code ("Base 2/3 · K7QF"), the name shortened so it fits the
	 * screen (TitleFit).
	 */
	public static java.util.Optional<Component> title(ShulkerBoxBlockEntity box) {
		java.util.Optional<SyncedBox.Link> link = link(box);
		return group(box).map(group -> Component.literal(TitleFit.fit(group.name() != null ? group.name() : "Synced Shulker Box",
			" " + group.position(link.orElseThrow().member()) + "/" + group.size() + " · " + group.code(),
			TitleFit.SHULKER_SCREEN_WIDTH)));
	}

	/** A marker for "don't open anything", which the mixin turns into no menu. */
	public static AbstractContainerMenu blockedMenu() {
		return BLOCKED;
	}

	private static final AbstractContainerMenu BLOCKED = new AbstractContainerMenu(null, -1) {
		@Override
		public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean stillValid(net.minecraft.world.entity.player.Player player) {
			return false;
		}
	};

	public static int color(ShulkerBoxBlockEntity box) {
		DyeColor dye = box.getColor();
		return (dye == null ? DyeColor.PURPLE : dye).getTextureDiffuseColor();
	}

	/**
	 * With the server option ambientParticles on: specks in the box's colour, fading to white, around every loaded
	 * synced box, a few every fifth of a second, sent to every player near it who hasn't turned them off
	 * (/syncedshulkers particles). Clients with the mod hide them (they
	 * decide with their own setting, mixin.client.ClientPacketListenerMixin): sent from {@link #ambientOrigin}.
	 */
	private static void ambient(ServerLevel level, ShulkerBoxBlockEntity box) {
		if (!SyncedShulkerBoxes.config().ambientParticles()
				|| Math.floorMod(level.getServer().getTickCount() + box.getBlockPos().hashCode(), 4) != 0) {
			return;
		}
		Vec3 at = ambientOrigin(box.getBlockPos());
		var speck = new net.minecraft.core.particles.DustColorTransitionOptions(color(box), 0xFFFFFF, 1.4F);
		PlayerSettings choices = PlayerSettings.get(level.getServer());
		for (ServerPlayer player : level.players()) {
			if (choices.of(player.getUUID()).ambientParticles()) {
				level.sendParticles(player, speck, false, false, at.x, at.y, at.z, 4, 0.4, 0.45, 0.4, 0.0);
			}
		}
	}

	/** Where the server's always-on particles of a box are sent from: a little above its middle. */
	public static Vec3 ambientOrigin(BlockPos pos) {
		return Vec3.atCenterOf(pos).add(0.0, 0.2, 0.0);
	}

	/**
	 * Opening: a vibration going out to the other boxes: trails in the box's colour shoot out to all sides, starting in
	 * the box's middle (clients with the mod can hide them: mixin.client.ClientPacketListenerMixin).
	 */
	private static void showOpening(ServerPlayer player, ShulkerBoxBlockEntity box) {
		if (!(box.getLevel() instanceof ServerLevel level)) {
			return;
		}
		// On top of the shulker box's own opening sound (which the box plays itself), always the same.
		level.playSound(null, box.getBlockPos(), net.minecraft.sounds.SoundEvents.LODESTONE_COMPASS_LOCK,
			net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
		if (!SyncedShulkerBoxes.config().openingParticles()) {
			return;
		}
		PlayerSettings choices = PlayerSettings.get(level.getServer());
		java.util.List<ServerPlayer> viewers = level.players().stream()
			.filter(viewer -> choices.of(viewer.getUUID()).openingParticles()).toList();
		int color = color(box);
		Vec3 center = Vec3.atCenterOf(box.getBlockPos());
		net.minecraft.util.RandomSource random = level.getRandom();
		int trails = 32;
		for (int i = 0; i < trails; i++) {
			// Directions spread evenly over a sphere (golden angle), 2 to 3.5 blocks out.
			double y = 1 - 2 * (i + 0.5) / trails;
			double radius = Math.sqrt(1 - y * y);
			double angle = i * Math.PI * (3 - Math.sqrt(5));
			double distance = 2 + 1.5 * random.nextDouble();
			Vec3 target = center.add(Math.cos(angle) * radius * distance, y * distance, Math.sin(angle) * radius * distance);
			TrailParticleOption trail = new TrailParticleOption(target, color, 15 + random.nextInt(10));
			for (ServerPlayer viewer : viewers) {
				level.sendParticles(viewer, trail, false, false, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}

	/** No hoppers, droppers, crafters or hopper minecarts on linked members. */
	public static boolean blocksAutomation(ShulkerBoxBlockEntity box) {
		return link(box).isPresent();
	}

	/** Comparators read the shared contents. */
	public static OptionalInt analogSignal(ShulkerBoxBlockEntity box) {
		return group(box).map(group -> OptionalInt.of(AbstractContainerMenu.getRedstoneSignalFromContainer(group.contents())))
			.orElse(OptionalInt.empty());
	}

	// --- Items ---

	/**
	 * A box item destroyed by fire, lava, a cactus or the void. Returns true when this mod handled it: a linked member
	 * drops nothing while other members exist (their items live on in the group); the last one drops the group's
	 * items, as a vanilla box would drop its own.
	 */
	public static boolean onDestroyed(ItemEntity entity) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return false;
		}
		Optional<SyncedBox.Link> link = SyncedBox.of(entity.getItem()).flatMap(SyncedBox::link);
		if (link.isEmpty()) {
			return false;
		}
		Groups groups = Groups.get(level.getServer());
		Optional<Group> group = groups.group(link.get().group()).filter(found -> found.contains(link.get().member()));
		if (group.isEmpty()) {
			return false;
		}
		groups.removeMember(group.get(), link.get().member())
			.ifPresent(items -> ItemUtils.onContainerDestroyed(entity, items.stream().filter(stack -> !stack.isEmpty())));
		return true;
	}

	/**
	 * A box item gone without being destroyed: despawned, fallen into the void, killed. As in vanilla, its contents are
	 * lost: it leaves its group, and if it was the last box, the group and its items are gone too.
	 */
	public static void onLost(ItemEntity entity) {
		if (!(entity.level() instanceof ServerLevel level) || entity.getItem().isEmpty()) {
			return;
		}
		Optional<SyncedBox.Link> link = SyncedBox.of(entity.getItem()).flatMap(SyncedBox::link);
		if (link.isEmpty()) {
			return;
		}
		Groups groups = Groups.get(level.getServer());
		groups.group(link.get().group()).filter(found -> found.contains(link.get().member()))
			.ifPresent(group -> groups.removeMember(group, link.get().member()));
	}

	/**
	 * An item stack as it is sent to a client: every synced box gets the glint and its "Synced" name, and a linked one
	 * the group's current contents (vanilla's tooltip and tooltip mods read them from there). None of this is saved,
	 * so the world keeps only the mark. Runs on network threads, also for singleplayer; the saved stack is not changed.
	 */
	public static ItemStack forClient(ItemStack stack) {
		if (!SyncedBox.isShulkerBox(stack) || !stack.has(DataComponents.CUSTOM_DATA)) {
			return stack;
		}
		Optional<SyncedBox> mark = SyncedBox.of(stack);
		if (mark.isEmpty()) {
			return stack;
		}
		ItemStack copy = stack.copy();
		// Minecraft doesn't draw the glint on shulker boxes yet (MC-69683); sent anyway, it costs nothing.
		copy.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		int color = SyncedBox.nameColor(stack);
		copy.set(DataComponents.ITEM_NAME, SyncedBox.displayName(stack, mark.get()).copy().withColor(color));
		Component customName = stack.get(DataComponents.CUSTOM_NAME);
		if (customName != null && customName.getStyle().getColor() == null) {
			copy.set(DataComponents.CUSTOM_NAME, customName.copy().withColor(color));
		}
		Optional<Group> group = mark.get().link().flatMap(link -> Groups.live(link.group()));
		group.ifPresent(found -> copy.set(DataComponents.CONTAINER, found.snapshot()));
		java.util.List<Component> lines = group.map(found -> java.util.List.<Component>of(
				Component.literal("Group " + found.code() + " · Box " + found.position(mark.get().link().orElseThrow().member())
					+ "/" + found.size())))
			.orElseGet(() -> java.util.List.of(Component.literal(mark.get().isLinked() ? "Link lost" : "Link two in an anvil")));
		net.minecraft.world.item.component.ItemLore lore = copy.getOrDefault(DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY);
		for (Component line : lines) {
			lore = lore.withLineAdded(line.copy().withStyle(style -> style.withColor(net.minecraft.ChatFormatting.GRAY).withItalic(false)));
		}
		copy.set(DataComponents.LORE, lore);
		return copy;
	}

	/**
	 * An item a creative player puts into their inventory: it comes from the client, as the client was shown it. A
	 * synced box loses what only clients see (glint, name), and a linked one keeps the contents the server knows (the
	 * anchor's copy, empty for the others).
	 */
	public static void fromCreativeClient(ServerPlayer player, ItemStack stack) {
		Optional<SyncedBox> mark = SyncedBox.of(stack);
		if (mark.isEmpty()) {
			return;
		}
		stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
		stack.remove(DataComponents.ITEM_NAME);
		// The tooltip line only clients get; a lore of the player's own (none in survival) would be lost here.
		stack.remove(DataComponents.LORE);
		Optional<SyncedBox.Link> link = mark.get().link();
		if (link.isEmpty()) {
			return;
		}
		Optional<Group> group = Groups.get(player.level().getServer()).group(link.get().group());
		if (group.isPresent() && group.get().isAnchor(link.get().member())) {
			stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(group.get().contents().getItems()));
		} else {
			stack.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		}
	}

	/**
	 * A placed box broken in survival drops what the vanilla loot table copies (name, contents, lock); the mark is put
	 * back here, or the box would lose its link (and a mined anchor would turn its copy into real items).
	 */
	public static void keepMark(@Nullable BlockEntity blockEntity, List<ItemStack> drops) {
		if (!(blockEntity instanceof ShulkerBoxBlockEntity box)) {
			return;
		}
		net.minecraft.world.item.component.CustomData data = box.components().get(DataComponents.CUSTOM_DATA);
		if (data == null || SyncedBox.of(box).isEmpty()) {
			return;
		}
		for (ItemStack drop : drops) {
			if (SyncedBox.isShulkerBox(drop)) {
				drop.set(DataComponents.CUSTOM_DATA, data);
			}
		}
	}

	// --- Every server tick ---

	/**
	 * End of a server tick: the anchors that are items in online players' inventories, ender chests or open screens get
	 * the current contents, and players holding members of changed groups get those slots sent again (tooltips).
	 */
	public static void onServerTick(MinecraftServer server) {
		tickLoaded(server);
		Groups groups = Groups.get(server);
		Set<UUID> pending = new HashSet<>();
		for (Group group : groups.all()) {
			if (!group.anchorWritten()) {
				pending.add(group.id());
			}
		}
		Set<UUID> changed = new HashSet<>();
		for (Group group : groups.takeChanged()) {
			changed.add(group.id());
		}
		if (pending.isEmpty() && changed.isEmpty()) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean resend = false;
			resend |= visit(player.getInventory(), groups, pending, changed);
			visit(player.getEnderChestInventory(), groups, pending, changed);
			AbstractContainerMenu menu = player.containerMenu;
			if (menu != player.inventoryMenu) {
				for (Slot slot : menu.slots) {
					if (!(slot.container instanceof Inventory)) {
						resend |= visitStack(slot.getItem(), groups, pending, changed);
					}
				}
			}
			ItemStack carried = menu.getCarried();
			resend |= visitStack(carried, groups, pending, changed);
			if (resend) {
				menu.sendAllDataToRemote();
			}
		}
	}

	/** Returns whether the container holds a member of a changed group. */
	private static boolean visit(Container container, Groups groups, Set<UUID> pending, Set<UUID> changed) {
		boolean found = false;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			found |= visitStack(container.getItem(slot), groups, pending, changed);
		}
		return found;
	}

	private static boolean visitStack(ItemStack stack, Groups groups, Set<UUID> pending, Set<UUID> changed) {
		if (stack.isEmpty() || !stack.has(DataComponents.CUSTOM_DATA)) {
			return false;
		}
		Optional<SyncedBox.Link> link = SyncedBox.of(stack).flatMap(SyncedBox::link);
		if (link.isEmpty()) {
			return false;
		}
		UUID groupId = link.get().group();
		if (pending.contains(groupId)) {
			groups.group(groupId).filter(group -> group.isAnchor(link.get().member())).ifPresent(group -> {
				stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(group.contents().getItems()));
				group.markAnchorWritten();
				pending.remove(groupId);
			});
		}
		return changed.contains(groupId);
	}
}
