package de.nr137.syncedshulkerboxes;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;
import de.nr137.syncedshulkerboxes.group.Group;
import de.nr137.syncedshulkerboxes.group.Groups;

/**
 * /syncedshulkers: {@code particles} for every player (what the server sends them); the rest for admins (permission
 * synced_shulker_boxes.admin, operators by default):
 * <ul>
 * <li>{@code held}: the group of the box in your main hand</li>
 * <li>{@code give <count>}: a new group of that many linked boxes, into your inventory</li>
 * <li>{@code group <code or id>}: its members (the first is the anchor) and contents</li>
 * <li>{@code group <id> give}: copies of its contents into your inventory (to rescue items)</li>
 * <li>{@code group <id> remove <member>}: forgets a member that is lost for good</li>
 * <li>{@code config}: the server's options; {@code config <option> <value>} changes one right away and saves it</li>
 * </ul>
 */
final class AdminCommands {
	private AdminCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		// The root is visible to everyone for "particles"; everything else needs the admin permission.
		dispatcher.register(Commands.literal("syncedshulkers")
			.then(particlesCommand())
			.then(Commands.literal("held").requires(AdminCommands::isAdmin).executes(AdminCommands::held))
			.then(configCommand().requires(AdminCommands::isAdmin))
			.then(Commands.literal("give").requires(AdminCommands::isAdmin)
				.then(Commands.argument("count", IntegerArgumentType.integer(2)).executes(AdminCommands::giveGroup)))
			.then(Commands.literal("group").requires(AdminCommands::isAdmin)
				.then(Commands.argument("id", StringArgumentType.word())
					.executes(context -> info(context, group(context)))
					.then(Commands.literal("give").executes(AdminCommands::give))
					.then(Commands.literal("remove")
						.then(Commands.argument("member", StringArgumentType.word()).executes(AdminCommands::remove))))));
	}

	/**
	 * /syncedshulkers particles [opening|always on|off]: what the server sends this player, for players without the
	 * mod (who can't change client settings). Permission synced_shulker_boxes.settings, everyone by default.
	 */
	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> particlesCommand() {
		var particles = Commands.literal("particles")
			.requires(source -> source.getPlayer() != null && Permission.SETTINGS.check(source.getPlayer()))
			.executes(AdminCommands::showParticles);
		for (boolean opening : List.of(true, false)) {
			for (boolean on : List.of(true, false)) {
				particles.then(Commands.literal(opening ? "opening" : "always").then(Commands.literal(on ? "on" : "off").executes(context -> {
					ServerPlayer player = context.getSource().getPlayerOrException();
					PlayerSettings settings = PlayerSettings.get(context.getSource().getServer());
					PlayerSettings.Choices current = settings.of(player.getUUID());
					settings.set(player.getUUID(), opening ? new PlayerSettings.Choices(on, current.ambientParticles())
						: new PlayerSettings.Choices(current.openingParticles(), on));
					return showParticles(context);
				})));
			}
		}
		return particles;
	}

	private static int showParticles(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		PlayerSettings.Choices choices = PlayerSettings.get(context.getSource().getServer()).of(player.getUUID());
		Config config = SyncedShulkerBoxes.config();
		context.getSource().sendSuccess(() -> Component.literal("Particles of synced shulker boxes sent to you: opening "
			+ state(choices.openingParticles(), config.openingParticles()) + ", always " + state(choices.ambientParticles(),
			config.ambientParticles()) + " (with the mod on your client, its settings decide too)"), false);
		return 1;
	}

	private static String state(boolean chosen, boolean serverSends) {
		return !serverSends ? "off on this server" : chosen ? "on" : "off";
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> configCommand() {
		var config = Commands.literal("config").executes(AdminCommands::showConfig)
			.then(Commands.literal("maxGroupSize").then(Commands.argument("value",
				IntegerArgumentType.integer(Config.MIN_GROUP_SIZE, Config.MAX_GROUP_SIZE)).executes(context -> setConfig(context,
				c -> c.withMaxGroupSize(IntegerArgumentType.getInteger(context, "value"))))))
			.then(Commands.literal("linkCostLevels").then(Commands.argument("value",
				IntegerArgumentType.integer(0, Config.MAX_LINK_COST)).executes(context -> setConfig(context,
				c -> c.withLinkCostLevels(IntegerArgumentType.getInteger(context, "value"))))))
			.then(Commands.literal("openingParticles").then(Commands.argument("value", com.mojang.brigadier.arguments.BoolArgumentType.bool())
				.executes(context -> setConfig(context,
					c -> c.withOpeningParticles(com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "value"))))))
			.then(Commands.literal("ambientParticles").then(Commands.argument("value", com.mojang.brigadier.arguments.BoolArgumentType.bool())
				.executes(context -> setConfig(context,
					c -> c.withAmbientParticles(com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "value"))))));
		var permission = Commands.literal("permission");
		for (Permission node : Permission.values()) {
			permission.then(Commands.literal(node.node())
				.then(Commands.literal("everyone").executes(context -> setConfig(context, c -> c.withPermission(node, true))))
				.then(Commands.literal("operators").executes(context -> setConfig(context, c -> c.withPermission(node, false)))));
		}
		return config.then(permission);
	}

	private static int setConfig(CommandContext<CommandSourceStack> context, java.util.function.UnaryOperator<Config> change) {
		SyncedShulkerBoxes.updateConfig(change);
		return showConfig(context);
	}

	private static int showConfig(CommandContext<CommandSourceStack> context) {
		Config config = SyncedShulkerBoxes.config();
		StringBuilder text = new StringBuilder("Synced Shulker Boxes config (saved):")
			.append("\n maxGroupSize: ").append(config.maxGroupSize())
			.append("\n linkCostLevels: ").append(config.linkCostLevels())
			.append("\n openingParticles: ").append(config.openingParticles())
			.append("\n ambientParticles: ").append(config.ambientParticles());
		for (Permission node : Permission.values()) {
			text.append("\n permission ").append(node.node()).append(": ").append(config.forEveryone(node) ? "everyone" : "operators");
		}
		context.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
		return 1;
	}

	private static boolean isAdmin(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
		}
		return Permission.ADMIN.check(player);
	}

	private static int held(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.literal("Only players hold boxes."));
			return 0;
		}
		Optional<SyncedBox> mark = SyncedBox.of(player.getMainHandItem());
		if (mark.isEmpty()) {
			context.getSource().sendFailure(Component.literal("That is not a synced shulker box."));
			return 0;
		}
		if (mark.get().link().isEmpty()) {
			context.getSource().sendSuccess(() -> Component.literal("A synced shulker box, not linked yet."), false);
			return 1;
		}
		SyncedBox.Link link = mark.get().link().get();
		Optional<Group> group = Groups.get(context.getSource().getServer()).group(link.group());
		if (group.isEmpty()) {
			context.getSource().sendFailure(Component.literal("Its group " + link.group() + " no longer exists."));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal("Member " + link.member()), false);
		return info(context, group);
	}

	private static int giveGroup(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		int count = IntegerArgumentType.getInteger(context, "count");
		int max = SyncedShulkerBoxes.config().maxGroupSize();
		if (player == null || count > max) {
			context.getSource().sendFailure(Component.literal(player == null ? "Only players can get boxes." : "At most " + max + " boxes."));
			return 0;
		}
		Groups groups = Groups.get(context.getSource().getServer());
		List<UUID> members = new java.util.ArrayList<>();
		for (int i = 0; i < count; i++) {
			members.add(UUID.randomUUID());
		}
		Group group = groups.create(groups.newGroupId(), null, members.get(0), members.get(1));
		members.stream().skip(2).forEach(member -> groups.addMember(group, member));
		for (int i = 0; i < members.size(); i++) {
			ItemStack box = SyncedBox.newUnlinked();
			SyncedBox.link(box, new SyncedBox.Link(group.id(), members.get(i)));
			player.getInventory().placeItemBackInInventory(box, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		context.getSource().sendSuccess(() -> Component.literal("Gave " + count + " linked boxes of group " + group.code() + " " + group.id()), true);
		return count;
	}

	/** A group by its code (K7QF, as players see it) or its full id. */
	private static Optional<Group> group(CommandContext<CommandSourceStack> context) {
		String text = StringArgumentType.getString(context, "id");
		Groups groups = Groups.get(context.getSource().getServer());
		try {
			return groups.group(UUID.fromString(text));
		} catch (IllegalArgumentException e) {
			return groups.all().stream().filter(group -> group.code().equalsIgnoreCase(text)).findFirst();
		}
	}

	private static int info(CommandContext<CommandSourceStack> context, Optional<Group> found) {
		if (found.isEmpty()) {
			context.getSource().sendFailure(Component.literal("No such group."));
			return 0;
		}
		Group group = found.get();
		List<UUID> members = group.members();
		StringBuilder text = new StringBuilder("Group " + group.code() + (group.name() != null ? " \"" + group.name() + "\"" : "")
			+ " (" + group.id() + "): " + members.size() + " boxes");
		for (int i = 0; i < members.size(); i++) {
			text.append("\n ").append(i + 1).append(". ").append(members.get(i)).append(i == 0 ? " (anchor)" : "");
		}
		long stacks = group.contents().getItems().stream().filter(stack -> !stack.isEmpty()).count();
		text.append("\n").append(stacks).append(" of 27 slots used");
		group.contents().getItems().stream().filter(stack -> !stack.isEmpty())
			.forEach(stack -> text.append("\n  ").append(stack.getCount()).append("x ").append(stack.getHoverName().getString()));
		context.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
		return members.size();
	}

	private static int give(CommandContext<CommandSourceStack> context) {
		Optional<Group> group = group(context);
		ServerPlayer player = context.getSource().getPlayer();
		if (group.isEmpty() || player == null) {
			context.getSource().sendFailure(Component.literal("No such group, or not run by a player."));
			return 0;
		}
		int given = 0;
		for (ItemStack stack : group.get().contents().getItems()) {
			if (!stack.isEmpty()) {
				player.getInventory().placeItemBackInInventory(stack.copy(), net.minecraft.util.Prediction.SERVER_ONLY);
				given++;
			}
		}
		int count = given;
		context.getSource().sendSuccess(() -> Component.literal("Gave copies of " + count + " stacks."), true);
		return given;
	}

	private static int remove(CommandContext<CommandSourceStack> context) {
		Optional<Group> group = group(context);
		if (group.isEmpty()) {
			context.getSource().sendFailure(Component.literal("No such group."));
			return 0;
		}
		UUID member;
		try {
			member = UUID.fromString(StringArgumentType.getString(context, "member"));
		} catch (IllegalArgumentException e) {
			context.getSource().sendFailure(Component.literal("Not a member id."));
			return 0;
		}
		if (!group.get().contains(member)) {
			context.getSource().sendFailure(Component.literal("Not a member of this group."));
			return 0;
		}
		if (group.get().members().size() == 1 && context.getSource().getPlayer() == null) {
			context.getSource().sendFailure(Component.literal("That is the last member: run this as a player to get its items."));
			return 0;
		}
		Optional<List<ItemStack>> left = Groups.get(context.getSource().getServer()).removeMember(group.get(), member);
		if (left.isPresent()) {
			ServerPlayer player = context.getSource().getPlayer();
			left.get().stream().filter(stack -> !stack.isEmpty()).forEach(stack -> {
				if (player != null) {
					player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
				}
			});
			context.getSource().sendSuccess(() -> Component.literal("That was the last member: the group is gone"
				+ (player != null ? ", its items are in your inventory." : ".")), true);
		} else {
			context.getSource().sendSuccess(() -> Component.literal("Removed the member."), true);
		}
		return 1;
	}
}
