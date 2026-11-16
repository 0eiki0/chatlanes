package de.chattabs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.chattabs.config.ChatTabsConfig;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

/**
 * Eigene Sprachwahl der Mod (unabhängig von der Minecraft-Sprache).
 * Liefert für Schlüssel der Mod den Text in der gewählten Sprache – oder null bei "Automatisch".
 */
public final class ModLanguage {
	private static final Map<ChatTabsConfig.Language, Map<String, String>> TABLES = new HashMap<>();
	/** Vorschau im Menü, bevor gespeichert wurde. */
	private static ChatTabsConfig.@Nullable Language preview;

	private ModLanguage() {
	}

	public static boolean isModKey(String key) {
		return key.startsWith("chattabs.") || key.startsWith("key.chattabs.") || key.startsWith("key.category.chattabs.");
	}

	public static void setPreview(ChatTabsConfig.@Nullable Language language) {
		preview = language;
	}

	public static @Nullable String lookup(String key) {
		ChatTabsConfig.Language language = preview != null ? preview : ChatTabs.get().config().language;
		if (language == null || language == ChatTabsConfig.Language.AUTO) {
			return null;
		}
		return table(language).get(key);
	}

	private static synchronized Map<String, String> table(ChatTabsConfig.Language language) {
		return TABLES.computeIfAbsent(language, lang -> {
			String file = lang == ChatTabsConfig.Language.GERMAN ? "de_de" : "en_us";
			Map<String, String> map = new HashMap<>();
			try (InputStream in = ModLanguage.class.getResourceAsStream("/assets/chattabs/lang/" + file + ".json")) {
				if (in != null) {
					JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
					json.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
				}
			} catch (Exception e) {
				LoggerFactory.getLogger("chattabs").warn("Konnte Sprachdatei {} nicht laden", file, e);
			}
			return map;
		});
	}
}
