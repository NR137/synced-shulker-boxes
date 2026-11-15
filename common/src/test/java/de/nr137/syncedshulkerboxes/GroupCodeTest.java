package de.nr137.syncedshulkerboxes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import de.nr137.syncedshulkerboxes.group.Group;

class GroupCodeTest {
	@Test
	void codesAreFourCharactersWithoutConfusableLetters() {
		for (int i = 0; i < 1000; i++) {
			String code = Group.code(UUID.randomUUID());
			assertEquals(4, code.length());
			assertTrue(code.chars().noneMatch(c -> "ILOU".indexOf(c) >= 0), code);
		}
	}

	@Test
	void theCodeComesFromTheStartOfTheUuid() {
		assertEquals("0000", Group.code(new UUID(0, 0)));
		assertEquals("ZZZZ", Group.code(new UUID(-1L, 0)));
	}
}
