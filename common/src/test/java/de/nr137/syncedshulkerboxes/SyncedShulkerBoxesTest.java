package de.nr137.syncedshulkerboxes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Unit tests of the loader-independent code: ./gradlew :common:test (./tasks.sh test-java). */
class SyncedShulkerBoxesTest {
	@Test
	void idsUseTheModNamespace() {
		assertEquals("synced_shulker_boxes:thing", SyncedShulkerBoxes.id("thing").toString());
	}
}
