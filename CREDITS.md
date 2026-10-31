# Credits and third-party code

Mirabilia Hexica is MIT-licensed (see [LICENSE](LICENSE)). It adapts code from the projects below, also MIT-licensed;
their notices are reproduced here as
that license requires. Everything else in this repository was written for this mod.

| From | What | Where in this repo |
|---|---|---|
| [HexDummy](https://github.com/FallingColors/HexDummy) (Hex Casting addon template) | The registrar (collect entries in common code, register per loader), the `@ExpectPlatform` registry hook, the entrypoint layout, the dependency list in the version catalog | `registry/MirabiliaRegistrar.kt`, `MirabiliaAbstractions.kt` and its two `*Impl.kt`, `Fabric/ForgeMirabilia*.kt`, `gradle/libs.versions.toml` |
| [Hex Casting](https://github.com/FallingColors/HexMod) | How the Scrying Lens is drawn on the face in Curios/Trinkets slots (placement numbers and structure); the lens model's head transform; the shape of the string arithmetic, modelled on Hex's list arithmetic and its slice operator | `forge/.../interop/CuriosRenderers.kt`, `fabric/.../interop/LensTrinketRenderer.kt`, `models/item/greater_lens.json`, `casting/arithmetic/StringArithmetic.kt` |
| [Create](https://github.com/Creators-of-Create/Create) (via Hex Casting) | Hex's lens renderers are themselves adapted from Create's goggles Curio renderer, so the face placement above descends from it | same two renderer files |

Elsewhere the mod builds on Hex Casting's public API (actions, iotas, casting environments, mishaps), as every
addon does, and follows its conventions, but doesn't copy its code.

## What the licence covers

Everything in this repository is under the MIT licence in [LICENSE](LICENSE): the code, every texture (all drawn by
the scripts in `tools/`), the icon, the guidebook and other texts, and the English and Russian translations. The
only third-party pieces are the adapted code in the table above, under its own MIT notices below.

What the mod uses but doesn't include keeps its own licence: Minecraft and its assets (the Lost Dreamer, for
instance, is drawn over the game's own zombie villager skin at run time; nothing of Mojang's is copied here), Hex
Casting, Architectury API, Fabric API, Fabric Language Kotlin, Kotlin for Forge, Patchouli, Trinkets and Curios,
which the mod depends on but doesn't bundle.

## Inspirations, not copied

- **Ra** by qntm. Tanako's World, the Glass Man, the wheel of stars and the idea that magic leaves something behind
  come from the novel. Its names are used as a tribute; no text from it is reproduced, and every line in the mod
  was written fresh. This is a fan project, not affiliated with qntm. Go read it: <https://qntm.org/ra>.
- **Hex Casting's Russian translation**, whose terms (мысль, заговор, руна, провал...) the Russian translation
  follows so the two read as one book. Terms only; no sentences are taken from it.
- **Hexal**, whose pattern list was read only to make sure no pattern of ours clashes with one of its.

This mod was written with heavy help from an AI coding assistant (Claude), directed and reviewed by its author.

---

## HexDummy

MIT License

Copyright (c) 2025 artynova, Falling Colors

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
documentation files (the "Software"), to deal in the Software without restriction, including without limitation
the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of
the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## Hex Casting

The MIT License (MIT)

Copyright © 2021 gamma-delta

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
documentation files (the “Software”), to deal in the Software without restriction, including without limitation
the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of
the Software.

THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## Create (code only; Create's assets are all rights reserved and none are used here)

MIT License

Copyright (c) The Create Team / The Creators of Create

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
documentation files (the "Software"), to deal in the Software without restriction, including without limitation
the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of
the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
