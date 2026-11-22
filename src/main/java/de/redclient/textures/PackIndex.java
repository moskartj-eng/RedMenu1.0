package de.redclient.textures;

import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Liest alle Packs aus .minecraft/redclient/packs und indexiert deren Texturen. */
public final class PackIndex {
    public record Entry(String pack, String path, String name, String category, Path source, boolean zip) {
    }

    /** Aufloesung der Vorschau (N x N Pixel). */
    public static final int N = 16;

    public static final String[] CATEGORIES = {
            "Alle", "Crystals", "Swords", "Axes", "Pickaxes", "Armor", "Totems", "Blocks", "Entities", "Items"
    };

    public static final List<Entry> ENTRIES = new ArrayList<>();
    private static final Map<String, int[]> CACHE = new HashMap<>();
    private static final String PREFIX = "assets/minecraft/textures/";

    public static Path packDir() {
        return FabricLoader.getInstance().getGameDir().resolve("redclient").resolve("packs");
    }

    public static void reload() {
        ENTRIES.clear();
        CACHE.clear();
        try {
            Files.createDirectories(packDir());
        } catch (IOException e) {
            return;
        }
        try (Stream<Path> s = Files.list(packDir())) {
            s.sorted().forEach(p -> {
                try {
                    if (Files.isDirectory(p)) scanDir(p);
                    else if (p.getFileName().toString().toLowerCase().endsWith(".zip")) scanZip(p);
                } catch (Exception ignored) {
                }
            });
        } catch (IOException ignored) {
        }
        ENTRIES.sort(Comparator.comparing((Entry e) -> e.name().toLowerCase()).thenComparing(Entry::pack));
    }

    private static void scanZip(Path p) throws IOException {
        String pack = p.getFileName().toString();
        try (ZipFile z = new ZipFile(p.toFile())) {
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (!e.isDirectory()) add(pack, e.getName().replace('\\', '/'), p, true);
            }
        }
    }

    private static void scanDir(Path dir) throws IOException {
        String pack = dir.getFileName().toString();
        try (Stream<Path> w = Files.walk(dir.resolve("assets/minecraft/textures"))) {
            w.filter(Files::isRegularFile).forEach(f ->
                    add(pack, dir.relativize(f).toString().replace('\\', '/'), dir, false));
        }
    }

    private static void add(String pack, String path, Path source, boolean zip) {
        if (!path.startsWith(PREFIX) || !path.endsWith(".png")) return;
        String rel = path.substring(PREFIX.length());
        int slash = rel.indexOf('/');
        if (slash < 0) return;
        String sub = rel.substring(0, slash);
        if (!(sub.equals("item") || sub.equals("block") || sub.equals("entity"))) return;
        String name = rel.substring(rel.lastIndexOf('/') + 1, rel.length() - 4);
        ENTRIES.add(new Entry(pack, path, name, classify(sub, name.toLowerCase()), source, zip));
    }

    private static String classify(String sub, String n) {
        if (n.contains("crystal")) return "Crystals";
        if (n.contains("totem")) return "Totems";
        if (n.endsWith("_sword")) return "Swords";
        if (n.endsWith("_pickaxe")) return "Pickaxes";
        if (n.endsWith("_axe")) return "Axes";
        if (n.endsWith("_helmet") || n.endsWith("_chestplate") || n.endsWith("_leggings") || n.endsWith("_boots"))
            return "Armor";
        if (sub.equals("block")) return "Blocks";
        if (sub.equals("entity")) return "Entities";
        return "Items";
    }

    /** Liefert eine N x N ARGB-Vorschau (gecacht, wird erst beim Anzeigen geladen). */
    public static int[] preview(Entry e) {
        return CACHE.computeIfAbsent(e.pack() + "|" + e.path(), k -> load(e));
    }

    private static int[] load(Entry e) {
        int[] out = new int[N * N];
        try (InputStream in = open(e)) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) return out;
            int w = img.getWidth();
            int h = Math.min(img.getHeight(), w); // animierte Texturen: nur erstes Bild
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    int sx = Math.min(w - 1, (int) ((x + 0.5) * w / N));
                    int sy = Math.min(h - 1, (int) ((y + 0.5) * h / N));
                    out[y * N + x] = img.getRGB(sx, sy);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static InputStream open(Entry e) throws IOException {
        if (!e.zip()) return Files.newInputStream(e.source().resolve(e.path()));
        ZipFile z = new ZipFile(e.source().toFile());
        ZipEntry ze = z.getEntry(e.path());
        if (ze == null) {
            z.close();
            throw new IOException("Eintrag fehlt: " + e.path());
        }
        return new FilterInputStream(z.getInputStream(ze)) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    z.close();
                }
            }
        };
    }
}
