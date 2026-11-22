package de.redclient.textures;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RedConfig {
    /** Akzentfarbe (RGB). */
    public static int accent = 0xE02020;
    /** 0 Schwarz, 1 Verlauf, 2 Raster, 3 Punkte, 4 Streifen. */
    public static int bgStyle = 0;
    /** Deckkraft des Menues in Prozent (40-100). */
    public static int opacity = 92;
    public static boolean smooth = true;
    public static boolean blur = true;
    public static boolean sounds = true;
    /** 1-5 */
    public static int blurStrength = 3;
    /** 1-5 */
    public static int scrollSpeed = 3;
    /** Ausgewaehlte Texturen als "pack|pfad". */
    public static final List<String> selected = new ArrayList<>();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("redtextures.json");
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            if (o.has("accent")) accent = o.get("accent").getAsInt() & 0xFFFFFF;
            if (o.has("bgStyle")) bgStyle = clamp(o.get("bgStyle").getAsInt(), 0, 4);
            if (o.has("opacity")) opacity = clamp(o.get("opacity").getAsInt(), 40, 100);
            if (o.has("smooth")) smooth = o.get("smooth").getAsBoolean();
            if (o.has("blur")) blur = o.get("blur").getAsBoolean();
            if (o.has("sounds")) sounds = o.get("sounds").getAsBoolean();
            if (o.has("blurStrength")) blurStrength = clamp(o.get("blurStrength").getAsInt(), 1, 5);
            if (o.has("scrollSpeed")) scrollSpeed = clamp(o.get("scrollSpeed").getAsInt(), 1, 5);
            if (o.has("selected")) {
                selected.clear();
                for (JsonElement e : o.getAsJsonArray("selected")) selected.add(e.getAsString());
            }
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("accent", accent);
            o.addProperty("bgStyle", bgStyle);
            o.addProperty("opacity", opacity);
            o.addProperty("smooth", smooth);
            o.addProperty("blur", blur);
            o.addProperty("sounds", sounds);
            o.addProperty("blurStrength", blurStrength);
            o.addProperty("scrollSpeed", scrollSpeed);
            JsonArray arr = new JsonArray();
            for (String s : selected) arr.add(s);
            o.add("selected", arr);
            Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(o));
        } catch (Exception ignored) {
        }
    }
}
