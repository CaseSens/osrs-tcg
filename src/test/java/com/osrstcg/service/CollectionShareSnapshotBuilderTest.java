package com.osrstcg.service;

import com.osrstcg.model.CardEntry;
import com.osrstcg.model.CardVariant;
import com.osrstcg.model.CollectionState;
import com.osrstcg.model.OwnedCardInstance;
import com.osrstcg.model.TcgPublicStats;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class CollectionShareSnapshotBuilderTest
{
	@Test
	public void buildCardEntriesExcludesDebugAndGroupsByName()
	{
		List<OwnedCardInstance> instances = List.of(
			OwnedCardInstance.createNew("Abyssal whip", false, "Player", 1000L),
			OwnedCardInstance.createNew("Abyssal whip", false, "Player", 1001L),
			OwnedCardInstance.createNew("Abyssal whip", true, "Player", 1002L),
			OwnedCardInstance.createNew("Dragon scimitar", false,
				OwnedCardInstance.withDebugPullMetadataPrefix("Player"), 1003L),
			OwnedCardInstance.createNew("Twisted bow", false, "DEBUG_Other", 1004L)
		);

		List<CardEntry> entries = CollectionShareSnapshotBuilder.buildCardEntries(
			CollectionState.copyOf(instances));

		Assert.assertEquals(1, entries.size());
		CardEntry whip = entries.get(0);
		Assert.assertEquals("Abyssal whip", whip.cardName);
		Assert.assertEquals(3, whip.variants.size());

		Assert.assertFalse(CollectionShareSnapshotBuilder.isFoilVariant(whip.variants.get(0)));
		Assert.assertFalse(CollectionShareSnapshotBuilder.isFoilVariant(whip.variants.get(1)));
		Assert.assertTrue(CollectionShareSnapshotBuilder.isFoilVariant(whip.variants.get(2)));
		Assert.assertEquals(1000L, whip.variants.get(0).pulledAt.longValue());
		Assert.assertEquals(1001L, whip.variants.get(1).pulledAt.longValue());
		Assert.assertNull(whip.variants.get(0).foil);
		Assert.assertEquals(Boolean.TRUE, whip.variants.get(2).foil);
	}

	@Test
	public void buildCardEntriesOmitsEmptyPulledByAndLocked()
	{
		OwnedCardInstance whip = OwnedCardInstance.createNew("Abyssal whip", false, "Player", 1000L);
		OwnedCardInstance emptyPuller = OwnedCardInstance.createNew("Rune scimitar", false, "", 50L);
		OwnedCardInstance locked = OwnedCardInstance.createNew("Rune scimitar", false, "Player", 60L).withLocked(true);

		List<CardEntry> entries = CollectionShareSnapshotBuilder.buildCardEntries(
			CollectionState.copyOf(List.of(whip, emptyPuller, locked)));

		Assert.assertEquals(2, entries.size());
		CardVariant normal = entries.get(1).variants.get(0);
		Assert.assertNull(normal.pulledBy);
		Assert.assertNull(normal.locked);
		CardVariant lockedVariant = entries.get(1).variants.get(1);
		Assert.assertNull(lockedVariant.locked);
	}

	@Test
	public void buildPayloadUsesSchemaV2CardEntries()
	{
		OwnedCardInstance owned = OwnedCardInstance.createNew("Rune scimitar", false, "Player", 1L);
		CollectionState collection = CollectionState.copyOf(List.of(owned));
		TcgPublicStats stats = new TcgPublicStats(10L, 1.5d, 1, 0, 0.0d, 100, 3L, 1, false);

		Map<String, Object> payload = CollectionShareSnapshotBuilder.buildPayload(
			"1.0.0",
			"TestPlayer",
			stats,
			collection,
			Instant.parse("2026-07-17T00:00:00Z"));

		Assert.assertEquals(2, payload.get("schemaVersion"));
		Assert.assertEquals("1.0.0", payload.get("catalogVersion"));
		Assert.assertEquals("TestPlayer", payload.get("displayName"));
		Assert.assertEquals("2026-07-17T00:00:00Z", payload.get("updatedAt"));
		Assert.assertTrue(payload.get("stats") instanceof Map);
		Assert.assertFalse(payload.containsKey("cards"));
		Assert.assertFalse(payload.containsKey("instances"));
		Assert.assertTrue(payload.get("cardEntries") instanceof List);

		@SuppressWarnings("unchecked")
		List<CardEntry> cardEntries = (List<CardEntry>) payload.get("cardEntries");
		Assert.assertEquals(1, cardEntries.size());
		Assert.assertEquals("Rune scimitar", cardEntries.get(0).cardName);
		Assert.assertEquals(1, cardEntries.get(0).variants.size());
		Assert.assertEquals("Player", cardEntries.get(0).variants.get(0).pulledBy);
		Assert.assertEquals(1L, cardEntries.get(0).variants.get(0).pulledAt.longValue());

		@SuppressWarnings("unchecked")
		Map<String, Object> statsMap = (Map<String, Object>) payload.get("stats");
		Assert.assertEquals(10L, statsMap.get("collectionScore"));
		Assert.assertEquals(false, statsMap.get("customRates"));
		Assert.assertFalse(statsMap.containsKey("credits"));
	}

	@Test
	public void buildCardEntriesReturnsEmptyForNullOrEmptyCollection()
	{
		Assert.assertTrue(CollectionShareSnapshotBuilder.buildCardEntries(null).isEmpty());
		Assert.assertTrue(CollectionShareSnapshotBuilder.buildCardEntries(CollectionState.empty()).isEmpty());
	}
}
