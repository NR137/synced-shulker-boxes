package de.nr137.syncedshulkerboxes.gametest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.AABB;
import de.nr137.syncedshulkerboxes.Boxes;
import de.nr137.syncedshulkerboxes.SyncedBox;
import de.nr137.syncedshulkerboxes.SyncedShulkerBoxes;
import de.nr137.syncedshulkerboxes.group.Group;
import de.nr137.syncedshulkerboxes.group.Groups;

/**
 * Game tests on a real server, shared by both loaders: fabric/src/gametest and neoforge/src/gametest register each
 * entry. Run with ./tasks.sh gametest.
 */
public final class GameTests {
	private GameTests() {
	}

	/** Test name -> test, in the order they run. */
	public static Map<String, Consumer<GameTestHelper>> all() {
		Map<String, Consumer<GameTestHelper>> tests = new LinkedHashMap<>();
		tests.put("mod_is_running", GameTests::modIsRunning);
		tests.put("smithing_makes_a_synced_box", GameTests::smithingMakesASyncedBox);
		tests.put("smithing_refuses_full_or_synced_boxes", GameTests::smithingRefusesFullOrSyncedBoxes);
		tests.put("anvil_links_and_joins", GameTests::anvilLinksAndJoins);
		tests.put("anvil_never_duplicates", GameTests::anvilNeverDuplicates);
		tests.put("members_share_contents_and_the_anchor_holds_the_copy", GameTests::membersShareContents);
		tests.put("no_automation_and_comparators_read_the_group", GameTests::noAutomation);
		tests.put("real_hoppers_neither_take_nor_add", GameTests::realHoppers);
		tests.put("hopper_minecarts_and_droppers_neither", GameTests::minecartsAndDroppers);
		tests.put("groups_and_player_choices_survive_saving", GameTests::savingAndLoading);
		tests.put("destroyed_members", GameTests::destroyedMembers);
		tests.put("a_mined_member_keeps_its_link", GameTests::minedMemberKeepsItsLink);
		tests.put("lost_members_leave_and_the_last_takes_the_items", GameTests::lostMembers);
		tests.put("clients_see_the_contents_but_creative_cannot_set_them", GameTests::clientsSeeContents);
		tests.put("packets_carry_glint_name_and_contents", GameTests::packetsCarryGlint);
		return tests;
	}

	private static void modIsRunning(GameTestHelper helper) {
		helper.assertTrue(SyncedShulkerBoxes.isRunning(), "the mod did not get the server's start event");
		helper.succeed();
	}

	// --- Getting one ---

	private static ItemStack smith(GameTestHelper helper, ServerPlayer player, ItemStack base) {
		SmithingMenu menu = new SmithingMenu(1, player.getInventory());
		menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(new ItemStack(Items.ECHO_SHARD));
		menu.getSlot(SmithingMenu.BASE_SLOT).set(base);
		menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.ENDER_EYE));
		return menu.getSlot(SmithingMenu.RESULT_SLOT).getItem();
	}

	private static void smithingMakesASyncedBox(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack result = smith(helper, player, new ItemStack(Items.DYED_SHULKER_BOX.red()));
		helper.assertValueEqual(result.getItem(), Items.DYED_SHULKER_BOX.red(), "the result keeps the box's colour");
		Optional<SyncedBox> mark = SyncedBox.of(result);
		helper.assertTrue(mark.isPresent() && !mark.get().isLinked(), "the result is a synced box, not linked yet");
		helper.assertFalse(result.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE), "the saved result has no glint (only clients see it)");
		ItemStack shown = Boxes.forClient(result);
		helper.assertTrue(Boolean.TRUE.equals(shown.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "clients see the glint");
		helper.assertValueEqual(shown.getHoverName().getString(), "Syncable Red Shulker Box", "the name clients see");
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	private static void smithingRefusesFullOrSyncedBoxes(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack full = new ItemStack(Items.SHULKER_BOX);
		full.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND))));
		helper.assertTrue(smith(helper, player, full).isEmpty(), "a box with contents is refused");
		ItemStack synced = smith(helper, player, new ItemStack(Items.SHULKER_BOX));
		helper.assertTrue(smith(helper, player, synced).isEmpty(), "a synced box is refused");
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	private static ItemStack syncedBox(GameTestHelper helper, ServerPlayer player) {
		return smith(helper, player, new ItemStack(Items.SHULKER_BOX)).copy();
	}

	/**
	 * Puts two boxes into an anvil and takes the result; returns {the first slot afterwards, the result taken}. The
	 * result is the second box, linked; the first goes back into its slot. Without a result: {first box, empty}.
	 */
	private static ItemStack[] anvil(ServerPlayer player, ItemStack left, ItemStack right, String name) {
		AnvilMenu menu = new AnvilMenu(2, player.getInventory());
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(left);
		menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(right);
		if (name != null) {
			menu.setItemName(name);
		}
		ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().copy();
		if (!result.isEmpty()) {
			menu.clicked(AnvilMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
			menu.setCarried(ItemStack.EMPTY);
		}
		return new ItemStack[] {menu.getSlot(AnvilMenu.INPUT_SLOT).getItem().copy(), result};
	}

	private static void anvilLinksAndJoins(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Groups groups = Groups.get(helper.getLevel().getServer());
		ItemStack[] linked = anvil(player, syncedBox(helper, player), syncedBox(helper, player), "Mine");
		Optional<SyncedBox.Link> left = SyncedBox.of(linked[0]).flatMap(SyncedBox::link);
		Optional<SyncedBox.Link> right = SyncedBox.of(linked[1]).flatMap(SyncedBox::link);
		helper.assertTrue(left.isPresent() && right.isPresent(), "both boxes come out linked");
		helper.assertValueEqual(left.get().group(), right.get().group(), "the two boxes' group");
		Group group = groups.group(left.get().group()).orElseThrow(() -> new AssertionError("the group exists"));
		helper.assertValueEqual(group.members(), List.of(left.get().member(), right.get().member()), "members, first slot first");
		helper.assertValueEqual(linked[0].getHoverName().getString(), "Mine", "the first box's name");
		helper.assertValueEqual(linked[1].getHoverName().getString(), "Mine", "the second box's name");
		helper.assertValueEqual(Boxes.forClient(anvil(player, syncedBox(helper, player), syncedBox(helper, player), null)[1])
			.getHoverName().getString(), "Synced Shulker Box", "without a name, the client sees the synced name");

		ItemStack[] joined = anvil(player, linked[0], syncedBox(helper, player), null);
		Optional<SyncedBox.Link> third = SyncedBox.of(joined[1]).flatMap(SyncedBox::link);
		helper.assertTrue(third.isPresent() && third.get().group().equals(group.id()), "a third box joins the group");
		helper.assertValueEqual(group.members().size(), 3, "members after joining");
		helper.assertValueEqual(joined[1].getHoverName().getString(), "Mine", "the joining box gets the first box's name");
		helper.assertTrue(ItemStack.matches(joined[0], linked[0]), "the first box goes back unchanged");

		ItemStack[] renamed = anvil(player, joined[0], syncedBox(helper, player), "Other");
		helper.assertValueEqual(renamed[1].getHoverName().getString(), "Other", "a name typed while joining goes onto the new box");
		helper.assertValueEqual(renamed[0].getHoverName().getString(), "Mine", "and not onto the first one");

		ItemStack full = syncedBox(helper, player);
		full.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND))));
		helper.assertTrue(anvil(player, joined[0], full, null)[1].isEmpty(), "a box with contents can't join");
		helper.assertTrue(anvil(player, joined[0], joined[1], null)[1].isEmpty(), "a linked box can't join again");
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	/**
	 * Every way to take the anvil's result (click, shift-click) for a new group and for adding a box, with and without a
	 * name: afterwards, with the anvil closed, the player has exactly the two boxes, and no more.
	 */
	private static void anvilNeverDuplicates(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		for (ContainerInput input : List.of(ContainerInput.PICKUP, ContainerInput.QUICK_MOVE)) {
			for (String name : java.util.Arrays.asList(null, "Synced Shulker Box", "Other")) {
				for (boolean joining : List.of(false, true)) {
					player.getInventory().clearContent();
					ItemStack first = joining ? anvil(player, syncedBox(helper, player), syncedBox(helper, player), null)[0] : syncedBox(helper, player);
					player.getInventory().clearContent();
					for (boolean nameFirst : List.of(false, true)) {
					player.getInventory().clearContent();
					AnvilMenu menu = new AnvilMenu(9, player.getInventory());
					menu.getSlot(AnvilMenu.INPUT_SLOT).set(first.copy());
					// The order a player may use: the name typed while only the first box is in (vanilla's rename),
					// then the second box.
					if (nameFirst && name != null) {
						menu.setItemName(name);
					}
					menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(syncedBox(helper, player));
					if (!nameFirst && name != null) {
						menu.setItemName(name);
					}
					menu.clicked(AnvilMenu.RESULT_SLOT, 0, input, player);
					menu.broadcastChanges();
					if (!menu.getCarried().isEmpty()) {
						player.getInventory().add(menu.getCarried());
						menu.setCarried(ItemStack.EMPTY);
					}
					// An anvil without a block in the world doesn't hand its slots back when closed: counted directly.
					long boxes = java.util.stream.Stream.concat(player.getInventory().getNonEquipmentItems().stream(),
							java.util.stream.Stream.of(menu.getSlot(AnvilMenu.INPUT_SLOT).getItem(), menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem(),
								menu.getSlot(AnvilMenu.RESULT_SLOT).getItem()))
						.filter(SyncedBox::isShulkerBox).mapToInt(ItemStack::getCount).sum();
					helper.assertValueEqual(boxes, 2L, "boxes after " + input + (joining ? " adding" : " linking") + " with name " + name
						+ (nameFirst ? ", typed first" : ""));
					}
				}
			}
		}
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	// --- Using it ---

	private static ShulkerBoxBlockEntity place(GameTestHelper helper, BlockPos pos, Group group, UUID member) {
		helper.setBlock(pos, Blocks.SHULKER_BOX);
		ShulkerBoxBlockEntity box = helper.getBlockEntity(pos, ShulkerBoxBlockEntity.class);
		ItemStack stack = new ItemStack(Items.SHULKER_BOX);
		SyncedBox.link(stack, new SyncedBox.Link(group.id(), member));
		box.applyComponentsFromItemStack(stack);
		return box;
	}

	private static void membersShareContents(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Groups groups = Groups.get(helper.getLevel().getServer());
		UUID anchor = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		Group group = groups.create(UUID.randomUUID(), null, anchor, other);
		ShulkerBoxBlockEntity anchorBox = place(helper, new BlockPos(1, 1, 1), group, anchor);
		ShulkerBoxBlockEntity otherBox = place(helper, new BlockPos(3, 1, 1), group, other);
		player.teleportTo(helper.absolutePos(new BlockPos(2, 1, 2)).getX(), helper.absolutePos(new BlockPos(2, 1, 2)).getY(),
			helper.absolutePos(new BlockPos(2, 1, 2)).getZ());

		helper.assertValueEqual(Boxes.title(otherBox).map(title -> title.getString()).orElse(""),
			"Synced Shulker Box 2/2 · " + group.code(), "the title of the other box's screen");
		AbstractContainerMenu menu = Boxes.createMenu(otherBox, 3, player.getInventory());
		helper.assertTrue(menu != null && menu != Boxes.blockedMenu(), "the other box opens the group's menu");
		menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 5));
		helper.assertValueEqual(group.contents().getItem(0).getCount(), 5, "diamonds in the group");
		AbstractContainerMenu second = Boxes.createMenu(anchorBox, 4, player.getInventory());
		helper.assertValueEqual(second.getSlot(0).getItem().getCount(), 5, "diamonds seen through the anchor");
		second.getSlot(0).getItem().shrink(2);
		second.getSlot(0).setChanged();
		helper.assertValueEqual(menu.getSlot(0).getItem().getCount(), 3, "taking from one takes from the other");
		helper.assertFalse(menu.getSlot(1).mayPlace(new ItemStack(Items.SHULKER_BOX)), "no shulker box inside");

		helper.succeedWhen(() -> {
			helper.assertValueEqual(anchorBox.getItem(0).getCount(), 3, "the anchor's own copy");
			helper.assertTrue(otherBox.isEmpty(), "the other box stays empty in vanilla terms");
			helper.assertTrue(group.anchorWritten(), "the anchor is written");
			menu.removed(player);
			second.removed(player);
			helper.getLevel().getServer().getPlayerList().remove(player);
		});
	}

	private static void noAutomation(GameTestHelper helper) {
		Groups groups = Groups.get(helper.getLevel().getServer());
		UUID anchor = UUID.randomUUID();
		Group group = groups.create(UUID.randomUUID(), null, anchor, UUID.randomUUID());
		ShulkerBoxBlockEntity box = place(helper, new BlockPos(1, 1, 1), group, anchor);
		helper.assertValueEqual(box.getSlotsForFace(Direction.UP).length, 0, "slots for hoppers");
		helper.assertValueEqual(Boxes.analogSignal(box).orElse(-1), 0, "comparator signal of an empty group");
		group.contents().setItem(0, new ItemStack(Items.DIAMOND, 64));
		helper.assertTrue(Boxes.analogSignal(box).orElse(-1) > 0, "comparators read the group's contents");
		helper.succeed();
	}

	/** Real hoppers, ticking for 3 seconds (with Lithium in the test run, which has hopper code of its own). */
	private static void realHoppers(GameTestHelper helper) {
		Groups groups = Groups.get(helper.getLevel().getServer());
		UUID anchor = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		Group group = groups.create(UUID.randomUUID(), null, anchor, other);
		group.contents().setItem(0, new ItemStack(Items.DIAMOND, 5));
		// Under the anchor, which holds the copy in vanilla terms: it must not pull anything out.
		BlockPos below = new BlockPos(1, 1, 1);
		helper.setBlock(below, Blocks.HOPPER);
		ShulkerBoxBlockEntity anchorBox = place(helper, new BlockPos(1, 2, 1), group, anchor);
		// Above the other box, with an emerald: it must not push it in.
		place(helper, new BlockPos(3, 1, 1), group, other);
		BlockPos above = new BlockPos(3, 2, 1);
		helper.setBlock(above, Blocks.HOPPER);
		helper.getBlockEntity(above, net.minecraft.world.level.block.entity.HopperBlockEntity.class).setItem(0, new ItemStack(Items.EMERALD));
		helper.runAfterDelay(60, () -> {
			helper.assertValueEqual(anchorBox.getItem(0).getCount(), 5, "the anchor's copy (written while the box sleeps)");
			helper.assertTrue(helper.getBlockEntity(below, net.minecraft.world.level.block.entity.HopperBlockEntity.class).isEmpty(),
				"the hopper under the anchor took nothing");
			helper.assertValueEqual(group.contents().getItem(0).getCount(), 5, "the group's diamonds");
			helper.assertTrue(helper.getBlockEntity(above, net.minecraft.world.level.block.entity.HopperBlockEntity.class).getItem(0).is(Items.EMERALD),
				"the hopper above kept its emerald");
			helper.assertTrue(group.contents().getItems().stream().noneMatch(stack -> stack.is(Items.EMERALD)), "no emerald in the group");
			helper.succeed();
		});
	}

	/** A hopper minecart under the anchor and a powered dropper facing the other box, 3 seconds. */
	private static void minecartsAndDroppers(GameTestHelper helper) {
		Groups groups = Groups.get(helper.getLevel().getServer());
		UUID anchor = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		Group group = groups.create(groups.newGroupId(), null, anchor, other);
		group.contents().setItem(0, new ItemStack(Items.DIAMOND, 5));
		helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.RAIL);
		ShulkerBoxBlockEntity anchorBox = place(helper, new BlockPos(1, 2, 1), group, anchor);
		var minecart = helper.spawn(net.minecraft.world.entity.EntityTypes.HOPPER_MINECART, new BlockPos(1, 1, 1));
		place(helper, new BlockPos(4, 1, 1), group, other);
		BlockPos dropper = new BlockPos(3, 1, 1);
		helper.setBlock(dropper, Blocks.DROPPER.defaultBlockState().setValue(net.minecraft.world.level.block.DispenserBlock.FACING,
			net.minecraft.core.Direction.EAST));
		var dropperBox = helper.getBlockEntity(dropper, net.minecraft.world.level.block.entity.DispenserBlockEntity.class);
		dropperBox.setItem(0, new ItemStack(Items.EMERALD));
		helper.runAfterDelay(5, () -> helper.setBlock(new BlockPos(3, 2, 1), Blocks.REDSTONE_BLOCK));
		helper.runAfterDelay(60, () -> {
			// (The rail may break off in the empty test area and end up in the minecart; only the diamonds count.)
			helper.assertTrue(java.util.stream.IntStream.range(0, minecart.getContainerSize()).noneMatch(slot -> minecart.getItem(slot).is(Items.DIAMOND)),
				"the hopper minecart took no diamonds");
			helper.assertValueEqual(anchorBox.getItem(0).getCount(), 5, "the anchor's copy");
			helper.assertValueEqual(group.contents().getItem(0).getCount(), 5, "the group's diamonds");
			helper.assertTrue(group.contents().getItems().stream().noneMatch(stack -> stack.is(Items.EMERALD)),
				"the dropper put nothing into the group");
			helper.succeed();
		});
	}

	/** What the world saves of the mod comes back the same: groups (contents, members, name), players' choices. */
	private static void savingAndLoading(GameTestHelper helper) {
		var ops = helper.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
		Groups saved = new Groups();
		Group group = saved.create(saved.newGroupId(), "Storage", UUID.randomUUID(), UUID.randomUUID());
		saved.addMember(group, UUID.randomUUID());
		ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
		named.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Edge"));
		group.contents().setItem(3, named);
		group.contents().setItem(26, new ItemStack(Items.EMERALD, 64));
		var tag = Groups.TYPE.codec().encodeStart(ops, saved).getOrThrow();
		Group loaded = Groups.TYPE.codec().parse(ops, tag).getOrThrow().group(group.id()).orElseThrow();
		helper.assertValueEqual(loaded.members(), group.members(), "members, in order");
		helper.assertValueEqual(loaded.name(), "Storage", "the group's name");
		helper.assertValueEqual(loaded.code(), group.code(), "the code");
		helper.assertTrue(ItemStack.matches(loaded.contents().getItem(3), named), "the named sword in slot 3");
		helper.assertValueEqual(loaded.contents().getItem(26).getCount(), 64, "the emeralds in slot 26");

		net.minecraft.world.level.saveddata.SavedDataType<de.nr137.syncedshulkerboxes.PlayerSettings> type = de.nr137.syncedshulkerboxes.PlayerSettings.TYPE;
		de.nr137.syncedshulkerboxes.PlayerSettings choices = new de.nr137.syncedshulkerboxes.PlayerSettings();
		UUID player = UUID.randomUUID();
		choices.set(player, new de.nr137.syncedshulkerboxes.PlayerSettings.Choices(false, true));
		var back = type.codec().parse(ops, type.codec().encodeStart(ops, choices).getOrThrow()).getOrThrow();
		helper.assertValueEqual(back.of(player), new de.nr137.syncedshulkerboxes.PlayerSettings.Choices(false, true), "a player's choices");
		helper.assertValueEqual(back.of(UUID.randomUUID()), new de.nr137.syncedshulkerboxes.PlayerSettings.Choices(true, true),
			"everyone else: both on");
		helper.succeed();
	}

	private static ItemEntity dropped(GameTestHelper helper, Group group, UUID member) {
		ItemStack stack = new ItemStack(Items.SHULKER_BOX);
		SyncedBox.link(stack, new SyncedBox.Link(group.id(), member));
		ItemEntity entity = helper.spawnItem(Items.STONE, new BlockPos(1, 2, 1));
		entity.setItem(stack);
		return entity;
	}

	private static void destroyedMembers(GameTestHelper helper) {
		Groups groups = Groups.get(helper.getLevel().getServer());
		UUID anchor = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		Group group = groups.create(UUID.randomUUID(), null, anchor, other);
		group.contents().setItem(0, new ItemStack(Items.DIAMOND, 7));
		ItemEntity first = dropped(helper, group, anchor);
		first.getItem().getItem().onDestroyed(first);
		first.discard();
		helper.assertValueEqual(group.members(), List.of(other), "the destroyed anchor left the group");
		helper.assertTrue(groups.group(group.id()).isPresent(), "the group still exists");
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8),
			entity -> entity.getItem().is(Items.DIAMOND)).isEmpty(), "nothing dropped while other members exist");

		ItemEntity last = dropped(helper, group, other);
		last.getItem().getItem().onDestroyed(last);
		last.discard();
		helper.assertTrue(groups.group(group.id()).isEmpty(), "the last member took the group with it");
		helper.succeedWhen(() -> helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class,
			new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8), entity -> entity.getItem().is(Items.DIAMOND)).isEmpty(),
			"the last member dropped the group's diamonds"));
	}

	/** Despawned, fallen into the void, killed: as in vanilla, gone with their contents (Boxes.onLost). */
	private static void lostMembers(GameTestHelper helper) {
		Groups groups = Groups.get(helper.getLevel().getServer());
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		Group group = groups.create(groups.newGroupId(), null, first, second);
		groups.addMember(group, UUID.randomUUID());
		UUID third = group.members().get(2);
		group.contents().setItem(0, new ItemStack(Items.DIAMOND, 4));
		dropped(helper, group, first).kill(helper.getLevel());
		helper.assertValueEqual(group.members(), List.of(second, third), "a killed box left the group");
		ItemEntity voided = dropped(helper, group, second);
		voided.setPos(voided.getX(), helper.getLevel().getMinY() - 100, voided.getZ());
		voided.tick();
		helper.assertValueEqual(group.members(), List.of(third), "a box in the void left the group");
		dropped(helper, group, third).kill(helper.getLevel());
		helper.assertTrue(groups.group(group.id()).isEmpty(), "the last box took the group with it");
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8),
			entity -> entity.getItem().is(Items.DIAMOND)).isEmpty(), "the items are lost, as in vanilla");
		helper.succeed();
	}

	private static void minedMemberKeepsItsLink(GameTestHelper helper) {
		Groups groups = Groups.get(helper.getLevel().getServer());
		UUID anchor = UUID.randomUUID();
		Group group = groups.create(UUID.randomUUID(), null, anchor, UUID.randomUUID());
		group.contents().setItem(0, new ItemStack(Items.DIAMOND, 3));
		BlockPos pos = new BlockPos(1, 1, 1);
		place(helper, pos, group, anchor);
		helper.runAfterDelay(2, () -> {
			// Survival mining: the vanilla loot table, which copies only name, contents and lock.
			helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
			helper.succeedWhen(() -> {
				List<ItemEntity> boxes = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
					new AABB(helper.absolutePos(pos)).inflate(3), entity -> SyncedBox.isShulkerBox(entity.getItem()));
				helper.assertValueEqual(boxes.size(), 1, "dropped boxes");
				Optional<SyncedBox.Link> link = SyncedBox.of(boxes.getFirst().getItem()).flatMap(SyncedBox::link);
				helper.assertTrue(link.isPresent() && link.get().member().equals(anchor), "the dropped box is still the anchor of its group");
			});
		});
	}

	/** What a client really receives: the stack through the network codec, as in every packet. */
	private static void packetsCarryGlint(GameTestHelper helper) {
		Groups groups = Groups.get(helper.getLevel().getServer());
		UUID anchor = UUID.randomUUID();
		Group group = groups.create(UUID.randomUUID(), null, anchor, UUID.randomUUID());
		group.contents().setItem(0, new ItemStack(Items.EMERALD, 2));
		ItemStack box = new ItemStack(Items.DYED_SHULKER_BOX.blue());
		SyncedBox.link(box, new SyncedBox.Link(group.id(), anchor));
		net.minecraft.network.RegistryFriendlyByteBuf buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
			io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
		ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, box);
		ItemStack received = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
		helper.assertTrue(Boolean.TRUE.equals(received.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "the client receives the glint");
		helper.assertValueEqual(received.getHoverName().getString(), "Synced Blue Shulker Box", "the name the client receives");
		helper.assertTrue(received.get(DataComponents.CONTAINER).nonEmptyItemCopyStream().anyMatch(stack -> stack.is(Items.EMERALD)),
			"the client receives the contents");
		helper.assertTrue(received.get(DataComponents.ITEM_NAME).getStyle().getColor() != null, "the name is coloured");
		helper.assertValueEqual(received.get(DataComponents.LORE).lines().stream().map(line -> line.getString()).toList(),
			List.of("Group " + group.code() + " · Box 1/2"), "the tooltip lines");
		helper.succeed();
	}

	private static void clientsSeeContents(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Groups groups = Groups.get(helper.getLevel().getServer());
		UUID anchor = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		Group group = groups.create(UUID.randomUUID(), null, anchor, other);
		group.contents().setItem(3, new ItemStack(Items.EMERALD, 9));
		ItemStack member = new ItemStack(Items.SHULKER_BOX);
		SyncedBox.link(member, new SyncedBox.Link(group.id(), other));
		ItemStack sent = Boxes.forClient(member);
		helper.assertTrue(sent != member && sent.get(DataComponents.CONTAINER).nonEmptyItemCopyStream()
			.anyMatch(stack -> stack.is(Items.EMERALD) && stack.getCount() == 9), "the client is shown the emeralds");
		helper.assertTrue(SyncedBox.isEmptyBox(member), "the saved stack stays empty");

		ItemStack fromClient = sent.copy();
		fromClient.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.NETHERITE_BLOCK, 64))));
		Boxes.fromCreativeClient(player, fromClient);
		helper.assertTrue(SyncedBox.isEmptyBox(fromClient), "contents from a creative client are dropped for a member");
		ItemStack anchorFromClient = fromClient.copy();
		SyncedBox.link(anchorFromClient, new SyncedBox.Link(group.id(), anchor));
		anchorFromClient.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.NETHERITE_BLOCK, 64))));
		Boxes.fromCreativeClient(player, anchorFromClient);
		helper.assertTrue(anchorFromClient.get(DataComponents.CONTAINER).nonEmptyItemCopyStream().allMatch(stack -> stack.is(Items.EMERALD)),
			"the anchor gets the group's contents, not the client's");
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.succeed();
	}
}
