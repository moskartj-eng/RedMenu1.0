package de.redclient.textures;

import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Liest alle Packs aus .minecraft/redclient/packs und indexiert deren Texturen. */
public final class PackIndex {
    public record Entry(String pack, String path, String name, String display, String category,
                        String sub, Path source, boolean zip) {
        public String key() {
            return pack + "|" + path;
        }
    }

    /** Vorschau: w x h Pixel, ARGB. */
    public record Preview(int w, int h, int[] px) {
    }

    public static final String[] CATEGORIES = {
            "Alle", "Crystals", "Swords", "Axes", "Pickaxes", "Shovels", "Hoes", "Bows", "Tridents",
            "Maces", "Shields", "Helmets", "Chestplates", "Leggings", "Boots", "Totems", "Food",
            "Potions", "Gems & Ingots", "Ores", "Plants", "Blocks", "Entities", "Particles",
            "GUI / HUD", "Environment", "Items"
    };

    public static final List<Entry> ENTRIES = new ArrayList<>();
    public static final Map<String, Entry> BY_KEY = new HashMap<>();
    private static final Preview EMPTY = new Preview(0, 0, new int[0]);
    private static final Map<String, Preview> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Preview> eldest) {
            return size() > 900;
        }
    };
    private static final String PREFIX = "assets/minecraft/textures/";
    private static final Set<String> SUBS = Set.of("item", "block", "entity", "particle", "gui", "environment");

    public static Path packDir() {
        return FabricLoader.getInstance().getGameDir().resolve("redclient").resolve("packs");
    }

    public static void reload() {
        ENTRIES.clear();
        BY_KEY.clear();
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
        ENTRIES.sort(Comparator.comparing((Entry e) -> e.display().toLowerCase()).thenComparing(Entry::pack));
        for (Entry e : ENTRIES) BY_KEY.put(e.key(), e);
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
        if (!SUBS.contains(sub)) return;
        String stem = rel.substring(rel.lastIndexOf('/') + 1, rel.length() - 4);
        String parent = "";
        int ls = rel.lastIndexOf('/');
        if (ls > 0) {
            String pp = rel.substring(0, ls);
            parent = pp.substring(pp.lastIndexOf('/') + 1);
        }
        String display = (sub.equals("gui") || sub.equals("entity") || sub.equals("environment")) && !parent.isEmpty()
                ? parent + "/" + stem : stem;
        ENTRIES.add(new Entry(pack, path, stem, display, classify(sub, stem.toLowerCase()), sub, source, zip));
    }

    private static final String[] FOOD = {"apple", "bread", "beef", "porkchop", "mutton", "chicken", "rabbit",
            "cod", "salmon", "carrot", "potato", "melon_slice", "cookie", "cake", "pie", "stew", "soup",
            "berries", "honey_bottle", "beetroot", "steak", "dried_kelp", "chorus_fruit", "tropical_fish",
            "pufferfish", "rotten_flesh", "spider_eye"};
    private static final String[] PLANTS = {"sapling", "flower", "tulip", "orchid", "allium", "azalea", "fern",
            "grass", "vine", "leaves", "mushroom", "kelp", "seagrass", "roots", "bush", "lily", "dandelion",
            "poppy", "rose", "peony", "lilac", "cornflower", "bluet", "wheat", "bamboo", "cactus"};

    private static boolean any(String n, String[] keys) {
        for (String k : keys) if (n.contains(k)) return true;
        return false;
    }

    private static String classify(String sub, String n) {
        switch (sub) {
            case "gui":
                return "GUI / HUD";
            case "particle":
                return "Particles";
            case "environment":
                return "Environment";
            default:
                break;
        }
        if (n.contains("crystal")) return "Crystals";
        if (n.contains("totem")) return "Totems";
        if (n.endsWith("_sword")) return "Swords";
        if (n.endsWith("_pickaxe")) return "Pickaxes";
        if (n.endsWith("_axe")) return "Axes";
        if (n.endsWith("_shovel")) return "Shovels";
        if (n.endsWith("_hoe")) return "Hoes";
        if (n.equals("bow") || n.startsWith("bow_") || n.contains("crossbow")) return "Bows";
        if (n.contains("trident")) return "Tridents";
        if (n.equals("mace") || n.startsWith("mace_")) return "Maces";
        if (n.contains("shield")) return "Shields";
        if (n.contains("helmet")) return "Helmets";
        if (n.contains("chestplate")) return "Chestplates";
        if (n.contains("leggings")) return "Leggings";
        if (n.contains("boots")) return "Boots";
        if (n.contains("potion") || n.equals("experience_bottle") || n.equals("dragon_breath")
                || n.equals("glass_bottle")) return "Potions";
        if (sub.equals("item")) {
            if (any(n, FOOD)) return "Food";
            if (n.endsWith("_ingot") || n.endsWith("_nugget") || n.endsWith("_scrap") || n.startsWith("raw_")
                    || n.equals("diamond") || n.equals("emerald") || n.equals("coal") || n.equals("charcoal")
                    || n.equals("quartz") || n.equals("lapis_lazuli") || n.equals("amethyst_shard")
                    || n.equals("redstone")) return "Gems & Ingots";
        }
        if (sub.equals("block")) {
            if (n.contains("_ore")) return "Ores";
            if (any(n, PLANTS)) return "Plants";
            return "Blocks";
        }
        if (sub.equals("entity")) return "Entities";
        return "Items";
    }

    /** Liest eine Datei aus dem Pack des Eintrags; null, wenn sie nicht existiert. */
    public static byte[] readPath(Entry e, String path) throws IOException {
        if (!e.zip()) {
            Path f = e.source().resolve(path);
            return Files.exists(f) ? Files.readAllBytes(f) : null;
        }
        try (ZipFile z = new ZipFile(e.source().toFile())) {
            ZipEntry ze = z.getEntry(path);
            if (ze == null) return null;
            return z.getInputStream(ze).readAllBytes();
        }
    }

    /**
     * Vorschau mit Box-Filter (Mittelwert). Texturen bis maxDim werden pixelgenau gezeigt,
     * groessere werden ohne Rauschen verkleinert.
     */
    public static Preview preview(Entry e, int maxDim) {
        return CACHE.computeIfAbsent(e.key() + "@" + maxDim, k -> load(e, maxDim));
    }

    private static Preview load(Entry e, int maxDim) {
        try {
            byte[] b = readPath(e, e.path());
            if (b == null) return EMPTY;
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(b));
            if (img == null) return EMPTY;
            int w = img.getWidth(), h = img.getHeight();
            if ((e.sub().equals("item") || e.sub().equals("block")) && h > w) h = w; // Animation: erstes Bild
            int[] src = img.getRGB(0, 0, w, h, null, 0, w);
            float scale = Math.min(1f, Math.min((float) maxDim / w, (float) maxDim / h));
            int tw = Math.max(1, Math.round(w * scale));
            int th = Math.max(1, Math.round(h * scale));
            int[] out = new int[tw * th];
            for (int ty = 0; ty < th; ty++) {
                int sy0 = ty * h / th;
                int sy1 = Math.max(sy0 + 1, (ty + 1) * h / th);
                for (int tx = 0; tx < tw; tx++) {
                    int sx0 = tx * w / tw;
                    int sx1 = Math.max(sx0 + 1, (tx + 1) * w / tw);
                    long a = 0, r = 0, g = 0, bl = 0;
                    int n = 0;
                    for (int sy = sy0; sy < sy1; sy++) {
                        for (int sx = sx0; sx < sx1; sx++) {
                            int p = src[sy * w + sx];
                            int pa = p >>> 24;
                            a += pa;
                            r += (long) ((p >> 16) & 255) * pa;
                            g += (long) ((p >> 8) & 255) * pa;
                            bl += (long) (p & 255) * pa;
                            n++;
                        }
                    }
                    if (a == 0) {
                        out[ty * tw + tx] = 0;
                    } else {
                        int oa = (int) (a / n);
                        out[ty * tw + tx] = (oa << 24) | ((int) (r / a) << 16) | ((int) (g / a) << 8) | (int) (bl / a);
                    }
                }
            }
            return new Preview(tw, th, out);
        } catch (Exception ex) {
            return EMPTY;
        }
    }
}
