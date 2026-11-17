package de.chatlanes.gui;

import de.chatlanes.ServerProfiles;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * Liste aller bekannten Server: pro Server ChatLanes an/aus, eigenes Profil bearbeiten,
 * auf Standard zurücksetzen oder entfernen. Dazu: Verhalten auf unbekannten Servern.
 * Änderungen hier werden sofort gespeichert.
 */
class ServerProfilesScreen extends BaseSettingsScreen {
	private static final int ROW_H = 24;

	private int page;
	private int rowsPerPage = 1;
	private int listTop;
	private List<String> keys = List.of();
	/** Server, bei dem "Zurücksetzen"/"Entfernen" gerade auf Bestätigung wartet. */
	private String confirmKey;
	private String confirmAction;

	ServerProfilesScreen(ChatLanesConfigScreen parent) {
		super(Component.translatable("chatlanes.profiles.title"), parent);
	}

	@Override
	protected void init() {
		this.beginLayout(440);
		int y = TOP;
		int w = this.contentWidth;

		CycleButton<Boolean> unknown = CycleButton.booleanBuilder(t("profiles.unknown.on"), t("profiles.unknown.off"), ServerProfiles.list().unknownEnabled)
			.create(this.left, y, w, 20, t("profiles.unknown"), (b, v) -> {
				ServerProfiles.list().unknownEnabled = v;
				ServerProfiles.saveList();
			});
		unknown.setTooltip(Tooltip.create(t("profiles.unknown.tooltip")));
		this.addRenderableWidget(unknown);
		y += ROW;

		Button standard = Button.builder(t("profiles.edit_standard"), b -> this.minecraft.gui.setScreen(new ChatLanesConfigScreen(this, null)))
			.bounds(this.left, y, w, 20).build();
		standard.setTooltip(Tooltip.create(t("profiles.edit_standard.tooltip")));
		this.addRenderableWidget(standard);
		y += ROW + 16;

		// Server, zuletzt besuchte zuerst
		List<Map.Entry<String, ServerProfiles.Entry>> entries = new ArrayList<>(ServerProfiles.list().servers.entrySet());
		entries.sort((x, z) -> Long.compare(z.getValue().lastJoin, x.getValue().lastJoin));
		List<String> sorted = new ArrayList<>();
		entries.forEach(e -> sorted.add(e.getKey()));
		this.keys = sorted;

		this.listTop = y;
		int bottomY = this.height - 26;
		this.rowsPerPage = Math.max(1, (bottomY - 4 - this.listTop) / ROW_H);
		int pages = Math.max(1, (this.keys.size() + this.rowsPerPage - 1) / this.rowsPerPage);
		this.page = Math.max(0, Math.min(this.page, pages - 1));

		int start = this.page * this.rowsPerPage;
		for (int i = start; i < Math.min(this.keys.size(), start + this.rowsPerPage); i++) {
			this.addRow(this.keys.get(i), this.listTop + (i - start) * ROW_H);
		}

		int x = this.left;
		if (pages > 1) {
			Button prev = Button.builder(Component.literal("<"), b -> {
				this.page--;
				this.rebuildWidgets();
			}).bounds(x, bottomY, 20, 20).build();
			prev.active = this.page > 0;
			this.addRenderableWidget(prev);
			Button next = Button.builder(Component.literal(">"), b -> {
				this.page++;
				this.rebuildWidgets();
			}).bounds(x + 22, bottomY, 20, 20).build();
			next.active = this.page < pages - 1;
			this.addRenderableWidget(next);
		}
		this.addRenderableWidget(Button.builder(t("back"), b -> this.onClose())
			.bounds(this.left + w - 120, bottomY, 120, 20).build());
	}

	private void addRow(String key, int y) {
		ServerProfiles.Entry entry = ServerProfiles.list().servers.get(key);
		boolean current = key.equals(ServerProfiles.activeKey());
		int small = 20;
		int toggleWidth = 50;
		int editWidth = 70;
		int resetWidth = 20;
		int x = this.left + this.contentWidth - (toggleWidth + editWidth + resetWidth + small + 6);

		CycleButton<Boolean> toggle = CycleButton.booleanBuilder(t("on"), t("off"), entry.enabled)
			.displayOnlyValue()
			.create(x, y, toggleWidth, 20, t("profiles.enabled"), (b, v) -> {
				ServerProfiles.setEnabled(key, v);
			});
		toggle.setTooltip(Tooltip.create(t("profiles.enabled.tooltip")));
		this.addRenderableWidget(toggle);
		x += toggleWidth + 2;

		this.addRenderableWidget(Button.builder(t("profiles.edit"), b -> this.minecraft.gui.setScreen(new ChatLanesConfigScreen(this, key)))
			.bounds(x, y, editWidth, 20).build());
		x += editWidth + 2;

		boolean confirmReset = key.equals(this.confirmKey) && "reset".equals(this.confirmAction);
		Button reset = Button.builder(Component.literal(confirmReset ? "!" : "↺"), b -> {
			if (confirmReset) {
				ServerProfiles.resetToStandard(key);
				this.confirmKey = null;
			} else {
				this.confirmKey = key;
				this.confirmAction = "reset";
			}
			this.rebuildWidgets();
		}).bounds(x, y, resetWidth, 20).build();
		reset.setTooltip(Tooltip.create(t(confirmReset ? "profiles.reset.confirm" : "profiles.reset")));
		this.addRenderableWidget(reset);
		x += resetWidth + 2;

		boolean confirmDelete = key.equals(this.confirmKey) && "delete".equals(this.confirmAction);
		Button delete = Button.builder(Component.literal(confirmDelete ? "!" : "✕"), b -> {
			if (confirmDelete) {
				ServerProfiles.remove(key);
				this.confirmKey = null;
			} else {
				this.confirmKey = key;
				this.confirmAction = "delete";
			}
			this.rebuildWidgets();
		}).bounds(x, y, small, 20).build();
		delete.active = !current;
		delete.setTooltip(Tooltip.create(t(current ? "profiles.delete.current" : confirmDelete ? "profiles.delete.confirm" : "profiles.delete")));
		this.addRenderableWidget(delete);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.text(this.font, t("profiles.list_header"), this.left, this.listTop - 11, LABEL_COLOR);

		if (this.keys.isEmpty()) {
			graphics.text(this.font, t("profiles.empty"), this.left, this.listTop + 6, LABEL_COLOR);
			return;
		}
		int start = this.page * this.rowsPerPage;
		SimpleDateFormat date = new SimpleDateFormat("dd.MM.yyyy");
		for (int i = start; i < Math.min(this.keys.size(), start + this.rowsPerPage); i++) {
			String key = this.keys.get(i);
			ServerProfiles.Entry entry = ServerProfiles.list().servers.get(key);
			int y = this.listTop + (i - start) * ROW_H;
			graphics.fill(this.left, y, this.left + this.contentWidth, y + 20, 0x60000000);
			boolean current = key.equals(ServerProfiles.activeKey());
			String name = ServerProfiles.displayName(key);
			graphics.text(this.font, name, this.left + 5, y + 2, current ? OK_COLOR : entry.enabled ? 0xFFFFFFFF : 0xFF888888);
			String info = entry.address + (entry.lastJoin > 0 ? " · " + date.format(new Date(entry.lastJoin)) : "");
			if (current) {
				info += " · " + t("profiles.current").getString();
			}
			graphics.text(this.font, info, this.left + 5, y + 11, LABEL_COLOR);
		}
	}
}
