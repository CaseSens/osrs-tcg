package com.osrstcg.persist;

import com.google.gson.Gson;
import com.osrstcg.model.CardCollectionKey;
import com.osrstcg.model.CollectionState;
import com.osrstcg.model.OwnedCardInstance;
import com.osrstcg.model.SkillCreditBaseline;
import com.osrstcg.model.TcgState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class TcgStateCodecTest
{
	private final TcgStateCodec codec = new TcgStateCodec(new Gson());

	@Test
	public void fromJsonUpgradesMissingSkillBaselineAndProfileMeta()
	{
		String legacy = "{"
			+ "\"schemaVersion\":3,"
			+ "\"credits\":500,"
			+ "\"openedPacks\":1,"
			+ "\"cardInstances\":[]"
			+ "}";

		TcgState state = codec.fromJson(legacy);
		Assert.assertEquals(TcgState.CURRENT_SCHEMA_VERSION, state.getSchemaVersion());
		Assert.assertEquals(500L, state.getEconomyState().getCredits());
		Assert.assertTrue(state.getSkillCreditBaseline().needsSchemaUpgradePersist());
		Assert.assertFalse(state.getSkillCreditBaseline().isPresent());
		Assert.assertEquals(0L, state.getTotalCreditsGained());
		Assert.assertEquals(0L, state.getProfileCreatedAtUnix());

		String upgraded = codec.toJson(state.withProfileCreatedAtUnix(1_700_000_000L));
		Assert.assertTrue(upgraded.contains("\"schemaVersion\":6") || upgraded.contains("\"schemaVersion\": 6"));
		Assert.assertTrue(upgraded.contains("skillCreditBaseline"));
		Assert.assertTrue(upgraded.contains("totalCreditsGained"));
		Assert.assertTrue(upgraded.contains("profileCreatedAtUnix"));
		Assert.assertTrue(upgraded.contains("profileSavedAtUnix"));
		Assert.assertTrue(upgraded.contains("cardEntries"));

		TcgState reloaded = codec.fromJson(upgraded);
		Assert.assertFalse(reloaded.getSkillCreditBaseline().needsSchemaUpgradePersist());
		Assert.assertFalse(reloaded.getSkillCreditBaseline().isPresent());
		Assert.assertEquals(1_700_000_000L, reloaded.getProfileCreatedAtUnix());
		Assert.assertEquals(0L, reloaded.getTotalCreditsGained());
		Assert.assertEquals(0L, reloaded.getProfileSavedAtUnix());
	}

	@Test
	public void roundTripsProfileSavedAtUnix()
	{
		TcgState state = TcgState.empty()
			.withProfileSavedAtUnix(1_700_000_100L);
		TcgState loaded = codec.fromJson(codec.toJson(state));
		Assert.assertEquals(1_700_000_100L, loaded.getProfileSavedAtUnix());
	}

	@Test
	public void roundTripsPresentSkillBaselineBySkillName()
	{
		Map<String, Integer> xp = new LinkedHashMap<>();
		xp.put("Attack", 1000);
		xp.put("Cooking", 55_000);
		TcgState state = TcgState.empty()
			.withCredits(10L)
			.withTotalCreditsGained(1_234L)
			.withSkillCreditBaseline(SkillCreditBaseline.of(xp, 250L));

		TcgState loaded = codec.fromJson(codec.toJson(state));
		Assert.assertTrue(loaded.getSkillCreditBaseline().isPresent());
		Assert.assertEquals(250L, loaded.getSkillCreditBaseline().getUncreditedXp());
		Assert.assertEquals(1000, loaded.getSkillCreditBaseline().xpFor(net.runelite.api.Skill.ATTACK).orElse(-1));
		Assert.assertEquals(55_000, loaded.getSkillCreditBaseline().xpFor(net.runelite.api.Skill.COOKING).orElse(-1));
		Assert.assertEquals(1_234L, loaded.getTotalCreditsGained());
		Assert.assertTrue(loaded.getProfileCreatedAtUnix() > 0L);
	}

	@Test
	public void roundTripsCardEntriesWithVariants()
	{
		List<OwnedCardInstance> instances = List.of(
			OwnedCardInstance.createNew("Abyssal whip", false, "Player", 1_710_000_000_000L),
			OwnedCardInstance.createNew("Abyssal whip", false, "Player", 1_710_000_010_000L),
			OwnedCardInstance.createNew("Abyssal whip", true, "Player", 1_710_000_020_000L)
		);
		TcgState state = TcgState.empty().withCollection(CollectionState.copyOf(instances));

		String json = codec.toJson(state);
		Assert.assertTrue(json.contains("cardEntries"));
		Assert.assertFalse(json.contains("cardInstances"));
		Assert.assertTrue(json.contains("\"foil\":true"));
		Assert.assertFalse(json.contains("\"foil\":false"));
		Assert.assertFalse(json.contains("quantity"));

		TcgState loaded = codec.fromJson(json);
		Map<CardCollectionKey, Integer> owned = loaded.getCollectionState().getOwnedCards();
		Assert.assertEquals(2, owned.get(new CardCollectionKey("Abyssal whip", false)).intValue());
		Assert.assertEquals(1, owned.get(new CardCollectionKey("Abyssal whip", true)).intValue());
		Assert.assertEquals(3, loaded.getCollectionState().getOwnedInstances().size());
	}

	@Test
	public void readsLegacyCardInstances()
	{
		String legacy = "{"
			+ "\"schemaVersion\":5,"
			+ "\"credits\":0,"
			+ "\"openedPacks\":0,"
			+ "\"cardInstances\":["
			+ "{\"id\":\"abc\",\"cardName\":\"Abyssal whip\",\"foil\":false,\"pulledBy\":\"Player\",\"pulledAt\":1000},"
			+ "{\"id\":\"def\",\"cardName\":\"Abyssal whip\",\"foil\":true,\"pulledBy\":\"Player\",\"pulledAt\":2000}"
			+ "]"
			+ "}";

		TcgState loaded = codec.fromJson(legacy);
		Assert.assertEquals(2, loaded.getCollectionState().getOwnedInstances().size());
		Assert.assertEquals("abc", loaded.getCollectionState().getOwnedInstances().get(0).getInstanceId());
	}

	@Test
	public void roundTripsLockedVariant()
	{
		OwnedCardInstance locked = OwnedCardInstance.createNew("Rune scimitar", false, "Player", 100L).withLocked(true);
		OwnedCardInstance unlocked = OwnedCardInstance.createNew("Rune scimitar", false, "Player", 200L);
		TcgState state = TcgState.empty().withCollection(CollectionState.copyOf(List.of(locked, unlocked)));

		String json = codec.toJson(state);
		Assert.assertTrue(json.contains("\"locked\":true"));
		Assert.assertFalse(json.contains("lockedQuantity"));

		TcgState loaded = codec.fromJson(json);
		long lockedCount = loaded.getCollectionState().getOwnedInstances().stream().filter(OwnedCardInstance::isLocked).count();
		Assert.assertEquals(1L, lockedCount);
	}

	@Test
	public void readsLegacyQuantityInVariants()
	{
		String json = "{"
			+ "\"schemaVersion\":6,"
			+ "\"credits\":0,"
			+ "\"openedPacks\":0,"
			+ "\"cardEntries\":[{\"cardName\":\"Rune scimitar\",\"variants\":[{\"quantity\":2,\"lockedQuantity\":1,\"pulledBy\":\"Player\",\"pulledAt\":100}]}]"
			+ "}";

		TcgState loaded = codec.fromJson(json);
		Assert.assertEquals(2, loaded.getCollectionState().getOwnedInstances().size());
		long lockedCount = loaded.getCollectionState().getOwnedInstances().stream().filter(OwnedCardInstance::isLocked).count();
		Assert.assertEquals(1L, lockedCount);
	}

	@Test
	public void readsCardEntriesWithoutFoilFieldAsNormal()
	{
		String json = "{"
			+ "\"schemaVersion\":6,"
			+ "\"credits\":0,"
			+ "\"openedPacks\":0,"
			+ "\"cardEntries\":[{\"cardName\":\"Rune scimitar\",\"variants\":[{\"pulledBy\":\"Player\",\"pulledAt\":100}]}]"
			+ "}";

		TcgState loaded = codec.fromJson(json);
		Assert.assertEquals(1, loaded.getCollectionState().getOwnedInstances().size());
		Assert.assertFalse(loaded.getCollectionState().getOwnedInstances().get(0).isFoil());
	}
}
