package de.chatlanes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

/**
 * Setzt den Skin-Kopf vor den Spielernamen einer Chatnachricht (nur für die Anzeige).
 * Nutzt die eingebaute Kopf-Darstellung von Minecraft – die Skins kommen aus der Spielerliste
 * des Servers bzw. von Mojang, es werden keine fremden Webseiten abgefragt.
 */
public final class PlayerHeads {
	/** "Name: Text", "<Name> Text", "From Name:", "[Name -> mir]" … */
	private static final Pattern NAME = Pattern.compile("(?<![A-Za-z0-9_])([A-Za-z0-9_]{2,16})(?=: |> |\\s?->|\\])");
	/** Namen, die in dieser Sitzung online waren – damit auch gespeicherte Nachrichten Köpfe bekommen. */
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
	private static final Map<String, Component> CACHE = new HashMap<>();

	private PlayerHeads() {
	}

	/** Kopf-Komponente für einen Spieler (mit oder ohne zweite Hautschicht). */
	private static Component head(String name, boolean hat) {
		String key = name.toLowerCase(Locale.ROOT) + (hat ? "|h" : "|n");
		return CACHE.computeIfAbsent(key, k -> {
			ResolvableProfile profile = null;
			ClientPacketListener connection = Minecraft.getInstance().getConnection();
			if (connection != null) {
				PlayerInfo info = connection.getPlayerInfo(name);
				if (info != null) {
					profile = ResolvableProfile.createResolved(info.getProfile());
				}
			}
			if (profile == null) {
				profile = ResolvableProfile.createUnresolved(name);
			}
			return Component.object(new PlayerSprite(profile, hat));
		});
	}

	private static boolean isKnownPlayer(String name) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection != null) {
			for (PlayerInfo info : connection.getOnlinePlayers()) {
				String online = info.getProfile().name();
				SEEN.add(online.toLowerCase(Locale.ROOT));
			}
		}
		return SEEN.contains(name.toLowerCase(Locale.ROOT));
	}

	/** Profil-Cache leeren (z. B. nach Serverwechsel, damit Skins neu geladen werden). */
	public static void clearCache() {
		CACHE.clear();
	}

	/**
	 * Fügt vor dem ersten Spielernamen den Kopf ein. Gibt null zurück, wenn kein bekannter Spieler gefunden wurde.
	 * @param knownPlayer Spieler aus der Msg-Erkennung (oder null)
	 */
	public static @Nullable Component decorate(Component content, @Nullable String knownPlayer, boolean hat) {
		// Text in Stücke mit Stil zerlegen (Farben, Klick- und Hover-Aktionen bleiben erhalten)
		record Part(String text, Style style) {
		}
		List<Part> parts = new ArrayList<>();
		StringBuilder plain = new StringBuilder();
		content.visit((style, text) -> {
			parts.add(new Part(text, style));
			plain.append(text);
			return Optional.empty();
		}, Style.EMPTY);

		int index = -1;
		String name = null;
		if (knownPlayer != null) {
			Matcher m = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(knownPlayer) + "(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE)
				.matcher(plain);
			if (m.find()) {
				index = m.start();
				name = knownPlayer;
			}
		}
		if (index < 0) {
			Matcher m = NAME.matcher(plain);
			while (m.find()) {
				if (isKnownPlayer(m.group(1))) {
					index = m.start(1);
					name = m.group(1);
					break;
				}
			}
		}
		if (index < 0) {
			return null;
		}

		MutableComponent result = Component.empty();
		int pos = 0;
		boolean inserted = false;
		for (Part part : parts) {
			int end = pos + part.text().length();
			if (!inserted && index >= pos && index < end) {
				int split = index - pos;
				if (split > 0) {
					result.append(Component.literal(part.text().substring(0, split)).setStyle(part.style()));
				}
				result.append(head(name, hat)).append(Component.literal(" "));
				result.append(Component.literal(part.text().substring(split)).setStyle(part.style()));
				inserted = true;
			} else {
				result.append(Component.literal(part.text()).setStyle(part.style()));
			}
			pos = end;
		}
		return inserted ? result : null;
	}
}
