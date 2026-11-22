# Red Texture Menu (Fabric, Minecraft 1.21.11)

Client-Mod mit grossem Texture-Browser.

- `/red menu` oeffnet das Menue
- Packs (.zip oder entpackte Ordner) in `.minecraft/redclient/packs/` kopieren, dann "Neu laden"
- Links Kategorien (Crystals, Swords, ...), oben Suche, Mitte Raster, rechts Farbe (Presets + Hex)
- Farbe wird in `config/redtextures.json` gespeichert

## Bauen
Repo auf GitHub hochladen -> Tab "Actions" -> Workflow "build" -> Artifact `redtextures` herunterladen,
die `.jar` (nicht `-sources`) in den `mods`-Ordner legen. Benoetigt Fabric Loader + Fabric API fuer 1.21.11.

Falls der Build bei den Versionen fehlschlaegt: Werte in `gradle.properties` mit https://fabricmc.net/develop abgleichen.
