package de.redclient.textures;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Baut aus der Auswahl ein Resource Pack (resourcepacks/RedMenuSelection) und aktiviert es. */
public final class SelectionPack {
    public static final String NAME = "RedMenuSelection";
    public static final String PACK_ID = "file/" + NAME;

    private static Path dir() {
        return FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve(NAME);
    }

    public static void write(List<PackIndex.Entry> entries) throws IOException {
        Path d = dir();
        if (Files.exists(d)) {
            try (Stream<Path> w = Files.walk(d)) {
                for (Path p : (Iterable<Path>) w.sorted(Comparator.reverseOrder())::iterator) Files.delete(p);
            }
        }
        Files.createDirectories(d);
        Files.writeString(d.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":75,\"description\":\"Red Menu Auswahl\"}}");
        for (PackIndex.Entry e : entries) {
            byte[] data = PackIndex.readPath(e, e.path());
            if (data == null) continue;
            Path out = d.resolve(e.path());
            Files.createDirectories(out.getParent());
            Files.write(out, data);
            byte[] meta = PackIndex.readPath(e, e.path() + ".mcmeta");
            if (meta != null) Files.write(d.resolve(e.path() + ".mcmeta"), meta);
        }
    }

    /** Aktiviert das Auswahl-Pack ganz oben und laedt alle Resource Packs neu. */
    public static void enableAndReload(MinecraftClient mc) {
        ResourcePackManager m = mc.getResourcePackManager();
        m.scanPacks();
        List<String> before = new ArrayList<>(m.getEnabledIds());
        List<String> ids = new ArrayList<>(before);
        ids.remove(PACK_ID);
        ids.add(PACK_ID);
        m.setEnabledProfiles(ids);
        mc.options.refreshResourcePacks(m); // laedt neu, wenn sich die Liste geaendert hat
        if (before.equals(new ArrayList<>(m.getEnabledIds()))) mc.reloadResources();
    }
}
