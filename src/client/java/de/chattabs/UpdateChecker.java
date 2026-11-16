package de.chattabs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prüft einmal pro Spielstart (beim ersten Betreten einer Welt/eines Servers), ob auf GitHub
 * ein neueres Release liegt, und meldet das im Chat – mit Link zum Download.
 */
public final class UpdateChecker {
	private static final Logger LOGGER = LoggerFactory.getLogger("chattabs");
	private static final String API_URL = "https://api.github.com/repos/0eiki0/chattabs/releases/latest";
	private static boolean checked;

	private UpdateChecker() {
	}

	public static void onJoin(Minecraft minecraft) {
		if (checked || !ChatTabs.get().config().updateCheck) {
			return;
		}
		checked = true;
		String current = currentVersion();

		HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.proxy(java.net.ProxySelector.getDefault()) // System-Proxy beachten, falls eingestellt
			.build();
		HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
			.timeout(Duration.ofSeconds(15))
			.header("Accept", "application/vnd.github+json")
			.header("User-Agent", "ChatTabs-Mod/" + current)
			.GET()
			.build();

		client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
			if (response.statusCode() != 200) {
				LOGGER.info("Update-Prüfung: GitHub antwortete mit {}", response.statusCode());
				return;
			}
			JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
			String tag = release.get("tag_name").getAsString();
			String latest = baseVersion(tag);
			String url = release.has("html_url") ? release.get("html_url").getAsString() : "https://github.com/0eiki0/chattabs/releases";
			if (isNewer(latest, baseVersion(current))) {
				minecraft.execute(() -> notify(minecraft, current, latest, url));
			} else {
				LOGGER.info("Chat Tabs ist aktuell ({}; neuestes Release {})", current, tag);
			}
		}).exceptionally(error -> {
			LOGGER.info("Update-Prüfung nicht möglich: {}", error.getMessage());
			return null;
		});
	}

	private static void notify(Minecraft minecraft, String current, String latest, String url) {
		MutableComponent link = Component.translatable("chattabs.update.download").withStyle(Style.EMPTY
			.withColor(ChatFormatting.AQUA)
			.withUnderlined(true)
			.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(url))));
		Component message = Component.literal("[Chat Tabs] ").withStyle(ChatFormatting.GOLD)
			.append(Component.translatable("chattabs.update.available", latest, current).withStyle(ChatFormatting.YELLOW))
			.append(" ")
			.append(link);
		// In allen Tabs anzeigen und nicht im Verlauf speichern
		ChatTabs.get().markEverywhere(message);
		minecraft.gui.hud.getChat().addClientSystemMessage(message);
	}

	public static String currentVersion() {
		return FabricLoader.getInstance().getModContainer("chattabs")
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
			.orElse("0");
	}

	/** "v1.8.0-build.12" → "1.8.0" */
	static String baseVersion(String version) {
		String v = version.trim();
		if (v.startsWith("v") || v.startsWith("V")) {
			v = v.substring(1);
		}
		int cut = v.indexOf('-');
		if (cut >= 0) {
			v = v.substring(0, cut);
		}
		int plus = v.indexOf('+');
		return plus >= 0 ? v.substring(0, plus) : v;
	}

	/** Vergleicht Versionen wie 1.10.0 und 1.9.3 zahlenweise. */
	static boolean isNewer(String candidate, String current) {
		String[] a = candidate.split("\\.");
		String[] b = current.split("\\.");
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			int x = i < a.length ? parse(a[i]) : 0;
			int y = i < b.length ? parse(b[i]) : 0;
			if (x != y) {
				return x > y;
			}
		}
		return false;
	}

	private static int parse(String part) {
		try {
			return Integer.parseInt(part.replaceAll("[^0-9]", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
