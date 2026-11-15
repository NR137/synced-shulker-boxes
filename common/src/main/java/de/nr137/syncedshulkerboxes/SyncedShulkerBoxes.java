package de.nr137.syncedshulkerboxes;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.nr137.syncedshulkerboxes.group.Groups;

/** The loader-independent entry point: each loader's entry point (fabric/, neoforge/) calls these methods. */
public final class SyncedShulkerBoxes {
	public static final String MOD_ID = "synced_shulker_boxes";
	public static final String MOD_NAME = "Synced Shulker Boxes";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

	private static Platform platform;
	private static volatile Config config = Config.defaults();
	private static volatile boolean running;

	private SyncedShulkerBoxes() {
	}

	public static void init(Platform loaderPlatform) {
		platform = loaderPlatform;
	}

	public static Platform platform() {
		return platform;
	}

	public static Config config() {
		return config;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static void onServerStarting(MinecraftServer server) {
		config = Config.load(configFile());
	}

	private static java.nio.file.Path configFile() {
		return platform.configDir().resolve(MOD_ID + ".json");
	}

	/** Changes the config right away and saves it (/syncedshulkers config, the settings screen in singleplayer). */
	public static synchronized void updateConfig(java.util.function.UnaryOperator<Config> change) {
		config = change.apply(config);
		config.save(configFile());
	}

	public static void onServerStarted(MinecraftServer server) {
		running = true;
		// The smoke test waits for this line (scripts/smoke_test/smoke_test.py).
		LOGGER.info("{} {} is running on Minecraft {} ({} groups)", MOD_NAME, platform.modVersion(),
			platform.minecraftVersion(), Groups.get(server).all().size());
	}

	public static void onServerTick(MinecraftServer server) {
		Boxes.onServerTick(server);
	}

	public static void onServerStopping() {
		running = false;
	}

	public static void onServerStopped() {
		Groups.clearLive();
		Boxes.clearLoaded();
	}

	public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		AdminCommands.register(dispatcher);
	}

	public static boolean isRunning() {
		return running;
	}
}
