package de.redclient.textures;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RedMenuScreen extends Screen {
    private static final int[] SWATCHES = {
            0xE02020, 0xFF6A00, 0xF2C200, 0x2ECC40, 0x00B7D4, 0x3B6CFF, 0x9B4DFF, 0xFF4FA3
    };
    private static final int LEFT = 112, RIGHT = 152, CELL = 56, GAP = 6, LABEL = 12;
    private static final int N = PackIndex.N;

    private String query = "";
    private String category = PackIndex.CATEGORIES[0];
    private int page = 0;
    private PackIndex.Entry selected;

    private final List<PackIndex.Entry> filtered = new ArrayList<>();
    private final int[] counts = new int[PackIndex.CATEGORIES.length];
    private final List<ButtonWidget> catButtons = new ArrayList<>();
    private final List<ButtonWidget> cellButtons = new ArrayList<>();
    private final List<ButtonWidget> swatchButtons = new ArrayList<>();
    private ButtonWidget openBtn, reloadBtn, prevBtn, nextBtn;
    private TextFieldWidget search, hex;

    private int px, py, pw, ph, gx, gy, gw, gh, cols, rows, rx;

    public RedMenuScreen() {
        super(Text.literal("Red Menu"));
        PackIndex.reload();
    }

    @Override
    protected void init() {
        catButtons.clear();
        cellButtons.clear();
        swatchButtons.clear();

        int m = 16;
        px = m;
        py = m;
        pw = width - 2 * m;
        ph = height - 2 * m;
        rx = px + pw - RIGHT;
        gx = px + LEFT + 8;
        gy = py + 46;
        gw = pw - LEFT - RIGHT - 16;
        gh = ph - 46 - 34;
        cols = Math.max(1, (gw + GAP) / (CELL + GAP));
        rows = Math.max(1, (gh + GAP) / (CELL + LABEL + GAP));

        // Kategorien (linke Spalte)
        for (int i = 0; i < PackIndex.CATEGORIES.length; i++) {
            final String c = PackIndex.CATEGORIES[i];
            catButtons.add(addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
                category = c;
                page = 0;
                refresh();
            }).dimensions(px + 8, py + 46 + i * 20, LEFT - 16, 18).build()));
        }

        // Suchleiste
        search = new TextFieldWidget(textRenderer, gx, py + 10, gw, 20, Text.literal("Suche"));
        search.setText(query);
        search.setChangedListener(s -> {
            query = s;
            page = 0;
            refresh();
        });
        addDrawableChild(search);

        // Texture-Raster
        for (int i = 0; i < cols * rows; i++) {
            final int idx = i;
            int cx = gx + (i % cols) * (CELL + GAP);
            int cy = gy + (i / cols) * (CELL + LABEL + GAP);
            cellButtons.add(addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
                int k = page * cols * rows + idx;
                if (k < filtered.size()) selected = filtered.get(k);
            }).dimensions(cx, cy, CELL, CELL + LABEL).build()));
        }

        // Farb-Presets
        for (int i = 0; i < SWATCHES.length; i++) {
            final int col = SWATCHES[i];
            swatchButtons.add(addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
                RedConfig.accent = col;
                RedConfig.save();
                hex.setText(String.format("#%06X", col));
            }).dimensions(rx + 8 + (i % 4) * 34, py + 62 + (i / 4) * 22, 30, 18).build()));
        }

        // Hex-Farbe
        hex = new TextFieldWidget(textRenderer, rx + 8, py + 122, 90, 18, Text.literal("Hex"));
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

        // Untere Leiste
        int by = py + ph - 26;
        openBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b ->
                Util.getOperatingSystem().open(PackIndex.packDir().toFile())
        ).dimensions(px + 8, by, LEFT - 16, 18).build());
        reloadBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            PackIndex.reload();
            refresh();
        }).dimensions(gx, by, 80, 18).build());
        prevBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            page--;
            refresh();
        }).dimensions(gx + gw - 40, by, 18, 18).build());
        nextBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            page++;
            refresh();
        }).dimensions(gx + gw - 18, by, 18, 18).build());

        refresh();
    }

    private int maxPage() {
        return Math.max(0, (filtered.size() - 1) / (cols * rows));
    }

    private void refresh() {
        String q = query.toLowerCase().trim();
        Arrays.fill(counts, 0);
        filtered.clear();
        List<String> cats = Arrays.asList(PackIndex.CATEGORIES);
        for (PackIndex.Entry e : PackIndex.ENTRIES) {
            if (!q.isEmpty() && !(e.name().toLowerCase().contains(q) || e.pack().toLowerCase().contains(q)))
                continue;
            counts[0]++;
            int ci = cats.indexOf(e.category());
            if (ci > 0) counts[ci]++;
            if (category.equals(PackIndex.CATEGORIES[0]) || category.equals(e.category())) filtered.add(e);
        }
        page = Math.max(0, Math.min(page, maxPage()));
        int per = cols * rows;
        for (int i = 0; i < cellButtons.size(); i++) {
            cellButtons.get(i).visible = page * per + i < filtered.size();
        }
        prevBtn.active = page > 0;
        nextBtn.active = page < maxPage();
    }

    // ---------- Rendering ----------

    @Override
    public void renderBackground(DrawContext c, int mx, int my, float delta) {
        int acc = 0xFF000000 | RedConfig.accent;
        c.fill(0, 0, width, height, 0xCC000000);
        c.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, acc);
        c.fill(px, py, px + pw, py + ph, 0xFF0B0B0B);
        c.fill(px, py, px + LEFT, py + ph, 0xFF121212);
        c.fill(rx, py, px + pw, py + ph, 0xFF121212);
        c.fill(px, py + 38, px + pw, py + 39, mix(RedConfig.accent, 0x0B0B0B, 0.6f));
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        int acc = 0xFF000000 | RedConfig.accent;

        c.drawText(textRenderer, "RED MENU", px + 10, py + 11, acc, true);
        c.drawText(textRenderer, "TEXTURES", px + 10, py + 22, 0xFF888888, false);

        if (query.isEmpty() && !search.isFocused())
            c.drawText(textRenderer, "Suchen (z. B. crystal) ...", gx + 6, py + 16, 0xFF666666, false);

        // Kategorien
        for (int i = 0; i < catButtons.size(); i++) {
            ButtonWidget b = catButtons.get(i);
            boolean sel = category.equals(PackIndex.CATEGORIES[i]);
            boolean hov = in(b, mx, my);
            int bg = sel ? mix(RedConfig.accent, 0x1A1A1A, 0.35f) : hov ? 0xFF2A2A2A : 0xFF1A1A1A;
            c.fill(b.getX(), b.getY(), b.getX() + b.getWidth(), b.getY() + b.getHeight(), bg);
            if (sel) c.fill(b.getX(), b.getY(), b.getX() + 2, b.getY() + b.getHeight(), acc);
            c.drawText(textRenderer, PackIndex.CATEGORIES[i], b.getX() + 8, b.getY() + 5,
                    sel ? 0xFFFFFFFF : 0xFFBBBBBB, false);
            String n = String.valueOf(counts[i]);
            c.drawText(textRenderer, n, b.getX() + b.getWidth() - 4 - textRenderer.getWidth(n),
                    b.getY() + 5, 0xFF777777, false);
        }

        // Raster
        int per = cols * rows;
        for (int i = 0; i < cellButtons.size(); i++) {
            ButtonWidget b = cellButtons.get(i);
            if (!b.visible) continue;
            PackIndex.Entry e = filtered.get(page * per + i);
            boolean sel = e == selected;
            boolean hov = in(b, mx, my);
            int x = b.getX(), y = b.getY();
            c.fill(x, y, x + CELL, y + CELL + LABEL, 0xFF161616);
            border(c, x, y, CELL, CELL + LABEL, (sel || hov) ? acc : 0xFF2A2A2A);
            drawPreview(c, e, x + 4, y + 4, (CELL - 8) / N);
            c.drawText(textRenderer, textRenderer.trimToWidth(e.name(), CELL - 6), x + 3, y + CELL + 1,
                    0xFFDDDDDD, false);
        }

        if (filtered.isEmpty()) {
            String l1 = PackIndex.ENTRIES.isEmpty()
                    ? "Noch keine Packs gefunden."
                    : "Keine Texturen gefunden.";
            String l2 = "Lege Resource Packs (.zip) in redclient/packs und klicke Neu laden.";
            c.drawText(textRenderer, l1, gx + 4, gy + 6, 0xFFAAAAAA, false);
            c.drawText(textRenderer, textRenderer.trimToWidth(l2, gw - 8), gx + 4, gy + 20, 0xFF777777, false);
        }

        // Rechte Spalte
        c.drawText(textRenderer, "FARBE", rx + 8, py + 48, acc, false);
        for (ButtonWidget b : swatchButtons) {
            int col = SWATCHES[swatchButtons.indexOf(b)];
            boolean cur = col == RedConfig.accent;
            c.fill(b.getX(), b.getY(), b.getX() + b.getWidth(), b.getY() + b.getHeight(),
                    cur ? 0xFFFFFFFF : 0xFF000000);
            c.fill(b.getX() + 1, b.getY() + 1, b.getX() + b.getWidth() - 1, b.getY() + b.getHeight() - 1,
                    0xFF000000 | col);
        }
        c.drawText(textRenderer, "Hex-Farbe", rx + 8, py + 110, 0xFF888888, false);

        c.drawText(textRenderer, "AUSGEWÄHLT", rx + 8, py + 152, acc, false);
        if (selected != null) {
            int s = (RIGHT - 16) / N;
            c.fill(rx + 8, py + 166, rx + 8 + s * N, py + 166 + s * N, 0xFF161616);
            drawPreview(c, selected, rx + 8, py + 166, s);
            int ty = py + 166 + s * N + 6;
            c.drawText(textRenderer, textRenderer.trimToWidth(selected.name(), RIGHT - 16), rx + 8, ty, 0xFFFFFFFF, false);
            c.drawText(textRenderer, textRenderer.trimToWidth(selected.pack(), RIGHT - 16), rx + 8, ty + 12, 0xFF888888, false);
            c.drawText(textRenderer, selected.category(), rx + 8, ty + 24, acc, false);
        } else {
            c.drawText(textRenderer, "Klicke eine Textur an.", rx + 8, py + 168, 0xFF666666, false);
        }

        // Untere Leiste
        styled(c, openBtn, "Pack-Ordner öffnen", mx, my);
        styled(c, reloadBtn, "Neu laden", mx, my);
        styled(c, prevBtn, "<", mx, my);
        styled(c, nextBtn, ">", mx, my);
        String info = "Seite " + (page + 1) + "/" + (maxPage() + 1) + "  ·  " + filtered.size() + " Texturen";
        c.drawText(textRenderer, info, gx + 90, py + ph - 21, 0xFF888888, false);
    }

    private void drawPreview(DrawContext c, PackIndex.Entry e, int x, int y, int s) {
        int[] px = PackIndex.preview(e);
        for (int yy = 0; yy < N; yy++) {
            for (int xx = 0; xx < N; xx++) {
                int col = px[yy * N + xx];
                if ((col >>> 24) == 0) continue;
                c.fill(x + xx * s, y + yy * s, x + xx * s + s, y + yy * s + s, col);
            }
        }
    }

    private void styled(DrawContext c, ButtonWidget b, String label, int mx, int my) {
        boolean hov = in(b, mx, my) && b.active;
        int bg = !b.active ? 0xFF161616 : mix(RedConfig.accent, 0x101010, hov ? 0.75f : 0.45f);
        c.fill(b.getX(), b.getY(), b.getX() + b.getWidth(), b.getY() + b.getHeight(), bg);
        int tx = b.getX() + (b.getWidth() - textRenderer.getWidth(label)) / 2;
        c.drawText(textRenderer, label, tx, b.getY() + (b.getHeight() - 8) / 2,
                b.active ? 0xFFFFFFFF : 0xFF555555, false);
    }

    private static void border(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x, y, x + w, y + 1, col);
        c.fill(x, y + h - 1, x + w, y + h, col);
        c.fill(x, y, x + 1, y + h, col);
        c.fill(x + w - 1, y, x + w, y + h, col);
    }

    private static boolean in(ButtonWidget b, int mx, int my) {
        return mx >= b.getX() && mx < b.getX() + b.getWidth() && my >= b.getY() && my < b.getY() + b.getHeight();
    }

    /** Mischt zwei RGB-Farben (t = Anteil von a) und liefert eine deckende ARGB-Farbe. */
    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * t + ((b >> 16) & 255) * (1 - t));
        int g = (int) (((a >> 8) & 255) * t + ((b >> 8) & 255) * (1 - t));
        int bl = (int) ((a & 255) * t + (b & 255) * (1 - t));
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // ---------- Eingabe ----------

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (v < 0 && page < maxPage()) page++;
        else if (v > 0 && page > 0) page--;
        else return super.mouseScrolled(mx, my, h, v);
        refresh();
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
