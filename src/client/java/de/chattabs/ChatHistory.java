package de.chattabs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import de.chattabs.mixin.ChatComponentAccessor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Speichert die Nachrichten aller Tabs pro Server in config/chattabs/history/<server>.jsonl
 * und stellt sie beim nächsten Betreten des Servers wieder her (sie landen dabei wieder in den richtigen Tabs).
 */
public final class ChatHistory {
	private static final Logger LOGGER = LoggerFactory.getLogger("chattabs");
	private static final ChatHistory INSTANCE = new ChatHistory();

	/** Schreiben passiert im Hintergrund, damit das Spiel nicht ruckelt. */
	private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "ChatTabs-History");
		thread.setDaemon(true);
		return thread;
	});

	private @Nullable Path file;
	private @Nullable RegistryOps<JsonElement> ops;
	private boolean restoring;
	private boolean restoredThisSession;
	private int linesInFile;

	private ChatHistory() {
	}

	public static ChatHistory get() {
		return INSTANCE;
	}

	public static Path directory() {
		return FabricLoader.getInstance().getConfigDir().resolve("chattabs").resolve("history");
	}

	public boolean isRestoring() {
		return this.restoring;
	}

	/** Dateiname aus Serveradresse bzw. Weltname, z. B. "server_cubeside.de.jsonl". */
	private static String serverKey(Minecraft minecraft) {
		ServerData server = minecraft.getCurrentServer();
		String key;
		if (server != null && server.ip != null && !server.ip.isBlank()) {
			key = "server_" + server.ip;
		} else if (minecraft.getSingleplayerServer() != null) {
			key = "world_" + minecraft.getSingleplayerServer().getWorldData().getLevelName();
		} else {
			key = "unknown";
		}
		return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
	}

	public void onJoin(ClientPacketListener connection, Minecraft minecraft) {
		this.ops = connection.registryAccess().createSerializationContext(JsonOps.INSTANCE);
		this.file = directory().resolve(serverKey(minecraft) + ".jsonl");
		if (!ChatTabs.get().config().saveHistory) {
			return;
		}

		if (this.restoredThisSession) {
			// z. B. Server-Transfer: Minecraft hat den Chat selbst übernommen, nicht doppelt einfügen.
			return;
		}
		this.restoredThisSession = true;
		// Direkt lesen (die Datei ist auf maxHistory Zeilen begrenzt), damit der alte Verlauf VOR den neuen Nachrichten steht.
		List<String> lines = readLastLines(this.file, ChatTabs.get().config().maxHistory);
		this.linesInFile = lines.size();
		if (!lines.isEmpty()) {
			this.restore(minecraft, this.file, lines);
		}
	}

	/** Chat wurde beim Trennen geleert → beim nächsten Betreten wieder herstellen. */
	public void onChatCleared() {
		this.restoredThisSession = false;
	}

	public void onDisconnect() {
		this.file = null;
		this.ops = null;
	}

	private static List<String> readLastLines(Path path, int limit) {
		if (!Files.exists(path)) {
			return List.of();
		}
		try {
			List<String> all = Files.readAllLines(path, StandardCharsets.UTF_8);
			List<String> last = new ArrayList<>(all.subList(Math.max(0, all.size() - limit), all.size()));
			if (all.size() > limit) {
				// Datei auf das Limit kürzen, damit sie nicht endlos wächst.
				Files.write(path, last, StandardCharsets.UTF_8);
			}
			return last;
		} catch (IOException | UncheckedIOException e) {
			LOGGER.warn("Konnte gespeicherten Chatverlauf {} nicht lesen", path, e);
			return List.of();
		}
	}

	private void restore(Minecraft minecraft, Path path, List<String> lines) {
		if (this.ops == null || !path.equals(this.file)) {
			return;
		}
		ChatComponentAccessor chat = (ChatComponentAccessor) minecraft.gui.hud.getChat();
		long lastTime = 0L;
		this.restoring = true;
		try {
			for (String line : lines) {
				try {
					JsonObject json = JsonParser.parseString(line).getAsJsonObject();
					Component message = ComponentSerialization.CODEC.parse(this.ops, json.get("c")).getOrThrow();
					lastTime = json.has("t") ? json.get("t").getAsLong() : lastTime;
					chat.chattabs$addMessage(message, null, GuiMessageSource.SYSTEM_CLIENT, null);
				} catch (RuntimeException e) {
					LOGGER.debug("Überspringe beschädigte Zeile im Chatverlauf", e);
				}
			}

			String when = lastTime > 0 ? new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(lastTime)) : "?";
			Component separator = Component.translatable("chattabs.history.separator", when).withStyle(ChatFormatting.GRAY);
			ChatTabs.get().markEverywhere(separator);
			chat.chattabs$addMessage(separator, null, GuiMessageSource.SYSTEM_CLIENT, null);
		} finally {
			this.restoring = false;
		}
	}

	/** Neue Nachricht empfangen → im Hintergrund anhängen. */
	void onMessage(GuiMessage message) {
		Path path = this.file;
		RegistryOps<JsonElement> registryOps = this.ops;
		if (path == null || registryOps == null || this.restoring || !ChatTabs.get().config().saveHistory) {
			return;
		}

		JsonElement content;
		try {
			content = ComponentSerialization.CODEC.encodeStart(registryOps, message.content()).getOrThrow();
		} catch (RuntimeException e) {
			LOGGER.debug("Nachricht konnte nicht gespeichert werden", e);
			return;
		}
		JsonObject json = new JsonObject();
		json.addProperty("t", System.currentTimeMillis());
		json.add("c", content);
		String line = json.toString() + "\n";
		int limit = ChatTabs.get().config().maxHistory;

		this.io.submit(() -> {
			try {
				Files.createDirectories(path.getParent());
				Files.writeString(path, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				// Gelegentlich auf das Limit kürzen.
				if (++this.linesInFile > limit * 2) {
					this.linesInFile = readLastLines(path, limit).size();
				}
			} catch (IOException e) {
				LOGGER.warn("Konnte Chatverlauf nicht speichern: {}", e.getMessage());
			}
		});
	}

	/** Löscht alle gespeicherten Verläufe. @return Anzahl gelöschter Dateien */
	public int deleteAll() {
		Path dir = directory();
		if (!Files.isDirectory(dir)) {
			return 0;
		}
		int deleted = 0;
		try (Stream<Path> files = Files.list(dir)) {
			for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".jsonl")).toList()) {
				Files.deleteIfExists(path);
				deleted++;
			}
		} catch (IOException e) {
			LOGGER.warn("Konnte gespeicherte Chatverläufe nicht löschen", e);
		}
		this.linesInFile = 0;
		return deleted;
	}

	/** Anzahl gespeicherter Server/Welten – für die Anzeige im Menü. */
	public static int savedFileCount() {
		Path dir = directory();
		if (!Files.isDirectory(dir)) {
			return 0;
		}
		try (Stream<Path> files = Files.list(dir)) {
			return (int) files.filter(p -> p.getFileName().toString().endsWith(".jsonl")).count();
		} catch (IOException e) {
			return 0;
		}
	}
}
