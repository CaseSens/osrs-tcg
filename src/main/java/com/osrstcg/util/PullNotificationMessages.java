package com.osrstcg.util;

import java.util.List;

public final class PullNotificationMessages
{
	private PullNotificationMessages()
	{
	}

	public static String collectionMessage(String playerName, String cardName, boolean newForCollection, boolean foil)
	{
		String who = playerName == null || playerName.trim().isEmpty() ? "Unknown player" : playerName.trim();
		String card = cardName == null ? "" : cardName.trim();
		String duplicatePrefix = newForCollection ? "" : "duplicate ";
		String foilSuffix = foil ? " (foil)" : "";
		return who + " just added " + duplicatePrefix + card + foilSuffix + " to their collection!";
	}

	public static String dinkCollectionMessage(String cardName, boolean newForCollection, boolean foil)
	{
		return collectionMessage("%USERNAME%", cardName, newForCollection, foil);
	}

	public static String dinkPackSummaryMessage(List<String> newCards, List<String> duplicates)
	{
		return "%USERNAME% opened a booster pack!\n\n"
			+ "**New cards**\n" + markdownCardList(newCards) + "\n\n"
			+ "**Duplicates**\n" + markdownCardList(duplicates);
	}

	private static String markdownCardList(List<String> cards)
	{
		if (cards == null || cards.isEmpty())
		{
			return "- None";
		}
		StringBuilder result = new StringBuilder();
		for (String card : cards)
		{
			if (result.length() > 0)
			{
				result.append('\n');
			}
			result.append("- ").append(card);
		}
		return result.toString();
	}
}
