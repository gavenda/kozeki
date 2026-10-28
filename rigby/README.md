# rigby

A Kotlin port of the [Rigby2D](https://github.com/jaisurya-dev-art/rigby2d) runtime, which plays
Spine 3.8 skeleton animation (the format Blue Archive's characters ship in) on an Android `Canvas`.
It is ported from the copy vendored in plana (`src/lib/rigby`, Rigby2D commit `af53bfc`), Plana
patches included, so it plays rigs the way plana's live portraits do. Rigby2D is dual-licensed MIT
or Apache-2.0 (`LICENSE-MIT`, `LICENSE-APACHE`), and so is this port. No Spine runtime code is used.

## Using it

The runtime reads skeleton **JSON**. Convert a `.skel` with plana's script first:

```sh
bun ../plana/scripts/spine38_to_json.mjs branding/CH0169_spr/CH0169_spr.skel ui.json
```

```kotlin
val data = SkeletonJson.read(jsonText, "ui")
val atlas = Atlas.parse(atlasText)
val pose = Pose(data)
val state = AnimationState(data)
state.setAnimation(0, "Idle_01", looping = true)
state.setAnimation(1, "01", looping = true)

// The pages can be shrunk to about the size they are drawn at (Canvas sampling has no mipmaps).
val zoom = heightPx / data.bounds!![3]
val textures = AtlasTextures(atlas, mapOf(atlas.pages[0].name to pageBitmap), (zoom / atlas.pixelsPerUnit(pose)).toFloat())
val renderer = SkeletonRenderer()

// Every frame:
state.update(dt)
state.apply(pose) // resets to setup, applies the tracks, updates world transforms
renderer.draw(canvas, pose, textures, Camera(x = left, y = top, zoom = zoom)) // world Y is up
```

`pose.local` can be changed between `apply` and a second `pose.updateWorld()` to move bones by hand,
as plana does to tilt Plana's head towards the pointer. A one-shot animation (a blink) holds its last
frame until its track is cleared: clear it from an `AnimationStateListener.complete`.

## What is ported

Bones and inherit modes, slots with two-color tint and blend modes, skins, region, mesh, linked
mesh, clipping, path and point attachments, mesh deform, draw order, events, IK, transform and path
constraints, crossfades and layered tracks. Not ported: the editor half of Rigby2D (writing files,
undo, the pose library), Spine 4.x reading, and the 4.x physics, slider and sequence features, which
3.8 rigs don't have.

## Checking it against Rigby2D

`ParityTest` plays a scripted session on rigs and compares every bone, slot and world vertex with
what Rigby2D's JavaScript runtime gives. The rigs are game assets, so they are not in the repo:
point the test at a folder of `<rig>.json` and `<rig>.expected.json` pairs. `tools/parity.mjs`
makes the expected files with plana's vendored runtime:

```sh
bun rigby/tools/parity.mjs ../plana/src/lib/rigby/core dir/ui.json dir/ui.expected.json
./gradlew :rigby:testDebugUnitTest -Prigby.parity=dir
```

On 2026-10-29 it matched exactly (within 1e-11) on Ui's rig, plana's, four `_spr` character rigs and
two lobby rigs with clipping and path constraints.
