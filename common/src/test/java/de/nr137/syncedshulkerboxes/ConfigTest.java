package de.nr137.syncedshulkerboxes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigTest {
	@Test
	void optionsMissingFromTheFileKeepTheirDefaultsAndAreWrittenIn(@TempDir Path folder) throws Exception {
		Path file = folder.resolve("synced_shulker_boxes.json");
		Files.writeString(file, "{\"maxGroupSize\": 4, \"permissions\": {\"open\": \"operators\"}}");
		Config config = Config.load(file);
		assertEquals(4, config.maxGroupSize());
		assertEquals(5, config.linkCostLevels(), "missing: the default, not 0");
		assertEquals(true, config.ambientParticles(), "missing: the default (on), not false");
		assertEquals(false, config.forEveryone(Permission.OPEN));
		assertEquals(true, config.forEveryone(Permission.LINK));
		assertTrue(Files.readString(file).contains("linkCostLevels"), "the file now has every option");
	}

	@Test
	void valuesOutsideTheLimitsAreClamped(@TempDir Path folder) throws Exception {
		Path file = folder.resolve("synced_shulker_boxes.json");
		Files.writeString(file, "{\"maxGroupSize\": 1, \"linkCostLevels\": 99}");
		Config config = Config.load(file);
		assertEquals(Config.MIN_GROUP_SIZE, config.maxGroupSize());
		assertEquals(Config.MAX_LINK_COST, config.linkCostLevels());
	}
}
