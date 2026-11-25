package com.pokato.whatchanged.client;

import com.pokato.whatchanged.core.Config;
import com.pokato.whatchanged.core.Json;
import com.pokato.whatchanged.core.Loaders;
import com.pokato.whatchanged.core.Model.ConfigChange;
import com.pokato.whatchanged.core.Model.KeyChange;
import com.pokato.whatchanged.core.VersionCompare;
import com.pokato.whatchanged.core.Markdown;
import com.pokato.whatchanged.core.Model.ModChange;
import com.pokato.whatchanged.core.Model.Named;
import com.pokato.whatchanged.core.Model.PackReport;
import com.pokato.whatchanged.core.Model.RecipeChange;
import com.pokato.whatchanged.core.Model.WorldReport;
import com.pokato.whatchanged.core.Names;
import com.pokato.whatchanged.core.PackTracker;
import com.pokato.whatchanged.core.WorldTracker;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.pokato.whatchanged.core.Model;

/**
 * Tabbed, searchable list of everything that changed.
 * All logic lives here; the thin per-version subclasses only forward the renderer of their game version to
 * {@link #paint} and the mouse wheel to {@link #scrollList}. Only buttons and an edit box are used - no other
 * input-event overrides, whose signatures changed in 1.20.2 and 1.21.9.
 */
public abstract class AbstractWhatChangedScreen extends Screen {
    private static final int ROW_H = 18;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int DARK_GRAY = 0xFF707070;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int GOLD = 0xFFFFAA00;
    private static final int ORANGE = 0xFFFF8040;
    private static final int AQUA = 0xFF55FFFF;

    public static final int TAB_MODS = 0, TAB_ITEMS = 1, TAB_MOBS = 2, TAB_CONFIGS = 3, TAB_WORLD = 4;
    private static final String[] TAB_KEYS = {"whatchanged.tab.mods", "whatchanged.tab.items", "whatchanged.tab.mobs",
            "whatchanged.tab.configs", "whatchanged.tab.world"};
    private static final String[] SHORT_TAB_KEYS = {"whatchanged.tab.mods", "whatchanged.tab.items", "whatchanged.tab.mobs",
            "whatchanged.tab.configs", "whatchanged.tab.world.short"};
    private static final String[] NOTIFY_KEYS = {"whatchanged.button.notify.all", "whatchanged.button.notify.quiet",
            "whatchanged.button.notify.off", "whatchanged.button.notify.custom"};
    private static int lastTab = 0;

    private final Screen parent;
    private final PackReport pack;
    private final WorldReport world;
    private final boolean worldAvailable;
    private final Map<String, Item> items;
    private final Set<String> newItemIds;

    private final List<Button> tabButtons = new ArrayList<>();
    private EditBox search;
    private Button notifyButton;
    private List<Row> allRows = new ArrayList<>();
    private List<Row> rows = new ArrayList<>();
    private String query = "";
    private int tab;
    private double scroll;
    private String status;
    private long statusUntil;

    // list geometry (computed in init)
    private int listX, listW, listTop, listBottom;

    // [recovered 1.3.0: decompiled from the release jar, original comments lost]
    private static final int BAR_GRAB = 8;

    // [recovered 1.3.0: decompiled from the release jar, original comments lost]
    private EditBox barHit;

    // [recovered 1.3.0: decompiled from the release jar, original comments lost]
    private boolean draggingBar;

    // [recovered 1.3.0: decompiled from the release jar, original comments lost]
    private double barGrabOffset;

    protected AbstractWhatChangedScreen(Screen parent) {
        super(Component.translatable("whatchanged.screen.title"));
        this.parent = parent;
        PackTracker.checkOnce();
        this.pack = PackTracker.report();
        this.worldAvailable = WorldTracker.ready;
        this.world = WorldTracker.current;
        this.items = Names.itemsById();
        this.newItemIds = Markdown.newItemIds(pack);
        this.tab = lastTab;
        if (pack != null && !pack.seen && !pack.isEmpty()) {
            // jump to the most interesting tab
            boolean mods = !pack.modsAdded.isEmpty() || !pack.modsRemoved.isEmpty() || !pack.modsUpdated.isEmpty()
                    || !pack.modsDowngraded.isEmpty() || pack.loaderChanged();
            boolean things = !pack.itemsAdded.isEmpty() || !pack.itemsRemoved.isEmpty()
                    || !pack.entitiesAdded.isEmpty() || !pack.entitiesRemoved.isEmpty();
            this.tab = mods ? TAB_MODS : things ? TAB_ITEMS : !pack.configs.isEmpty() ? TAB_CONFIGS : TAB_MODS;
        }
        PackTracker.markSeen();
        WorldTracker.markSeen();
    }

    /** Used by the dev test harness too. */
    public void selectTab(int index) {
        this.tab = Math.max(0, Math.min(TAB_KEYS.length - 1, index));
        lastTab = this.tab;
        this.scroll = 0;
        this.allRows = buildRows(this.tab);
        applyFilter();
        for (int i = 0; i < tabButtons.size(); i++) tabButtons.get(i).active = i != this.tab;
    }

    public void setQuery(String q) {
        this.query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        this.scroll = 0;
        if (search != null && !search.getValue().equals(q == null ? "" : q)) search.setValue(q == null ? "" : q);
        applyFilter();
    }

    // [recovered 1.3.0: decompiled from the release jar, original comments lost]
    @Override
    protected void init() {
        try {
            ClientCompat.get().clearToasts();
        } catch (Throwable var8) {
        }

        this.listW = Math.min(440, this.width - 30);
        this.listX = (this.width - this.listW) / 2;
        this.listTop = 86;
        this.listBottom = this.height - 34;
        this.tabButtons.clear();
        int gap = 4;
        int tabW = (this.listW - gap * (TAB_KEYS.length - 1)) / TAB_KEYS.length;
        int style = 0;

        for (int i = 0; i < TAB_KEYS.length; i++) {
            style = Math.max(style, this.tabStyle(i, tabW - 8));
        }

        for (int i = 0; i < TAB_KEYS.length; i++) {
            int idx = i;
            Component label = this.tabLabel(i, style);
            Button b = Button.builder(label, btn -> this.selectTab(idx)).bounds(this.listX + i * (tabW + gap), 36, tabW, 20).build();
            this.tabButtons.add(this.addRenderableWidget(b));
        }

        this.search = new EditBox(this.font, this.listX, 61, this.listW, 18, Component.translatable("whatchanged.search"));
        this.search.setMaxLength(64);
        this.search.setHint(Component.translatable("whatchanged.search.hint").withStyle(s -> s.withColor(7368816)));
        this.search.setValue(this.query);
        this.search.setResponder(this::setQuery);
        this.addRenderableWidget(this.search);
        this.barHit = new EditBox(this.font, this.listX + this.listW - 8, this.listTop, 11, this.listBottom - this.listTop, Component.empty());
        this.barHit.setEditable(false);
        this.barHit.setMaxLength(0);
        this.addWidget(this.barHit);
        this.draggingBar = false;
        int bw = Math.min(140, (this.listW - 8) / 3);
        int bx = this.width / 2 - (bw * 3 + 8) / 2;
        this.notifyButton = this.addRenderableWidget(
            Button.builder(notifyLabel(), bxx -> this.cycleNotifications())
                .tooltip(Tooltip.create(Component.translatable("whatchanged.button.notify.tooltip", ClientCore.keyName())))
                .bounds(bx, this.height - 26, bw, 20)
                .build()
        );
        this.addRenderableWidget(
            Button.builder(Component.translatable("whatchanged.button.export"), bxx -> this.exportChangelog())
                .bounds(bx + bw + 4, this.height - 26, bw, 20)
                .build()
        );
        this.addRenderableWidget(
            Button.builder(Component.translatable("gui.done"), bxx -> this.onClose()).bounds(bx + 2 * (bw + 4), this.height - 26, bw, 20).build()
        );
        this.selectTab(this.tab);
    }

    /** 0 = full name + count, 1 = short name + count, 2 = short name. */
    private Component tabLabel(int i, int style) {
        if (style == 0) return Component.translatable(TAB_KEYS[i]).append(countLabel(i));
        if (style == 1) return Component.translatable(SHORT_TAB_KEYS[i]).append(countLabel(i));
        return Component.translatable(SHORT_TAB_KEYS[i]);
    }

    private int tabStyle(int i, int maxW) {
        for (int style = 0; style < 2; style++) if (this.font.width(tabLabel(i, style)) <= maxW) return style;
        return 2;
    }

    private static Component notifyLabel() {
        return Component.translatable(NOTIFY_KEYS[Config.get().notificationMode()]);
    }

    /** All -> Quiet -> Off -> All (a hand-edited combination counts as Custom and goes to All). */
    public void cycleNotifications() {
        int mode = Config.get().notificationMode();
        int next = mode == Config.NOTIFY_ALL ? Config.NOTIFY_QUIET : mode == Config.NOTIFY_QUIET ? Config.NOTIFY_OFF : Config.NOTIFY_ALL;
        Config.get().setNotificationMode(next);
        if (notifyButton != null) notifyButton.setMessage(notifyLabel());
    }

    private String countLabel(int tab) {
        int n = switch (tab) {
            case TAB_MODS -> pack == null ? 0 : pack.modsAdded.size() + pack.modsRemoved.size() + pack.modsUpdated.size()
                    + pack.modsDowngraded.size() + (pack.loaderChanged() ? 1 : 0);
            case TAB_ITEMS -> pack == null ? 0 : pack.itemsAdded.size() + pack.itemsRemoved.size();
            case TAB_MOBS -> pack == null ? 0 : pack.entitiesAdded.size() + pack.entitiesRemoved.size();
            case TAB_CONFIGS -> pack == null ? 0 : pack.configs.size();
            default -> world == null ? 0 : world.recipesAdded.size() + world.recipesChanged.size() + world.recipesRemoved.size()
                    + world.biomesAdded.size() + world.biomesRemoved.size()
                    + world.structuresAdded.size() + world.structuresRemoved.size()
                    + world.enchantmentsAdded.size() + world.enchantmentsRemoved.size()
                    + world.dimensionsAdded.size() + world.dimensionsRemoved.size();
        };
        return n > 0 ? " (" + n + ")" : "";
    }

    // ---------------------------------------------------------------- rows

    /** child: a line that belongs to the line above it (a changed value under its config file). */
    private record Row(int kind, String sign, int signColor, String text, int textColor, String sub, ItemStack icon, String tooltip,
                       boolean child) {
        static final int HEADER = 0, LINE = 1, NOTE = 2, SPACER = 3, SUBHEADER = 4;

        static Row header(String text, int color) {
            return new Row(HEADER, null, 0, text, color, null, null, null, false);
        }

        static Row subheader(String text) {
            return new Row(SUBHEADER, null, 0, text, GRAY, null, null, null, false);
        }

        static Row note(String text, int color) {
            return new Row(NOTE, null, 0, text, color, null, null, null, false);
        }

        static Row spacer() {
            return new Row(SPACER, null, 0, "", 0, null, null, null, false);
        }

        static Row line(String sign, int signColor, String text, int textColor, String sub, ItemStack icon, String tooltip) {
            return new Row(LINE, sign, signColor, clean(text), textColor, clean(sub), icon, clean(tooltip), false);
        }

        static Row childLine(String text, int textColor, String tooltip) {
            return new Row(LINE, "", 0, literal(text), textColor, null, null, literal(tooltip), true);
        }

        /** Mod and item names may carry § formatting codes ("§6Fancy Mod") - drop them so they can't restyle the row. */
        static String clean(String s) {
            return s == null || s.indexOf('§') < 0 ? s : s.replaceAll("§.?", "");
        }

        /** Config values: keep the code visible as text ("&6[Server]"), so a colour change still shows. */
        static String literal(String s) {
            return s == null || s.indexOf('§') < 0 ? s : s.replace('§', '&');
        }

        boolean matches(String q) {
            return (text != null && text.toLowerCase(Locale.ROOT).contains(q))
                    || (sub != null && sub.toLowerCase(Locale.ROOT).contains(q))
                    || (tooltip != null && tooltip.toLowerCase(Locale.ROOT).contains(q));
        }
    }

    /** Keeps headers only when something under them matches. */
    private void applyFilter() {
        if (query.isEmpty()) {
            rows = allRows;
            return;
        }
        List<Row> out = new ArrayList<>();
        Row pendingHeader = null, pendingSub = null, pendingParent = null;
        boolean parentShown = false; // a matching file line shows all of its values
        for (Row r : allRows) {
            switch (r.kind()) {
                case Row.HEADER -> { pendingHeader = r; pendingSub = null; pendingParent = null; parentShown = false; }
                case Row.SUBHEADER -> { pendingSub = r; pendingParent = null; parentShown = false; }
                case Row.LINE -> {
                    if (!r.child()) {
                        pendingParent = r;
                        parentShown = r.matches(query);
                        if (!parentShown) continue;
                    } else if (!parentShown && !r.matches(query)) {
                        continue;
                    }
                    if (pendingHeader != null) { out.add(pendingHeader); pendingHeader = null; }
                    if (pendingSub != null) { out.add(pendingSub); pendingSub = null; }
                    if (r.child() && pendingParent != null) { out.add(pendingParent); pendingParent = null; }
                    if (!r.child()) pendingParent = null;
                    out.add(r);
                }
                default -> { }
            }
        }
        if (out.isEmpty()) out.add(Row.note(t("whatchanged.search.none"), GRAY));
        rows = out;
    }

    private static String t(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private List<Row> buildRows(int tab) {
        List<Row> out = new ArrayList<>();
        if (tab == TAB_WORLD) {
            buildWorldRows(out);
            return out;
        }
        if (pack == null || pack.isEmpty()) {
            out.add(Row.note(t("whatchanged.empty.pack.1"), WHITE));
            out.add(Row.note(t("whatchanged.empty.pack.2", WorldTracker.date(PackTracker.baselineTime())), GRAY));
            out.add(Row.spacer());
            out.add(Row.note(t("whatchanged.empty.pack.3"), GRAY));
            out.add(Row.note(t("whatchanged.empty.pack.4"), GRAY));
            out.add(Row.spacer());
            out.add(Row.note(t("whatchanged.empty.pack.5"), DARK_GRAY));
            return out;
        }
        switch (tab) {
            case TAB_MODS -> {
                boolean top = false;
                if (pack.mcOld != null && !pack.mcOld.equals(pack.mcNew)) {
                    boolean down = VersionCompare.isDowngrade(pack.mcOld, pack.mcNew);
                    String url = Loaders.minecraftUrl(pack.mcNew);
                    out.add(Row.line(down ? "↓" : "↑", down ? ORANGE : YELLOW, "Minecraft " + pack.mcOld + " → " + pack.mcNew, WHITE,
                            t(down ? "whatchanged.label.downgraded" : "whatchanged.label.updated"), Names.stack(Items.GRASS_BLOCK),
                            "Minecraft " + pack.mcOld + " → " + pack.mcNew + (url != null ? "\n" + url : "")));
                    top = true;
                }
                if (pack.loaderChanged()) {
                    boolean down = pack.loaderDowngraded();
                    String text = Loaders.describe(pack);
                    String url = Loaders.changelogUrl(pack.loaderName, pack.loaderNew, pack.mcNew);
                    String label = pack.loaderSwitched() ? "whatchanged.label.switched"
                            : down ? "whatchanged.label.downgraded" : "whatchanged.label.updated";
                    out.add(Row.line(down ? "↓" : pack.loaderSwitched() ? "⇄" : "↑", down ? ORANGE : YELLOW, text, WHITE,
                            t(label), Names.stack(Items.ANVIL),
                            text + (url != null ? "\n" + t("whatchanged.tooltip.loader") + "\n" + url : "")));
                    top = true;
                }
                if (top) out.add(Row.spacer());
                modSection(out, t("whatchanged.section.mods_added"), pack.modsAdded, "+", GREEN, false);
                modSection(out, t("whatchanged.section.mods_updated"), pack.modsUpdated, "↑", YELLOW, true);
                modSection(out, t("whatchanged.section.mods_downgraded"), pack.modsDowngraded, "↓", ORANGE, true);
                modSection(out, t("whatchanged.section.mods_removed"), pack.modsRemoved, "-", RED, false);
                if (out.isEmpty()) out.add(Row.note(t("whatchanged.empty.mods"), GRAY));
            }
            case TAB_CONFIGS -> buildConfigRows(out);
            case TAB_ITEMS -> {
                namedSection(out, t("whatchanged.section.items_added"), pack.itemsAdded, "+", GREEN, true);
                if (!pack.itemsRemoved.isEmpty()) out.add(Row.note(t("whatchanged.note.removed_items"), GOLD));
                namedSection(out, t("whatchanged.section.items_removed"), pack.itemsRemoved, "-", RED, true);
                if (out.isEmpty()) out.add(Row.note(t("whatchanged.empty.items"), GRAY));
            }
            case TAB_MOBS -> {
                namedSection(out, t("whatchanged.section.mobs_added"), pack.entitiesAdded, "+", GREEN, false);
                namedSection(out, t("whatchanged.section.mobs_removed"), pack.entitiesRemoved, "-", RED, false);
                if (out.isEmpty()) out.add(Row.note(t("whatchanged.empty.mobs"), GRAY));
            }
            default -> { }
        }
        return out;
    }

    private void buildConfigRows(List<Row> out) {
        if (!Config.get().trackConfigs) {
            out.add(Row.note(t("whatchanged.empty.configs.off"), GRAY));
            return;
        }
        if (pack.configs.isEmpty()) {
            out.add(Row.note(t("whatchanged.empty.configs"), GRAY));
            return;
        }
        int[] kinds = {ConfigChange.CHANGED, ConfigChange.ADDED, ConfigChange.REMOVED};
        String[] titles = {"whatchanged.section.configs_changed", "whatchanged.section.configs_added", "whatchanged.section.configs_removed"};
        String[] signs = {"~", "+", "-"};
        int[] colors = {YELLOW, GREEN, RED};
        for (int i = 0; i < 3; i++) {
            List<ConfigChange> list = new ArrayList<>();
            for (ConfigChange c : pack.configs) if (c.kind == kinds[i]) list.add(c);
            if (list.isEmpty()) continue;
            out.add(Row.header(t(titles[i]) + " (" + list.size() + ")", colors[i]));
            Map<String, Integer> perMod = new LinkedHashMap<>();
            for (ConfigChange c : list) perMod.merge(nz(c.mod), 1, Integer::sum);
            String mod = null;
            for (ConfigChange c : list) {
                if (!nz(c.mod).equals(mod)) {
                    mod = nz(c.mod);
                    out.add(Row.subheader(Row.clean(mod) + " (" + perMod.get(mod) + ")"));
                }
                String sub = c.kind == ConfigChange.CHANGED
                        ? (c.keys.isEmpty() ? t("whatchanged.label.content_changed") : t(c.keys.size() + c.moreKeys == 1 ? "whatchanged.label.values.one" : "whatchanged.label.values", c.keys.size() + c.moreKeys))
                        : null;
                out.add(Row.line(signs[i], colors[i], c.path, WHITE, sub, null, c.path + "\n" + mod));
                for (KeyChange k : c.keys) {
                    String text = valueLine(Row.literal(k.key), Row.literal(k.oldValue), Row.literal(k.newValue));
                    out.add(Row.childLine(text, k.oldValue == null ? GREEN : k.newValue == null ? RED : WHITE,
                            k.key + "\n" + t("whatchanged.tooltip.old") + " " + valueText(k.oldValue) + "\n"
                                    + t("whatchanged.tooltip.new") + " " + valueText(k.newValue) + "\n" + c.path));
                }
                if (c.moreKeys > 0) out.add(Row.note("   " + t("whatchanged.label.more_values", c.moreKeys), DARK_GRAY));
            }
            out.add(Row.spacer());
        }
        out.add(Row.note(t("whatchanged.note.configs"), DARK_GRAY));
    }

    /** Text cut to maxW pixels, with "…" when something was cut. */
    private String fit(String s, int maxW) {
        if (s == null) return "";
        if (this.font.width(s) <= maxW) return s;
        String dots = "…";
        if (maxW <= this.font.width(dots)) return dots;
        return this.font.plainSubstrByWidth(s, maxW - this.font.width(dots)) + dots;
    }

    /** "   key: old → new" fitted to the row: long keys and values are shortened so both values stay visible. */
    private String valueLine(String key, String oldValue, String newValue) {
        String o = valueText(oldValue), n = valueText(newValue);
        String full = "   " + key + ": " + o + " → " + n;
        int avail = listW - 32;
        if (this.font.width(full) <= avail) return full;
        int budget = avail - this.font.width("   : → ") - 4;
        int kw = this.font.width(key), ow = this.font.width(o), nw = this.font.width(n);
        String k = fit(key, Math.min(kw, Math.max(budget * 2 / 5, budget - ow - nw)));
        int rest = budget - this.font.width(k);
        String n2 = fit(n, Math.min(nw, Math.max(rest / 2, rest - ow)));
        String o2 = fit(o, rest - this.font.width(n2));
        return "   " + k + ": " + o2 + " → " + n2;
    }

    private static String valueText(String v) {
        return v == null ? "-" : v.isEmpty() ? "\"\"" : v;
    }

    private void buildWorldRows(List<Row> out) {
        if (!worldAvailable) {
            if (this.minecraft != null && this.minecraft.level != null && !this.minecraft.hasSingleplayerServer()) {
                out.add(Row.note(t("whatchanged.empty.server.1"), WHITE));
                out.add(Row.note(t("whatchanged.empty.server.2"), GRAY));
                out.add(Row.note(t("whatchanged.empty.server.3"), GRAY));
            } else {
                out.add(Row.note(t("whatchanged.empty.noworld.1"), WHITE));
                out.add(Row.note(t("whatchanged.empty.noworld.2"), GRAY));
                out.add(Row.note(t("whatchanged.empty.noworld.3"), GRAY));
            }
            return;
        }
        if (world == null || world.isEmpty()) {
            out.add(Row.note(t("whatchanged.empty.world.1"), WHITE));
            out.add(Row.note(t("whatchanged.empty.world.2"), GRAY));
            return;
        }
        out.add(Row.note(t("whatchanged.world.since", world.world, WorldTracker.date(world.previous)), GRAY));
        int crafted = world.craftedCount();
        if (crafted > 0) out.add(Row.note(t("whatchanged.world.crafted_legend", crafted), YELLOW));
        if (world.recipesUncomparable) out.add(Row.note(t("whatchanged.note.uncomparable", world.mcOld, world.mcNew), GOLD));

        List<RecipeChange> added = world.recipesAdded;
        int hidden = 0;
        if (Config.get().hideRecipesForNewItems && !newItemIds.isEmpty()) {
            added = new ArrayList<>();
            for (RecipeChange c : world.recipesAdded) {
                if (c.out != null && newItemIds.contains(c.out)) hidden++;
                else added.add(c);
            }
        }
        recipeSection(out, t("whatchanged.section.recipes_changed"), world.recipesChanged, "~", YELLOW);
        recipeSection(out, t("whatchanged.section.recipes_added"), added, "+", GREEN);
        if (hidden > 0) {
            out.add(Row.note(t("whatchanged.note.hidden_recipes", hidden), DARK_GRAY));
            out.add(Row.spacer());
        }
        recipeSection(out, t("whatchanged.section.recipes_removed"), world.recipesRemoved, "-", RED);
        idSection(out, t("whatchanged.section.biomes_added"), world.biomesAdded, "+", GREEN, "biome", Items.OAK_SAPLING);
        idSection(out, t("whatchanged.section.biomes_removed"), world.biomesRemoved, "-", RED, "biome", Items.DEAD_BUSH);
        idSection(out, t("whatchanged.section.structures_added"), world.structuresAdded, "+", GREEN, null, Items.CHEST);
        idSection(out, t("whatchanged.section.structures_removed"), world.structuresRemoved, "-", RED, null, Items.CHEST);
        idSection(out, t("whatchanged.section.enchantments_added"), world.enchantmentsAdded, "+", GREEN, "enchantment", Items.ENCHANTED_BOOK);
        idSection(out, t("whatchanged.section.enchantments_removed"), world.enchantmentsRemoved, "-", RED, "enchantment", Items.BOOK);
        idSection(out, t("whatchanged.section.dimensions_added"), world.dimensionsAdded, "+", GREEN, null, Items.END_PORTAL_FRAME);
        idSection(out, t("whatchanged.section.dimensions_removed"), world.dimensionsRemoved, "-", RED, null, Items.END_PORTAL_FRAME);
        if (!world.biomesAdded.isEmpty() || !world.structuresAdded.isEmpty()) {
            out.add(Row.spacer());
            out.add(Row.note(t("whatchanged.note.unexplored"), GOLD));
        }
    }

    private void modSection(List<Row> out, String title, List<ModChange> list, String sign, int color, boolean versions) {
        if (list.isEmpty()) return;
        out.add(Row.header(title + " (" + list.size() + ")", color));
        for (ModChange m : list) {
            String v = versions ? nz(m.oldVersion) + " → " + nz(m.newVersion) : nz(m.newVersion != null ? m.newVersion : m.oldVersion);
            String tip = m.name + "\n" + m.id + (m.url != null ? "\n" + m.url : "");
            out.add(Row.line(sign, color, m.name, WHITE, v, null, tip));
        }
        out.add(Row.spacer());
    }

    private void namedSection(List<Row> out, String title, List<Named> list, String sign, int color, boolean itemIcons) {
        if (list.isEmpty()) return;
        out.add(Row.header(title + " (" + list.size() + ")", color));
        Map<String, Integer> perMod = new LinkedHashMap<>();
        for (Named n : list) perMod.merge(n.mod, 1, Integer::sum);
        String mod = null;
        for (Named n : list) {
            if (!n.mod.equals(mod)) {
                mod = n.mod;
                out.add(Row.subheader(Row.clean(mod) + " (" + perMod.get(mod) + ")"));
            }
            ItemStack icon = null;
            if (itemIcons) {
                Item item = items.get(n.id);
                icon = Names.stack(item != null ? item : Items.BARRIER);
            }
            out.add(Row.line(sign, color, n.name, WHITE, n.id, icon, n.name + "\n" + n.id + "\n" + n.mod));
        }
        out.add(Row.spacer());
    }

    private void recipeSection(List<Row> out, String title, List<RecipeChange> list, String sign, int color) {
        if (list.isEmpty()) return;
        out.add(Row.header(title + " (" + list.size() + ")", color));
        Map<String, Integer> perMod = new LinkedHashMap<>();
        for (RecipeChange c : list) perMod.merge(nz(c.mod), 1, Integer::sum);
        String mod = null;
        for (RecipeChange c : list) {
            if (!nz(c.mod).equals(mod)) {
                mod = nz(c.mod);
                out.add(Row.subheader(Row.clean(mod) + " (" + perMod.get(mod) + ")"));
            }
            Item item = c.out == null ? null : items.get(c.out);
            ItemStack icon = Names.stack(item != null ? item : Items.PAPER);
            String name = c.crafted ? "★ " + c.outName : c.outName;
            String tip = c.outName + "\n" + c.id + (c.crafted ? "\n" + t("whatchanged.tooltip.crafted") : "");
            out.add(Row.line(sign, color, name, c.crafted ? YELLOW : WHITE, c.id, icon, tip));
        }
        out.add(Row.spacer());
    }

    private void idSection(List<Row> out, String title, List<String> list, String sign, int color, String lang, Item iconItem) {
        if (list.isEmpty()) return;
        out.add(Row.header(title + " (" + list.size() + ")", color));
        for (String id : list) {
            String name = Names.translatedOrPretty(lang, id);
            out.add(Row.line(sign, color, name, WHITE, id, Names.stack(iconItem), name + "\n" + id));
        }
        out.add(Row.spacer());
    }

    // ---------------------------------------------------------------- rendering

    /** Called by the version-specific subclass after the widgets were drawn. */
    // [recovered 1.3.0: decompiled from the release jar, original comments lost]
    protected void paint(Painter p, int mouseX, int mouseY) {
        p.centered(this.title, this.width / 2, 8, -22016);
        p.centered(Component.literal(this.fit(this.headerLine(), this.width - 12)), this.width / 2, 22, -6250336);
        p.fill(this.listX - 2, this.listTop - 2, this.listX + this.listW + 2, this.listBottom + 2, -1442840576);
        int contentH = this.rows.size() * 18;
        this.clampScroll(contentH);
        boolean overBar = this.dragScrollbar(mouseX, mouseY, contentH);
        p.scissor(this.listX, this.listTop, this.listX + this.listW, this.listBottom);
        int first = (int)(this.scroll / 18.0);
        int y = this.listTop - (int)(this.scroll % 18.0);
        AbstractWhatChangedScreen.Row hovered = null;
        int hoverX = !overBar && !this.draggingBar ? mouseX : -1073741824;

        for (int i = first; i < this.rows.size() && y < this.listBottom; y += 18) {
            AbstractWhatChangedScreen.Row row = this.rows.get(i);
            if (this.drawRow(p, row, y, hoverX, mouseY)) {
                hovered = row;
            }

            i++;
        }

        p.endScissor();
        int viewH = this.listBottom - this.listTop;
        if (contentH > viewH) {
            int barH = this.barHeight(contentH);
            int barY = this.listTop + (int)((viewH - barH) * (this.scroll / (contentH - viewH)));
            boolean hot = this.draggingBar || overBar && mouseY >= barY && mouseY < barY + barH;
            p.fill(this.listX + this.listW - 3, this.listTop, this.listX + this.listW, this.listBottom, 1157627903);
            p.fill(this.listX + this.listW - (hot ? 4 : 3), barY, this.listX + this.listW, barY + barH, hot ? -1 : -4144960);
        }

        if (this.status != null && System.currentTimeMillis() < this.statusUntil) {
            p.centered(Component.literal(this.status), this.width / 2, this.listBottom - 12, -11141291);
        }

        if (hovered != null && hovered.tooltip() != null) {
            try {
                List<Component> lines = new ArrayList<>();
                boolean firstLine = true;
                int max = Math.max(120, Math.min(190, this.width - 24));

                for (String line : hovered.tooltip().split("\n")) {
                    String rest = line;

                    while (this.font.width(rest) > max) {
                        String head = this.font.plainSubstrByWidth(rest, max);
                        if (head.isEmpty()) {
                            break;
                        }

                        int cut = -1;

                        for (int i = head.length() - 1; i > head.length() / 2; i--) {
                            if (" /-_.,".indexOf(head.charAt(i)) >= 0) {
                                cut = i + 1;
                                break;
                            }
                        }

                        if (cut > 0) {
                            head = head.substring(0, cut);
                        }

                        lines.add(
                            firstLine ? Component.literal(head.stripTrailing()) : Component.literal(head.stripTrailing()).withStyle(s -> s.withColor(10526880))
                        );
                        rest = rest.substring(head.length());
                    }

                    lines.add(firstLine ? Component.literal(rest) : Component.literal(rest).withStyle(s -> s.withColor(10526880)));
                    firstLine = false;
                }

                p.tooltip(lines, mouseX, mouseY);
            } catch (Throwable var22) {
            }
        }
    }

    private String headerLine() {
        if (tab == TAB_WORLD) {
            if (world != null && !world.isEmpty()) return t("whatchanged.header.detected", WorldTracker.date(world.detected));
            return t("whatchanged.header.world");
        }
        if (pack == null || pack.isEmpty()) return t("whatchanged.header.watching", WorldTracker.date(PackTracker.baselineTime()));
        String full = t("whatchanged.header.since", WorldTracker.date(pack.previous), WorldTracker.date(pack.detected));
        if (this.font.width(full) <= this.width - 12) return full;
        return t("whatchanged.header.since.short", WorldTracker.date(pack.previous)); // narrow window
    }

    // [recovered 1.3.0: decompiled from the release jar, original comments lost]
    private void icon(Painter p, ItemStack stack, int x, int y) {
        Item item = stack.getItem();
        if (!Icons.canDraw(item, p.drawsItemsLater()) || !p.item(stack, x, y)) {
            p.fill(x + 2, y + 2, x + 14, y + 14, 872415231);
            p.text("?", x + 6, y + 4, -6250336);
        }
    }

    /** Returns true when the mouse is over this row. */
    // [recovered 1.3.0: decompiled from the release jar, original comments lost]
    private boolean drawRow(Painter p, AbstractWhatChangedScreen.Row row, int y, int mouseX, int mouseY) {
        int x = this.listX + 4;
        int textY = y + 5;
        int right = this.listX + this.listW - 8;
        switch (row.kind()) {
            case 0:
                p.fill(this.listX, y + 1, this.listX + this.listW, y + 18 - 1, 872415231);
                p.text(this.fit(row.text(), this.listW - 14), x, textY, row.textColor());
                break;
            case 1:
                boolean hover = mouseX >= this.listX
                    && mouseX < this.listX + this.listW
                    && mouseY >= y
                    && mouseY < y + 18
                    && mouseY >= this.listTop
                    && mouseY < this.listBottom;
                if (hover) {
                    p.fill(this.listX, y, this.listX + this.listW, y + 18, 587202559);
                }

                p.text(row.sign(), x, textY, row.signColor());
                int cx = x + 10;
                if (row.icon() != null) {
                    this.icon(p, row.icon(), cx, y + 1);
                    cx += 20;
                }

                int subW = 0;
                String sub = row.sub();
                if (sub != null) {
                    int maxSub = Math.max(40, Math.max((right - cx) / 2, right - cx - 8 - this.font.width(row.text())));
                    sub = this.fit(sub, maxSub);
                    subW = this.font.width(sub);
                    p.text(sub, right - subW, textY, -9408400);
                }

                String main = this.fit(row.text(), Math.max(20, right - cx - subW - 8));
                p.text(main, cx, textY, row.textColor());
                return hover;
            case 2:
                p.text(this.fit(row.text(), this.listW - 14), x, textY, row.textColor());
            case 3:
            default:
                break;
            case 4:
                p.text(this.fit(row.text(), this.listW - 26), x + 12, textY, row.textColor());
        }

        return false;
    }

    // [recovered 1.3.0: decompiled from the release jar, original comments lost]
    private int barHeight(int contentH) {
        int viewH = this.listBottom - this.listTop;
        return Math.max(16, viewH * viewH / Math.max(1, contentH));
    }

    // [recovered 1.3.0: decompiled from the release jar, original comments lost]
    private boolean dragScrollbar(int mouseX, int mouseY, int contentH) {
        int viewH = this.listBottom - this.listTop;
        boolean scrollable = contentH > viewH;
        boolean over = scrollable
            && mouseX >= this.listX + this.listW - 8
            && mouseX < this.listX + this.listW + 3
            && mouseY >= this.listTop
            && mouseY < this.listBottom;
        boolean held = this.barHit != null && this.getFocused() == this.barHit && this.isDragging();
        if (held && !this.draggingBar && scrollable) {
            int barH = this.barHeight(contentH);
            int barY = this.listTop + (int)((viewH - barH) * (this.scroll / (contentH - viewH)));
            this.draggingBar = true;
            this.barGrabOffset = mouseY >= barY && mouseY < barY + barH ? mouseY - barY : barH / 2.0;
        }

        if (!held) {
            this.draggingBar = false;
            if (this.barHit != null && this.getFocused() == this.barHit) {
                this.setFocused(null);
            }
        }

        if (this.draggingBar) {
            int barH = this.barHeight(contentH);
            double track = Math.max(1, viewH - barH);
            this.scroll = (mouseY - this.barGrabOffset - this.listTop) / track * (contentH - viewH);
            this.clampScroll(contentH);
        }

        return over;
    }

    private void clampScroll(int contentH) {
        int max = Math.max(0, contentH - (listBottom - listTop));
        if (scroll > max) scroll = max;
        if (scroll < 0) scroll = 0;
    }

    /** Mouse wheel; the subclasses forward their version's mouseScrolled (3 args before 1.20.2, 4 after). */
    protected boolean scrollList(double verticalAmount) {
        scroll -= verticalAmount * ROW_H * 3;
        clampScroll(rows.size() * ROW_H);
        return true;
    }

    // ---------------------------------------------------------------- export

    /** Saves a Markdown changelog and copies it to the clipboard. Public for the test harness. */
    public Path exportChangelog() {
        String md = Markdown.full(pack, world, Config.get().hideRecipesForNewItems);
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new Date());
        Path out = Config.dir().resolve("changelogs").resolve("changelog-" + stamp + ".md");
        Json.writeText(out, md);
        try {
            if (this.minecraft != null) this.minecraft.keyboardHandler.setClipboard(md);
        } catch (Throwable ignored) {
        }
        status = t("whatchanged.status.exported");
        statusUntil = System.currentTimeMillis() + 5000;
        return out;
    }

    @Override
    public void onClose() {
        ClientCompat.get().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
