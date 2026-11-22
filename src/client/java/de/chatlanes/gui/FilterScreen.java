package de.chatlanes.gui;

import de.chatlanes.ChatLanes;
import de.chatlanes.ChatTab;
import de.chatlanes.ChatWindows;
import de.chatlanes.ServerProfiles;
import de.chatlanes.config.ChatLanesConfig;
import de.chatlanes.config.ChatLanesConfig.Rule;
import de.chatlanes.config.ChatLanesConfig.TabConfig;
import de.chatlanes.config.TabColor;
import de.chatlanes.mixin.ChatComponentAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * "Filter erstellen": per Rechtsklick auf eine Nachricht im Chat. Legt eine Regel an, mit der passende
 * Nachrichten künftig in einem eigenen Fenster (oder einem Tab) landen.
 */
public class FilterScreen extends BaseSettingsScreen {
	/** Erkennt "[Kanal] Name: Text", "<Name> Text", "Name » Text" … → Gruppe "text" = ohne Absender. */
	private static final Pattern SENDER = Pattern.compile(
		"^(?:\\[[^\\]]*\\]\\s*)*(?:<[^>]{1,40}>|[~*]?[A-Za-z0-9_]{2,16})\\s*(?::|»|>|›|\\|)\\s+(?<text>.+)$");
	/** Kennung am Anfang, z. B. "[Advent 2026]" */
	private static final Pattern TAG = Pattern.compile("^\\s*(\\[[^\\]]{1,40}\\])");

	/** Vorlage für den Filtertext. */
	private enum Template { WHOLE, WITHOUT_SENDER, TAG }

	/** Ziel: ein neues Fenster, ein neuer Tab oder ein bestehender Kanal-Tab/ein bestehendes Fenster. */
	private record Target(@Nullable String existing, boolean window) {
		static final Target NEW_WINDOW = new Target(null, true);
		static final Target NEW_TAB = new Target(null, false);

		boolean isNew() {
			return this.existing == null;
		}
	}

	private final GuiMessage message;
	private final String plain;
	private final @Nullable String withoutSender;
	private final @Nullable String tag;
	private final List<Template> templates = new ArrayList<>();
	private final List<Target> targets = new ArrayList<>();

	private Template template = Template.WHOLE;
	private TabConfig.Match match = TabConfig.Match.CONTAINS;
	private String value;
	private Target target = Target.NEW_WINDOW;
	private String newName;
	private boolean alsoMain;
	private boolean nameEdited;

	private @Nullable EditBox valueBox;
	private int previewY;
	private int resultY;

	/** Zwischenspeicher für die Anzahl passender Nachrichten (nur neu zählen, wenn sich etwas ändert). */
	private String countedKey = "";
	private int countedMatches;
	private boolean countedSelf;

	public FilterScreen(@Nullable Screen parent, GuiMessage message) {
		super(Component.translatable("chatlanes.config.filter.title"), parent);
		this.message = message;
		String text = ChatFormatting.stripFormatting(message.content().getString());
		this.plain = text == null ? "" : text.strip();
		Matcher sender = SENDER.matcher(this.plain);
		this.withoutSender = sender.matches() ? sender.group("text").strip() : null;
		Matcher tagMatcher = TAG.matcher(this.plain);
		this.tag = tagMatcher.find() ? tagMatcher.group(1) : null;

		this.templates.add(Template.WHOLE);
		if (this.withoutSender != null && !this.withoutSender.isEmpty() && !this.withoutSender.equals(this.plain)) {
			this.templates.add(Template.WITHOUT_SENDER);
		}
		if (this.tag != null) {
			this.templates.add(Template.TAG);
		}
		// Ankündigungen mit Kennung (z. B. "[Advent 2026] …") lassen sich am besten an der Kennung erkennen
		this.template = this.tag != null && this.withoutSender == null ? Template.TAG : Template.WHOLE;
		this.value = this.templateValue(this.template);
		this.match = this.template == Template.TAG ? TabConfig.Match.TAG : TabConfig.Match.CONTAINS;

		this.targets.add(Target.NEW_WINDOW);
		this.targets.add(Target.NEW_TAB);
		for (TabConfig tab : ChatLanes.get().config().tabs) {
			if (tab.type == TabConfig.Type.CHANNEL) {
				this.targets.add(new Target(tab.name, tab.isWindow()));
			}
		}
		this.newName = this.suggestName();
	}

	private String templateValue(Template template) {
		return switch (template) {
			case WHOLE -> this.plain;
			case WITHOUT_SENDER -> this.withoutSender == null ? this.plain : this.withoutSender;
			case TAG -> this.tag == null ? this.plain : this.tag;
		};
	}

	/** Name für ein neues Fenster: die Kennung ohne Klammern, sonst "Filter" (eindeutig gemacht). */
	private String suggestName() {
		String base = this.template == Template.TAG && this.tag != null
			? this.tag.substring(1, this.tag.length() - 1).strip()
			: Component.translatable("chatlanes.config.filter.default_name").getString();
		if (base.isEmpty()) {
			base = "Filter";
		}
		if (base.length() > 24) {
			base = base.substring(0, 24).strip();
		}
		String name = base;
		for (int i = 2; this.nameTaken(name); i++) {
			String suffix = " " + i;
			name = (base.length() + suffix.length() > 24 ? base.substring(0, 24 - suffix.length()) : base) + suffix;
		}
		return name;
	}

	private boolean nameTaken(String name) {
		for (TabConfig tab : ChatLanes.get().config().tabs) {
			if (tab.name.equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	private @Nullable TabConfig existingTarget() {
		if (this.target.isNew()) {
			return null;
		}
		for (TabConfig tab : ChatLanes.get().config().tabs) {
			if (tab.name.equals(this.target.existing())) {
				return tab;
			}
		}
		return null;
	}

	/** Aktuellen Wert von "auch im normalen Chat" für das gewählte Ziel übernehmen. */
	private void loadAlsoMain() {
		TabConfig existing = this.existingTarget();
		if (existing == null) {
			this.alsoMain = false;
		} else if (existing.isWindow()) {
			this.alsoMain = existing.windowLayout != null && existing.windowLayout.alsoInMain;
		} else {
			this.alsoMain = existing.extraAlsoInSystem;
		}
	}

	private Component targetLabel(Target target) {
		if (target == Target.NEW_WINDOW) {
			return t("filter.target.new_window");
		}
		if (target == Target.NEW_TAB) {
			return t("filter.target.new_tab");
		}
		return t(target.window() ? "filter.target.window" : "filter.target.tab", target.existing());
	}

	@Override
	protected void init() {
		this.beginLayout(380);
		int half = this.halfWidth();
		int y = TOP;

		// Die Nachricht selbst (wird in extractRenderState gezeichnet)
		this.label(t("filter.message"), this.left, y + 2);
		this.previewY = y + 14;
		y += 14 + 12 + 4;

		CycleButton<Template> templateButton = CycleButton.<Template>builder(v -> t("filter.template." + v.name().toLowerCase(Locale.ROOT)), this.template)
			.withValues(this.templates)
			.create(this.left, y, half, 20, t("filter.template"), (b, v) -> {
				this.template = v;
				this.value = this.templateValue(v);
				this.match = v == Template.TAG ? TabConfig.Match.TAG : TabConfig.Match.CONTAINS;
				if (!this.nameEdited) {
					this.newName = this.suggestName();
				}
				this.rebuildWidgets();
			});
		templateButton.setTooltip(Tooltip.create(t("filter.template.tooltip")));
		templateButton.active = this.templates.size() > 1;
		this.addRenderableWidget(templateButton);

		CycleButton<TabConfig.Match> matchButton = CycleButton.<TabConfig.Match>builder(v -> t("match." + v.name().toLowerCase(Locale.ROOT)), this.match)
			.withValues(TabConfig.Match.MAIN)
			.create(this.rightHalfX(), y, half, 20, t("match"), (b, v) -> {
				this.match = v;
				if (v == TabConfig.Match.REGEX && this.value.equals(this.templateValue(this.template))) {
					// Text → Regex: Sonderzeichen maskieren, damit der Filter weiter genau diesen Text findet
					this.value = escapeRegex(this.value);
				}
				this.rebuildWidgets();
			});
		matchButton.setTooltip(Tooltip.create(t("match.tooltip")));
		this.addRenderableWidget(matchButton);
		y += ROW;

		String key = "edit.value." + this.match.name().toLowerCase(Locale.ROOT);
		this.valueBox = this.textRow(y, t(key), this.value, t(key + ".hint"), t(key + ".tooltip"), v -> {
			this.value = v;
			this.updateValueColor();
		});
		this.updateValueColor();
		y += ROW;

		CycleButton<Target> targetButton = CycleButton.<Target>builder(this::targetLabel, this.target)
			.withValues(this.targets)
			.displayOnlyValue()
			.create(this.left, y, this.target.isNew() ? half : this.contentWidth, 20, t("filter.target"), (b, v) -> {
				this.target = v;
				this.loadAlsoMain();
				this.rebuildWidgets();
			});
		targetButton.setTooltip(Tooltip.create(t("filter.target.tooltip")));
		this.addRenderableWidget(targetButton);
		if (this.target.isNew()) {
			EditBox name = new EditBox(this.font, this.rightHalfX(), y + 1, half, 18, t("edit.name"));
			name.setMaxLength(24);
			name.setValue(this.newName);
			name.setHint(t("filter.name.hint"));
			name.setTooltip(Tooltip.create(t("filter.name.tooltip")));
			name.setResponder(v -> {
				this.newName = v;
				this.nameEdited = true;
			});
			this.addRenderableWidget(name);
		}
		y += ROW;

		boolean window = this.target.window();
		CycleButton<Boolean> alsoMainButton = onOff(this.alsoMain)
			.create(this.left, y, this.contentWidth, 20, t(window ? "filter.also_main" : "extra.also_system"), (b, v) -> this.alsoMain = v);
		alsoMainButton.setTooltip(Tooltip.create(t(window ? "filter.also_main.tooltip" : "extra.also_system.tooltip")));
		this.addRenderableWidget(alsoMainButton);
		y += ROW + 4;

		this.resultY = y;

		int bottomY = this.height - 26;
		Button create = Button.builder(t("filter.create"), b -> this.create()).bounds(this.left, bottomY, half, 20).build();
		this.addRenderableWidget(create);
		this.addRenderableWidget(Button.builder(t("cancel"), b -> this.onClose()).bounds(this.rightHalfX(), bottomY, half, 20).build());
	}

	private static String escapeRegex(String text) {
		return text.replaceAll("[\\\\^$.|?*+()\\[\\]{}]", "\\\\$0");
	}

	private void updateValueColor() {
		if (this.valueBox != null) {
			this.valueBox.setTextColor(this.pattern() != null || this.value.isEmpty() ? 0xFFE0E0E0 : ERROR_COLOR);
		}
	}

	private @Nullable Pattern pattern() {
		if (this.value.isBlank()) {
			return null;
		}
		try {
			return ChatTab.buildPattern(this.match, this.value);
		} catch (PatternSyntaxException e) {
			return null;
		}
	}

	/** Wie viele Nachrichten im Verlauf passen, und passt die angeklickte Nachricht selbst? */
	private void count() {
		String key = this.match + "\u0000" + this.value;
		if (key.equals(this.countedKey)) {
			return;
		}
		this.countedKey = key;
		this.countedMatches = 0;
		this.countedSelf = false;
		Pattern pattern = this.pattern();
		if (pattern == null) {
			return;
		}
		List<GuiMessage> all = ((ChatComponentAccessor) this.minecraft.gui.hud.getChat()).chatlanes$getAllMessages();
		for (GuiMessage other : List.copyOf(all)) {
			String text = ChatFormatting.stripFormatting(other.content().getString());
			if (text != null && pattern.matcher(text).find()) {
				this.countedMatches++;
			}
		}
		this.countedSelf = pattern.matcher(this.plain).find();
	}

	private void create() {
		Pattern pattern = this.pattern();
		if (pattern == null) {
			return;
		}
		ChatLanesConfig working = ChatLanes.get().config().copy();
		TabConfig tab = null;
		if (this.target.isNew()) {
			String name = this.newName == null || this.newName.isBlank() ? this.suggestName() : this.newName.strip();
			tab = new TabConfig(name, TabConfig.Type.CHANNEL, TabConfig.Match.TAG, "", "", "", false, TabColor.DEFAULT);
			if (this.target.window()) {
				int windows = 0;
				for (TabConfig other : working.tabs) {
					if (other.isWindow()) {
						windows++;
					}
				}
				tab.window = true;
				tab.windowLayout = ChatWindows.defaultLayout(windows);
			}
			working.tabs.add(tab);
		} else {
			for (TabConfig other : working.tabs) {
				if (other.name.equals(this.target.existing())) {
					tab = other;
					break;
				}
			}
		}
		if (tab == null) {
			return;
		}
		tab.extraRulesOrEmpty().add(new Rule(this.match, this.value));
		if (tab.isWindow()) {
			tab.windowLayoutOrDefault().alsoInMain = this.alsoMain;
		} else {
			tab.extraAlsoInSystem = this.alsoMain;
		}
		ServerProfiles.save(ServerProfiles.activeKey(), working);
		this.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		// Angeklickte Nachricht (mit Farben, bei Bedarf gekürzt)
		graphics.fill(this.left, this.previewY - 2, this.left + this.contentWidth, this.previewY + 10, 0x80000000);
		FormattedCharSequence preview = this.font.width(this.message.content()) <= this.contentWidth - 6
			? this.message.content().getVisualOrderText()
			: net.minecraft.client.gui.components.ComponentRenderUtils.clipText(this.message.content(), this.font, this.contentWidth - 6);
		graphics.text(this.font, preview, this.left + 3, this.previewY, 0xFFFFFFFF, true);

		this.count();
		Component result;
		int color;
		if (this.pattern() == null) {
			result = t(this.value.isBlank() ? "edit.test.empty" : "edit.test.invalid");
			color = this.value.isBlank() ? LABEL_COLOR : ERROR_COLOR;
		} else if (!this.countedSelf) {
			result = t("filter.result.not_self");
			color = ERROR_COLOR;
		} else {
			result = this.countedMatches == 1 ? t("filter.result.matches_one") : t("filter.result.matches", this.countedMatches);
			color = OK_COLOR;
		}
		graphics.text(this.font, result, this.left, this.resultY, color);
		if (!this.target.window()) {
			// Tabs bekommen nur Nachrichten, die sonst im System-Tab landen würden
			graphics.text(this.font, t("filter.tab_note"), this.left, this.resultY + 12, LABEL_COLOR);
		} else if (this.target.isNew()) {
			graphics.text(this.font, t("filter.window_note"), this.left, this.resultY + 12, LABEL_COLOR);
		}
	}
}
