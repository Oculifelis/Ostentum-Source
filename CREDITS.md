# Ostentum: credits and licences

Copyright (C) 2026 Oculifelis.

Ostentum's code and Oculifelis's own art and music are licensed under the **GNU Lesser General Public License,
version 3 or (at your option) any later version** (`LGPL-3.0-or-later`). The LGPL text is in `LICENSE`. The GPL
text it builds on is in `COPYING`. Both files and this one ship inside the mod jar.

Third-party art keeps its own licence, listed below. The LGPL does not cover it.

## Oculifelis
Code, design, the music (the moon and siege tracks and their discs), and the hand-drawn art: the Phantom Veil,
the glaive head, the hearthblade fire and glow layers, the whips, the relic models and the Record Pendant. Some models reuse shapes from Oculifelis's own mods: the relic pedestal is from Ars Moriendi, and the
Weathervane Spear's lightning is drawn the way Ultima Ratio draws its lightning.

## Malcolm Riley: Unused Textures
- Licence: CC BY 4.0, https://creativecommons.org/licenses/by/4.0/
- Source: https://github.com/malcolmriley/unused-textures
- Used: `tool_greatsword_gold`, modified (recoloured and partly redrawn). It is the base of the Duelist's Saber, the
  Hearthblade, the Soul Hearthblade, the Cold Hearthblade, the Echo Blade and the Resonant Hilt.
- The modified textures stay under CC BY 4.0.

## Rhodox: Painterly Pack
- Licence: none public. Rhodox gave Oculifelis permission to use it in Oculifelis's own projects.
- Used: the ornate iron pick, recoloured, with new gems. It is the head of the Delver's Pick, the Dormant Delver's
  Pick and the Rusted Delver's Head, and the source of their glow layer.
- These textures are **not** LGPL. Nobody else may reuse them in another mod or pack.

## Mojang: Minecraft
- Many item and entity textures are recoloured or reworked from Minecraft's own: the tridents, bows, crossbows,
  spears, the Quake Hammer (mace), the Recall Chime (bell), the Frostbite Hatchet (diamond axe), the saddles and
  the music discs.
- Mojang keeps its rights to that art. The LGPL does not cover it.

## Shape references (no pixels copied)
- The Record Pendant's cord loop follows the shape of the necklaces in the Artifacts mod (MIT). Every pixel was
  placed by hand.

## Per-texture sources
`tools/weapons/CREDITS.md` lists each weapon texture with its source and licence. Unmodified donor files and their
licence texts are in `tools/third_party/`.

A byte-for-byte check against other mods' jars matched one file, `textures/item/glow_berry_whip_grip_glow.png`, to
Tinkers' Construct's `steel_cutlass_guard.png`. It has three orange pixels, drawn by Ostentum's own whip generator.
The match is a coincidence, not a copy.
