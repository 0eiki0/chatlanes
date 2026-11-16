package de.chattabs.gui;

import de.chattabs.ChatTab;
import de.chattabs.config.ChatTabsConfig.Rule;
import de.chattabs.config.ChatTabsConfig.TabConfig;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * Zusätzliche Regeln eines Kanals: Nachrichten, die sonst im System-Tab landen würden,
 * erscheinen in diesem Kanal, wenn eine Regel passt (z. B. "Enthält Text: Aktueller Chat").
 */
class ExtraRulesScreen extends BaseSettingsScreen {
	private static String lastTestMessage = "Aktueller Chat: Global";

	private final TabConfig tab;
	private int page;
	private int rowsPerPage = 1;
	private int rowsTop;
	private int testY;

	ExtraRulesScreen(TabEditScreen parent, TabConfig tab) {
		super(Component.translatable("chattabs.config.extra.title", tab.name), parent);
		this.tab = tab;
	}

	/** Passt die Nachricht auf eine der zusätzlichen Regeln? (für den Live-Test) */
	static boolean matchesAny(TabConfig tab, String text) {
		for (Rule rule : tab.extraRulesOrEmpty()) {
			if (rule.match != TabConfig.Match.COMMAND && isValid(rule)) {
				Pattern pattern = ChatTab.buildPattern(rule.match, rule.pattern);
				if (pattern != null && pattern.matcher(text).find()) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isValid(Rule rule) {
		if (rule.match != TabConfig.Match.REGEX) {
			return true;
		}
		try {
			Pattern.compile(rule.pattern);
			return true;
		} catch (PatternSyntaxException e) {
			return false;
		}
	}

	@Override
	protected void init() {
		this.beginLayout(380);
		List<Rule> rules = this.tab.extraRulesOrEmpty();
		int y = TOP;

		this.label(t("extra.info"), this.left, y + 2);
		y += 14;

		CycleButton<Boolean> alsoSystem = onOff(this.tab.extraAlsoInSystem)
			.create(this.left, y, this.contentWidth, 20, t("extra.also_system"), (b, v) -> this.tab.extraAlsoInSystem = v);
		alsoSystem.setTooltip(Tooltip.create(t("extra.also_system.tooltip")));
		this.addRenderableWidget(alsoSystem);
		y += ROW + 4;

		// Regel-Liste
		this.rowsTop = y;
		int bottomY = this.height - 26;
		this.testY = bottomY - 4 - 12 - ROW;
		this.rowsPerPage = Math.max(1, (this.testY - 4 - this.rowsTop) / ROW);
		int pages = this.pageCount();
		this.page = Math.max(0, Math.min(this.page, pages - 1));

		int matchWidth = 110;
		int deleteWidth = 20;
		int start = this.page * this.rowsPerPage;
		for (int i = start; i < Math.min(rules.size(), start + this.rowsPerPage); i++) {
			Rule rule = rules.get(i);
			int rowY = this.rowsTop + (i - start) * ROW;
			int index = i;

			this.addRenderableWidget(CycleButton.<TabConfig.Match>builder(v -> t("match." + v.name().toLowerCase()), rule.match)
				.withValues(TabConfig.Match.values())
				.displayOnlyValue()
				.create(this.left, rowY, matchWidth, 20, t("match"), (b, v) -> {
					rule.match = v;
					this.rebuildWidgets();
				}));

			boolean command = rule.match == TabConfig.Match.COMMAND;
			int secondsWidth = command ? 56 : 0;
			EditBox value = new EditBox(this.font, this.left + matchWidth + 3, rowY + 1,
				this.contentWidth - matchWidth - deleteWidth - 6 - (command ? secondsWidth + 3 : 0), 18, t("match"));
			value.setMaxLength(1024);
			value.setValue(rule.pattern);
			value.moveCursorToStart(false);
			value.setHint(t("edit.value." + rule.match.name().toLowerCase() + ".hint"));
			value.setTooltip(Tooltip.create(t("edit.value." + rule.match.name().toLowerCase() + ".tooltip")));
			value.setTextColor(isValid(rule) ? 0xFFE0E0E0 : ERROR_COLOR);
			value.setResponder(v -> {
				rule.pattern = v;
				value.setTextColor(isValid(rule) ? 0xFFE0E0E0 : ERROR_COLOR);
			});
			this.addRenderableWidget(value);

			if (command) {
				// Wie lange nach dem Befehl die Ausgabe eingefangen wird
				CycleButton<Double> seconds = CycleButton.<Double>builder(v -> t("extra.seconds", formatSeconds(v)), rule.secondsOrDefault())
					.withValues(secondValues(rule.secondsOrDefault()))
					.displayOnlyValue()
					.create(this.left + this.contentWidth - deleteWidth - 3 - secondsWidth, rowY, secondsWidth, 20, t("extra.seconds.name"),
						(b, v) -> rule.seconds = v);
				seconds.setTooltip(Tooltip.create(t("extra.seconds.tooltip")));
				this.addRenderableWidget(seconds);
			}

			Button delete = Button.builder(Component.literal("✕"), b -> {
				rules.remove(index);
				this.rebuildWidgets();
			}).bounds(this.left + this.contentWidth - deleteWidth, rowY, deleteWidth, 20).build();
			delete.setTooltip(Tooltip.create(t("extra.delete")));
			this.addRenderableWidget(delete);
		}

		// Live-Test
		this.textRow(this.testY, t("edit.test"), lastTestMessage, null, t("extra.test.tooltip"), v -> lastTestMessage = v);

		// Untere Leiste: [+ Regel] [<] [>] ... [Fertig]
		int x = this.left;
		this.addRenderableWidget(Button.builder(t("extra.add"), b -> {
			rules.add(new Rule(TabConfig.Match.CONTAINS, ""));
			this.page = (rules.size() - 1) / this.rowsPerPage;
			this.rebuildWidgets();
		}).bounds(x, bottomY, 90, 20).build());
		x += 92;
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
		this.addRenderableWidget(Button.builder(t("done"), b -> this.onClose())
			.bounds(this.left + this.contentWidth - 90, bottomY, 90, 20).build());
	}

	/** Auswahl an Sekunden – enthält immer auch den aktuell gespeicherten Wert. */
	private static List<Double> secondValues(double current) {
		java.util.TreeSet<Double> values = new java.util.TreeSet<>(List.of(
			0.1, 0.25, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0, 15.0, 20.0, 30.0, 45.0, 60.0));
		values.add(current);
		return List.copyOf(values);
	}

	/** 0.5 → "0,5" (Deutsch) bzw. "0.5" (Englisch), 3.0 → "3" */
	private static String formatSeconds(double value) {
		java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols(java.util.Locale.ROOT);
		symbols.setDecimalSeparator(net.minecraft.client.resources.language.I18n.get("chattabs.config.decimal_separator").charAt(0));
		return new java.text.DecimalFormat("0.##", symbols).format(value);
	}

	private int pageCount() {
		return Math.max(1, (this.tab.extraRulesOrEmpty().size() + this.rowsPerPage - 1) / this.rowsPerPage);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (this.tab.extraRulesOrEmpty().isEmpty()) {
			graphics.text(this.font, t("extra.empty"), this.left, this.rowsTop + 6, LABEL_COLOR);
		}
		int pages = this.pageCount();
		if (pages > 1) {
			graphics.text(this.font, (this.page + 1) + "/" + pages, this.left + 92 + 48, this.height - 20, LABEL_COLOR);
		}

		boolean matches = matchesAny(this.tab, lastTestMessage);
		Component result;
		result = this.tab.extraRulesOrEmpty().isEmpty()
			? t("extra.test.empty")
			: t(matches ? "extra.test.match" : "extra.test.no_match", this.tab.name);
		int color = this.tab.extraRulesOrEmpty().isEmpty() ? LABEL_COLOR : matches ? OK_COLOR : ERROR_COLOR;
		boolean hasCommand = this.tab.extraRulesOrEmpty().stream().anyMatch(r -> r.match == TabConfig.Match.COMMAND);
		if (!matches && hasCommand) {
			// Befehl-Ausgaben lassen sich nicht am Text testen – sie werden beim Ausführen des Befehls erkannt.
			result = t("extra.test.command");
			color = LABEL_COLOR;
		}
		graphics.text(this.font, result, this.left + this.labelWidth, this.testY + ROW, color);
	}
}
