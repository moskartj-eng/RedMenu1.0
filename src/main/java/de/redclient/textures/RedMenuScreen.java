package de.redclient.textures;

import de.redclient.textures.PackIndex.Entry;
import de.redclient.textures.PackIndex.Preview;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RedMenuScreen extends Screen {
    private static final String[] CATS = PackIndex.CATEGORIES;
    private static final int HEADER = 44, LEFT = 120, RIGHT = 176;
    private static final int CELL = 72, LABEL = 12, GAP = 8, ROW_H = CELL + LABEL + GAP;
    private static final int PV = 128, CAT_H = 20, SEL_H = 18;
    private static final int[] SWATCHES = {
            0xE02020, 0xFF6A00, 0xF2C200, 0x8BC34A, 0x2ECC40, 0x00B7D4,
            0x3B6CFF, 0x9B4DFF, 0xFF4FA3, 0xFFFFFF, 0x9AA0A6, 0x00E5A0
    };
    private static final String[] STYLES = {"Schwarz", "Verlauf", "Raster", "Punkte", "Streifen"};

    /** Weiches Scrollen: pos laeuft dem Ziel (target) hinterher. */
    private static final class Scroller {
        float pos, target, vel, max;

        void clampMax(float m) {
            max = Math.max(0, m);
            target = Math.max(0, Math.min(max, target));
            pos = Math.max(0, Math.min(max, pos));
        }

        void by(float d) {
            target = Math.max(0, Math.min(max, target + d));
        }

        void update(float dt, boolean smooth, float speed) {
            if (!smooth) {
                vel = dt > 0 ? (target - pos) / dt : 0;
                pos = target;
                return;
            }
            float k = 1f - (float) Math.exp(-dt * speed);
            float d = (target - pos) * k;
            pos += d;
            vel = dt > 0 ? d / dt : 0;
            if (Math.abs(target - pos) < 0.05f) {
                pos = target;
                vel = 0;
            }
        }
    }

    private boolean hud;
    private String query = "";
    private String category = CATS[0];
    private final List<Entry> filtered = new ArrayList<>();
    private final List<Entry> selectedEntries = new ArrayList<>();
    private final int[] counts = new int[CATS.length];
    private Entry focus, hovered;
    private boolean dirty, pendingReload;
    private String status = "";

    private final Scroller gridS = new Scroller(), catS = new Scroller(), selS = new Scroller();
    private long lastNs = System.nanoTime(), lastTickNs = 0;
    private int lastStep = 0;

    private final List<ButtonWidget> texFixed = new ArrayList<>();
    private final List<ButtonWidget> hudWidgets = new ArrayList<>();
    private final List<ButtonWidget> catBtns = new ArrayList<>();
    private final List<ButtonWidget> cellBtns = new ArrayList<>();
    private final List<ButtonWidget> selBtns = new ArrayList<>();
    private final List<ButtonWidget> swatchBtns = new ArrayList<>();
    private final List<ButtonWidget> styleBtns = new ArrayList<>();
    private int[] slotIdx = new int[0];
    private int[] selIdx = new int[0];
    private ButtonWidget texTab, hudTabBtn, openBtn, reloadBtn, applyBtn, doneBtn;
    private ButtonWidget opMinus, opPlus, spMinus, spPlus, blMinus, blPlus, smoothBtn, blurBtn, soundBtn;
    private TextFieldWidget search, hex;

    private int px, py, pw, ph, lx, ly, lw, lh, gx, gy, gw, gh, rx, sx, sy, sw, sh, pvY, pvBottom, cols;
    private int hax, hay, hbx;

    public RedMenuScreen() {
        super(Text.literal("Red Menu"));
        PackIndex.reload();
        rebuildSelected();
    }

    // ---------------------------------------------------------------- Aufbau

    private ButtonWidget add(int x, int y, int w, int h, Runnable r) {
        return addDrawableChild(ButtonWidget.builder(Text.empty(), b -> r.run()).dimensions(x, y, w, h).build());
    }

    @Override
    protected void init() {
        texFixed.clear();
        hudWidgets.clear();
        catBtns.clear();
        cellBtns.clear();
        selBtns.clear();
        swatchBtns.clear();
        styleBtns.clear();

        px = 14;
        py = 14;
        pw = width - 28;
        ph = height - 28;
        int top = py + HEADER;
        lx = px + 8;
        ly = top + 10;
        lw = LEFT - 16;
        lh = ph - HEADER - 10 - 34;
        gx = px + LEFT + 8;
        gy = top + 10;
        gw = pw - LEFT - RIGHT - 16;
        gh = ph - HEADER - 10 - 34;
        rx = px + pw - RIGHT;
        sx = rx + 8;
        sy = top + 24;
        sw = RIGHT - 16;
        int applyY = py + ph - 52, doneY = py + ph - 26;
        pvBottom = applyY - 16;
        pvY = pvBottom - 26 - PV;
        sh = Math.max(36, pvY - 8 - sy);
        cols = Math.max(1, (gw + GAP) / (CELL + GAP));

        // ---- Kopfzeile: Tabs oben rechts
        int tabW = 84;
        texTab = add(px + pw - 8 - tabW * 2 - 6, py + 12, tabW, 20, () -> {
            hud = false;
            sound(1.2f);
        });
        hudTabBtn = add(px + pw - 8 - tabW, py + 12, tabW, 20, () -> {
            hud = true;
            sound(1.2f);
        });

        search = new TextFieldWidget(textRenderer, gx, py + 12, Math.max(60, texTab.getX() - 12 - gx), 20,
                Text.literal("Suche"));
        search.setText(query);
        search.setChangedListener(s -> {
            query = s;
            gridS.target = 0;
            refresh();
        });
        addDrawableChild(search);

        // ---- Texturen-Tab: feste Buttons
        openBtn = add(px + 8, py + ph - 26, LEFT - 16, 18, () ->
                Util.getOperatingSystem().open(PackIndex.packDir().toFile()));
        reloadBtn = add(gx, py + ph - 26, 90, 18, () -> {
            PackIndex.reload();
            rebuildSelected();
            refresh();
            sound(1.1f);
        });
        applyBtn = add(rx + 8, applyY, RIGHT - 16, 20, this::applySelection);
        doneBtn = add(rx + 8, doneY, RIGHT - 16, 20, this::done);
        texFixed.add(openBtn);
        texFixed.add(reloadBtn);
        texFixed.add(applyBtn);

        // ---- HUD-Tab
        hax = px + 28;
        hay = top + 22;
        hbx = hax + 300;
        for (int i = 0; i < SWATCHES.length; i++) {
            final int col = SWATCHES[i];
            ButtonWidget b = add(hax + (i % 6) * 40, hay + 14 + (i / 6) * 24, 36, 20, () -> {
                RedConfig.accent = col;
                RedConfig.save();
                hex.setText(String.format("#%06X", col));
                sound(1.3f);
            });
            swatchBtns.add(b);
            hudWidgets.add(b);
        }
        hex = new TextFieldWidget(textRenderer, hax, hay + 68, 92, 18, Text.literal("Hex"));
        hex.setMaxLength(7);
        hex.setText(String.format("#%06X", RedConfig.accent));
        hex.setChangedListener(s -> {
            String t = s.startsWith("#") ? s.substring(1) : s;
            if (t.length() == 6) {
                try {
                    RedConfig.accent = Integer.parseInt(t, 16);
                    RedConfig.save();
                } catch (NumberFormatException ignored) {
                }
            }
        });
        addDrawableChild(hex);
        for (int i = 0; i < STYLES.length; i++) {
            final int st = i;
            ButtonWidget b = add(hax + (i % 3) * 92, hay + 114 + (i / 3) * 24, 88, 20, () -> {
                RedConfig.bgStyle = st;
                RedConfig.save();
                sound(1.2f);
            });
            styleBtns.add(b);
            hudWidgets.add(b);
        }
        opMinus = add(hax, hay + 184, 24, 18, () -> {
            RedConfig.opacity = Math.max(40, RedConfig.opacity - 10);
            RedConfig.save();
            sound(0.9f);
        });
        opPlus = add(hax + 74, hay + 184, 24, 18, () -> {
            RedConfig.opacity = Math.min(100, RedConfig.opacity + 10);
            RedConfig.save();
            sound(1.3f);
        });
        smoothBtn = add(hbx, hay + 14, 190, 20, () -> {
            RedConfig.smooth = !RedConfig.smooth;
            RedConfig.save();
            sound(RedConfig.smooth ? 1.4f : 0.8f);
        });
        blurBtn = add(hbx, hay + 38, 190, 20, () -> {
            RedConfig.blur = !RedConfig.blur;
            RedConfig.save();
            sound(RedConfig.blur ? 1.4f : 0.8f);
        });
        soundBtn = add(hbx, hay + 62, 190, 20, () -> {
            RedConfig.sounds = !RedConfig.sounds;
            RedConfig.save();
            sound(RedConfig.sounds ? 1.4f : 0.8f);
        });
        spMinus = add(hbx, hay + 114, 24, 18, () -> {
            RedConfig.scrollSpeed = Math.max(1, RedConfig.scrollSpeed - 1);
            RedConfig.save();
            sound(0.9f);
        });
        spPlus = add(hbx + 74, hay + 114, 24, 18, () -> {
            RedConfig.scrollSpeed = Math.min(5, RedConfig.scrollSpeed + 1);
            RedConfig.save();
            sound(1.3f);
        });
        blMinus = add(hbx, hay + 156, 24, 18, () -> {
            RedConfig.blurStrength = Math.max(1, RedConfig.blurStrength - 1);
            RedConfig.save();
            sound(0.9f);
        });
        blPlus = add(hbx + 74, hay + 156, 24, 18, () -> {
            RedConfig.blurStrength = Math.min(5, RedConfig.blurStrength + 1);
            RedConfig.save();
            sound(1.3f);
        });
        Collections_addAll(hudWidgets, opMinus, opPlus, smoothBtn, blurBtn, soundBtn, spMinus, spPlus, blMinus, blPlus);

        // ---- Kategorien (linke Spalte, scrollbar)
        for (int i = 0; i < CATS.length; i++) {
            final String c = CATS[i];
            catBtns.add(add(lx, ly + i * CAT_H, lw, CAT_H - 2, () -> {
                category = c;
                gridS.target = 0;
                refresh();
                sound(1.3f);
            }));
        }

        // ---- Auswahlliste rechts (scrollbar)
        int selSlots = sh / SEL_H + 2;
        selIdx = new int[selSlots];
        for (int i = 0; i < selSlots; i++) {
            final int slot = i;
            selBtns.add(add(sx, sy + i * SEL_H, sw, SEL_H - 2, () -> {
                int k = selIdx[slot];
                if (k >= 0 && k < selectedEntries.size()) toggle(selectedEntries.get(k));
            }));
        }

        // ---- Raster-Zellen (werden jedes Bild neu positioniert)
        int slots = cols * (gh / ROW_H + 3);
        slotIdx = new int[slots];
        for (int i = 0; i < slots; i++) {
            final int slot = i;
            cellBtns.add(add(gx, gy, CELL, CELL + LABEL, () -> {
                int k = slotIdx[slot];
                if (k >= 0 && k < filtered.size()) toggle(filtered.get(k));
            }));
        }

        refresh();
    }

    private static void Collections_addAll(List<ButtonWidget> l, ButtonWidget... bs) {
        l.addAll(Arrays.asList(bs));
    }

    // ---------------------------------------------------------------- Logik

    private void rebuildSelected() {
        selectedEntries.clear();
        for (String k : RedConfig.selected) {
            Entry e = PackIndex.BY_KEY.get(k);
            if (e != null) selectedEntries.add(e);
        }
    }

    private void toggle(Entry e) {
        List<String> sel = RedConfig.selected;
        if (sel.remove(e.key())) {
            sound(0.8f);
        } else {
            sel.removeIf(k -> {
                Entry o = PackIndex.BY_KEY.get(k);
                return o != null && o.path().equals(e.path());
            });
            sel.add(e.key());
            sound(1.5f);
        }
        focus = e;
        dirty = true;
        status = "";
        RedConfig.save();
        rebuildSelected();
    }

    private void refresh() {
        String q = query.toLowerCase().trim();
        Arrays.fill(counts, 0);
        filtered.clear();
        List<String> cats = Arrays.asList(CATS);
        for (Entry e : PackIndex.ENTRIES) {
            if (!q.isEmpty() && !(e.display().toLowerCase().contains(q) || e.pack().toLowerCase().contains(q)
                    || e.category().toLowerCase().contains(q))) continue;
            counts[0]++;
            int ci = cats.indexOf(e.category());
            if (ci > 0) counts[ci]++;
            if (category.equals(CATS[0]) || category.equals(e.category())) filtered.add(e);
        }
    }

    private void applySelection() {
        try {
            SelectionPack.write(selectedEntries);
            dirty = false;
            pendingReload = true;
            status = "Angewendet - jetzt auf Fertig";
            sound(1.6f);
        } catch (IOException | RuntimeException ex) {
            status = "Fehler beim Schreiben";
        }
    }

    private void done() {
        if (dirty) applySelection();
        boolean reload = pendingReload;
        sound(0.9f);
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.setScreen(null);
        if (reload) SelectionPack.enableAndReload(mc);
    }

    private void sound(float pitch) {
        if (!RedConfig.sounds) return;
        MinecraftClient.getInstance().getSoundManager()
                .play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    // ---------------------------------------------------------------- Layout pro Bild

    private void updateLayout(float dt, long now) {
        boolean tex = !hud;
        float sp = 6f + 3f * RedConfig.scrollSpeed;
        int totalRows = (filtered.size() + cols - 1) / cols;
        catS.clampMax(CATS.length * CAT_H - lh);
        gridS.clampMax(totalRows * ROW_H - gh);
        selS.clampMax(selectedEntries.size() * SEL_H - sh);
        catS.update(dt, RedConfig.smooth, sp);
        gridS.update(dt, RedConfig.smooth, sp);
        selS.update(dt, RedConfig.smooth, sp);

        // Tick-Sound beim Scrollen
        int step = (int) (gridS.pos / (ROW_H / 2f));
        if (step != lastStep) {
            lastStep = step;
            if (Math.abs(gridS.vel) > 40 && now - lastTickNs > 70_000_000L) {
                lastTickNs = now;
                sound(1.7f);
            }
        }

        search.visible = tex;
        for (ButtonWidget b : texFixed) b.visible = tex;
        for (ButtonWidget b : hudWidgets) b.visible = !tex;
        hex.visible = !tex;
        applyBtn.visible = tex;

        for (int i = 0; i < catBtns.size(); i++) {
            ButtonWidget b = catBtns.get(i);
            int y = ly + i * CAT_H - Math.round(catS.pos);
            b.setY(y);
            b.visible = tex && y >= ly && y + CAT_H - 2 <= ly + lh;
        }

        int firstRow = Math.max(0, (int) Math.floor(gridS.pos / ROW_H));
        for (int s = 0; s < cellBtns.size(); s++) {
            int row = firstRow + s / cols;
            int idx = row * cols + s % cols;
            slotIdx[s] = idx;
            int y = gy + row * ROW_H - Math.round(gridS.pos);
            ButtonWidget b = cellBtns.get(s);
            b.setX(gx + (s % cols) * (CELL + GAP));
            b.setY(y);
            b.visible = tex && idx < filtered.size() && y >= gy && y + CELL + LABEL <= gy + gh;
        }

        int firstSel = Math.max(0, (int) Math.floor(selS.pos / SEL_H));
        for (int s = 0; s < selBtns.size(); s++) {
            int k = firstSel + s;
            selIdx[s] = k;
            int y = sy + k * SEL_H - Math.round(selS.pos);
            ButtonWidget b = selBtns.get(s);
            b.setY(y);
            b.visible = tex && k < selectedEntries.size() && y >= sy && y + SEL_H - 2 <= sy + sh;
        }
    }

    // ---------------------------------------------------------------- Zeichnen

    @Override
    public void renderBackground(DrawContext c, int mx, int my, float delta) {
        int acc = 0xFF000000 | RedConfig.accent;
        int a = Math.min(255, RedConfig.opacity * 255 / 100) << 24;
        c.fill(0, 0, width, height, 0xB0000000);
        c.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, acc);
        c.fill(px, py, px + pw, py + ph, a | 0x0B0B0B);
        int tint = RedConfig.accent;
        switch (RedConfig.bgStyle) {
            case 1:
                c.fillGradient(px, py, px + pw, py + ph, a | (mix(tint, 0x0B0B0B, 0.25f) & 0xFFFFFF), a | 0x0B0B0B);
                break;
            case 2:
                for (int x = px; x < px + pw; x += 16) c.fill(x, py, x + 1, py + ph, (0x22 << 24) | tint);
                for (int y = py; y < py + ph; y += 16) c.fill(px, y, px + pw, y + 1, (0x22 << 24) | tint);
                break;
            case 3:
                for (int y = py + 6; y < py + ph; y += 14)
                    for (int x = px + 6; x < px + pw; x += 14) c.fill(x, y, x + 2, y + 2, (0x38 << 24) | tint);
                break;
            case 4:
                for (int y = py; y < py + ph; y += 6) c.fill(px, y, px + pw, y + 2, (0x16 << 24) | tint);
                break;
            default:
                break;
        }
        c.fill(px, py, px + pw, py + HEADER, a | 0x101010);
        c.fill(px, py + HEADER, px + pw, py + HEADER + 1, mix(tint, 0x0B0B0B, 0.6f));
        if (!hud) {
            c.fill(px, py + HEADER + 1, px + LEFT, py + ph, a | 0x121212);
            c.fill(rx, py + HEADER + 1, px + pw, py + ph, a | 0x121212);
        }
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;
        updateLayout(dt, now);
        super.render(c, mx, my, delta);

        int acc = 0xFF000000 | RedConfig.accent;
        c.drawText(textRenderer, "RED MENU", px + 12, py + 12, acc, true);
        c.drawText(textRenderer, "Resource Pack Manager", px + 12, py + 24, 0xFF777777, false);
        styled(c, texTab, "Textures", mx, my, !hud);
        styled(c, hudTabBtn, "HUD", mx, my, hud);

        if (hud) {
            renderHud(c, mx, my, acc);
        } else {
            renderTextures(c, mx, my, acc);
        }
        styled(c, doneBtn, "Fertig", mx, my, false);
    }

    private void renderTextures(DrawContext c, int mx, int my, int acc) {
        hovered = null;
        if (query.isEmpty() && !search.isFocused())
            c.drawText(textRenderer, "Suchen (z. B. crystal) ...", gx + 6, py + 18, 0xFF666666, false);

        // Kategorien
        c.enableScissor(lx, ly, lx + lw, ly + lh);
        for (int i = 0; i < CATS.length; i++) {
            int y = ly + i * CAT_H - Math.round(catS.pos);
            if (y + CAT_H < ly || y > ly + lh) continue;
            boolean sel = category.equals(CATS[i]);
            boolean hov = in(mx, my, lx, y, lw, CAT_H - 2) && in(mx, my, lx, ly, lw, lh);
            int bg = sel ? mix(RedConfig.accent, 0x1A1A1A, 0.35f) : hov ? 0xFF2A2A2A : 0xFF1A1A1A;
            c.fill(lx, y, lx + lw, y + CAT_H - 2, bg);
            if (sel) c.fill(lx, y, lx + 2, y + CAT_H - 2, acc);
            c.drawText(textRenderer, CATS[i], lx + 8, y + 6, sel ? 0xFFFFFFFF : 0xFFBBBBBB, false);
            String n = String.valueOf(counts[i]);
            c.drawText(textRenderer, n, lx + lw - 4 - textRenderer.getWidth(n), y + 6, 0xFF777777, false);
        }
        c.disableScissor();

        // Raster
        c.enableScissor(gx, gy, gx + gw, gy + gh);
        float vel = gridS.vel;
        boolean ghost = RedConfig.blur && RedConfig.smooth && Math.abs(vel) > 60;
        int off = ghost ? Math.max(-22, Math.min(22, Math.round(vel * 0.01f * RedConfig.blurStrength))) : 0;
        int firstRow = Math.max(0, (int) Math.floor(gridS.pos / ROW_H));
        int lastRow = (int) Math.ceil((gridS.pos + gh) / ROW_H);
        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = row * cols + col;
                if (idx >= filtered.size()) break;
                Entry e = filtered.get(idx);
                int x = gx + col * (CELL + GAP);
                int y = gy + row * ROW_H - Math.round(gridS.pos);
                if (y + CELL + LABEL < gy || y > gy + gh) continue;
                boolean sel = RedConfig.selected.contains(e.key());
                boolean hov = in(mx, my, x, y, CELL, CELL + LABEL) && in(mx, my, gx, gy, gw, gh);
                if (hov) hovered = e;
                c.fill(x, y, x + CELL, y + CELL + LABEL, sel ? mix(RedConfig.accent, 0x161616, 0.22f) : 0xFF161616);
                border(c, x, y, CELL, CELL + LABEL, (sel || hov) ? acc : 0xFF2A2A2A);
                Preview p = PackIndex.preview(e, 32);
                if (ghost) {
                    drawTex(c, p, x + 4, y + 4 + off, CELL - 8, CELL - 8, 0.28f);
                    if (RedConfig.blurStrength >= 3) drawTex(c, p, x + 4, y + 4 + off * 2, CELL - 8, CELL - 8, 0.14f);
                }
                drawTex(c, p, x + 4, y + 4, CELL - 8, CELL - 8, 1f);
                c.drawText(textRenderer, textRenderer.trimToWidth(e.display(), CELL - 6), x + 3, y + CELL + 2,
                        sel ? 0xFFFFFFFF : 0xFFDDDDDD, false);
                if (sel) c.fill(x + CELL - 10, y + 2, x + CELL - 2, y + 10, acc);
            }
        }
        if (filtered.isEmpty()) {
            String l1 = PackIndex.ENTRIES.isEmpty() ? "Noch keine Packs gefunden." : "Keine Texturen gefunden.";
            c.drawText(textRenderer, l1, gx + 4, gy + 6, 0xFFAAAAAA, false);
            c.drawText(textRenderer, textRenderer.trimToWidth("Packs (.zip) in redclient/packs legen, dann Neu laden.", gw - 8),
                    gx + 4, gy + 20, 0xFF777777, false);
        }
        c.disableScissor();

        // Rechte Spalte: Auswahlliste
        c.drawText(textRenderer, "AUSGEWÄHLT (" + selectedEntries.size() + ")", sx, py + HEADER + 10, acc, false);
        c.enableScissor(sx, sy, sx + sw, sy + sh);
        for (int i = 0; i < selectedEntries.size(); i++) {
            int y = sy + i * SEL_H - Math.round(selS.pos);
            if (y + SEL_H < sy || y > sy + sh) continue;
            Entry e = selectedEntries.get(i);
            boolean hov = in(mx, my, sx, y, sw, SEL_H - 2) && in(mx, my, sx, sy, sw, sh);
            c.fill(sx, y, sx + sw, y + SEL_H - 2, hov ? 0xFF2A2A2A : 0xFF1A1A1A);
            c.fill(sx, y, sx + 2, y + SEL_H - 2, acc);
            drawTex(c, PackIndex.preview(e, 16), sx + 4, y, 16, 16, 1f);
            c.drawText(textRenderer, textRenderer.trimToWidth(e.display(), sw - 46), sx + 24, y + 5, 0xFFDDDDDD, false);
            c.drawText(textRenderer, "×", sx + sw - 10, y + 5, hov ? acc : 0xFF777777, false);
        }
        if (selectedEntries.isEmpty()) {
            c.drawText(textRenderer, "Noch nichts ausgewählt.", sx + 2, sy + 4, 0xFF777777, false);
            c.drawText(textRenderer, "Klicke Texturen an.", sx + 2, sy + 16, 0xFF555555, false);
        }
        c.disableScissor();

        // Vorschau gross
        Entry pe = hovered != null ? hovered : focus;
        int pvx = rx + (RIGHT - PV) / 2;
        for (int yy = 0; yy < PV; yy += 8)
            for (int xx = 0; xx < PV; xx += 8)
                c.fill(pvx + xx, pvY + yy, pvx + xx + 8, pvY + yy + 8, ((xx + yy) / 8) % 2 == 0 ? 0xFF1C1C1C : 0xFF262626);
        border(c, pvx - 1, pvY - 1, PV + 2, PV + 2, acc);
        if (pe != null) {
            drawTex(c, PackIndex.preview(pe, 64), pvx, pvY, PV, PV, 1f);
            c.drawText(textRenderer, textRenderer.trimToWidth(pe.display(), RIGHT - 16), rx + 8, pvBottom - 24, 0xFFFFFFFF, false);
            c.drawText(textRenderer, textRenderer.trimToWidth(pe.pack(), RIGHT - 16), rx + 8, pvBottom - 12, 0xFF888888, false);
        } else {
            c.drawText(textRenderer, "Vorschau", pvx + 4, pvY + 4, 0xFF555555, false);
        }
        if (!status.isEmpty())
            c.drawText(textRenderer, textRenderer.trimToWidth(status, RIGHT - 16), rx + 8, pvBottom + 4, acc, false);

        styled(c, applyBtn, "Anwenden", mx, my, dirty);
        styled(c, openBtn, "Pack-Ordner öffnen", mx, my, false);
        styled(c, reloadBtn, "Neu laden", mx, my, false);
        c.drawText(textRenderer, filtered.size() + " Texturen", gx + 100, py + ph - 21, 0xFF888888, false);
    }

    private void renderHud(DrawContext c, int mx, int my, int acc) {
        c.drawText(textRenderer, "MENÜFARBE", hax, hay, acc, false);
        for (int i = 0; i < swatchBtns.size(); i++) {
            ButtonWidget b = swatchBtns.get(i);
            boolean cur = SWATCHES[i] == RedConfig.accent;
            c.fill(b.getX(), b.getY(), b.getX() + b.getWidth(), b.getY() + b.getHeight(), cur ? 0xFFFFFFFF : 0xFF000000);
            c.fill(b.getX() + 1, b.getY() + 1, b.getX() + b.getWidth() - 1, b.getY() + b.getHeight() - 1,
                    0xFF000000 | SWATCHES[i]);
        }
        c.drawText(textRenderer, "Eigene Farbe (Hex)", hax + 98, hay + 73, 0xFF888888, false);

        c.drawText(textRenderer, "HINTERGRUND-TEXTUR", hax, hay + 100, acc, false);
        for (int i = 0; i < styleBtns.size(); i++) styled(c, styleBtns.get(i), STYLES[i], mx, my, RedConfig.bgStyle == i);

        c.drawText(textRenderer, "DECKKRAFT", hax, hay + 170, acc, false);
        stepper(c, opMinus, opPlus, RedConfig.opacity + "%", mx, my);

        c.drawText(textRenderer, "EFFEKTE", hbx, hay, acc, false);
        styled(c, smoothBtn, "Smooth Scrolling: " + (RedConfig.smooth ? "AN" : "AUS"), mx, my, RedConfig.smooth);
        styled(c, blurBtn, "Motion Blur: " + (RedConfig.blur ? "AN" : "AUS"), mx, my, RedConfig.blur);
        styled(c, soundBtn, "Sounds: " + (RedConfig.sounds ? "AN" : "AUS"), mx, my, RedConfig.sounds);
        c.drawText(textRenderer, "SCROLL-GESCHWINDIGKEIT", hbx, hay + 100, acc, false);
        stepper(c, spMinus, spPlus, RedConfig.scrollSpeed + " / 5", mx, my);
        c.drawText(textRenderer, "MOTION-BLUR-STÄRKE", hbx, hay + 142, acc, false);
        stepper(c, blMinus, blPlus, RedConfig.blurStrength + " / 5", mx, my);
        c.drawText(textRenderer, "Änderungen werden sofort gespeichert.", hax, py + ph - 20, 0xFF666666, false);
    }

    private void stepper(DrawContext c, ButtonWidget minus, ButtonWidget plus, String value, int mx, int my) {
        styled(c, minus, "-", mx, my, false);
        styled(c, plus, "+", mx, my, false);
        int cx = (minus.getX() + minus.getWidth() + plus.getX()) / 2;
        c.drawText(textRenderer, value, cx - textRenderer.getWidth(value) / 2, minus.getY() + 5, 0xFFFFFFFF, false);
    }

    /** Zeichnet die Textur pixelgenau (ganzzahlig skaliert), Zeilen mit gleicher Farbe werden zusammengefasst. */
    private void drawTex(DrawContext c, Preview p, int bx, int by, int bw, int bh, float alpha) {
        if (p.w() == 0) return;
        int s = Math.max(1, Math.min(bw / p.w(), bh / p.h()));
        int ox = bx + (bw - p.w() * s) / 2;
        int oy = by + (bh - p.h() * s) / 2;
        int[] px = p.px();
        for (int y = 0; y < p.h(); y++) {
            int x = 0;
            while (x < p.w()) {
                int col = px[y * p.w() + x];
                int run = 1;
                while (x + run < p.w() && px[y * p.w() + x + run] == col) run++;
                if ((col >>> 24) != 0) {
                    int cc = alpha >= 1f ? col : (((int) ((col >>> 24) * alpha)) << 24) | (col & 0xFFFFFF);
                    c.fill(ox + x * s, oy + y * s, ox + (x + run) * s, oy + (y + 1) * s, cc);
                }
                x += run;
            }
        }
    }

    private void styled(DrawContext c, ButtonWidget b, String label, int mx, int my, boolean on) {
        boolean hov = in(mx, my, b.getX(), b.getY(), b.getWidth(), b.getHeight());
        int bg = on ? mix(RedConfig.accent, 0x101010, hov ? 0.85f : 0.6f)
                : mix(RedConfig.accent, 0x101010, hov ? 0.5f : 0.22f);
        c.fill(b.getX(), b.getY(), b.getX() + b.getWidth(), b.getY() + b.getHeight(), bg);
        int tx = b.getX() + (b.getWidth() - textRenderer.getWidth(label)) / 2;
        c.drawText(textRenderer, label, tx, b.getY() + (b.getHeight() - 8) / 2, 0xFFFFFFFF, false);
    }

    private static void border(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x, y, x + w, y + 1, col);
        c.fill(x, y + h - 1, x + w, y + h, col);
        c.fill(x, y, x + 1, y + h, col);
        c.fill(x + w - 1, y, x + w, y + h, col);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * t + ((b >> 16) & 255) * (1 - t));
        int g = (int) (((a >> 8) & 255) * t + ((b >> 8) & 255) * (1 - t));
        int bl = (int) ((a & 255) * t + (b & 255) * (1 - t));
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // ---------------------------------------------------------------- Eingabe

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (!hud) {
            if (in(mx, my, lx, ly, lw, lh)) {
                catS.by((float) (-v * CAT_H * 2));
                return true;
            }
            if (in(mx, my, sx, sy, sw, sh)) {
                selS.by((float) (-v * SEL_H * 2));
                return true;
            }
            if (in(mx, my, gx, gy, gw, gh)) {
                gridS.by((float) (-v * ROW_H * (0.5 + 0.15 * RedConfig.scrollSpeed)));
                return true;
            }
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
