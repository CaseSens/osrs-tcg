package com.osrstcg.ui.collectionalbum;

import com.osrstcg.model.OwnedCardInstance;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

public final class AlbumInstanceTooltip
{
	public static final String LOCKED_ACTION_HINT =
		"This card is locked. Right-click it to unlock before selling or sending.";

	private AlbumInstanceTooltip()
	{
	}

	public static String format(OwnedCardInstance o)
	{
		if (o == null)
		{
			return null;
		}
		String displayBy = OwnedCardInstance.formatPulledByForUi(o.getPulledByUsername());
		long at = o.getPulledAtEpochMs();
		StringBuilder sb = new StringBuilder();
		if (!displayBy.isEmpty())
		{
			sb.append("Pulled by: ").append(displayBy);
		}
		if (at > 0L)
		{
			if (sb.length() > 0)
			{
				sb.append('\n');
			}
			String when = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
				.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()));
			sb.append(when);
		}
		if (o.isLocked())
		{
			if (sb.length() > 0)
			{
				sb.append('\n');
			}
			sb.append("Locked");
		}
		return sb.length() == 0 ? null : sb.toString();
	}
}
