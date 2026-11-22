package de.redclient.textures;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RedConfig {
    /** Akzentfarbe als RGB (ohne Alpha). Standard: Rot. */
    public static int accent = 0xE02020;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("redtextures.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                if (o.has("accent")) accent = o.get("accent").getAsInt() & 0xFFFFFF;
            }
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("accent", accent);
            Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(o));
        } catch (Exception ignored) {
        }
    }
}
