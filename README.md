# Red Texture Menu (Fabric, Minecraft 1.21.11)

Client-Mod mit grossem Texture-Browser.

- `/redmenu` oeffnet das Menue
- Packs (.zip oder entpackte Ordner) in `.minecraft/redclient/packs/` kopieren, dann "Neu laden"
- Tab **Textures**: Kategorien links, Suche oben, Raster mit weichem Scrollen, rechts die Liste der Auswahl.
  Textur anklicken = auswaehlen / abwaehlen. **Anwenden** schreibt das Pack `resourcepacks/RedMenuSelection`,
  **Fertig** schliesst das Menue, aktiviert das Pack ganz oben und laedt alle Resource Packs neu.
- Tab **HUD**: Menuefarbe, Hintergrund-Textur, Deckkraft, Smooth Scrolling, Motion Blur, Sounds
- Einstellungen werden in `config/redtextures.json` gespeichert

## Bauen
Repo auf GitHub hochladen -> Tab "Actions" -> Workflow "build" -> Artifact `redtextures` herunterladen,
die `.jar` (nicht `-sources`) in den `mods`-Ordner legen. Benoetigt Fabric Loader + Fabric API fuer 1.21.11.
