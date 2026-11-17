# feat(1.20.x): Add keyframe-driven Model Morph with visual cube mapping

> **Scope:** 1.20.x only. Targets the 1.20.1 release build (one jar covering 1.20.1–1.20.4). 1.21.11 is explicitly out of scope.
> **Branch:** `feat/model-morph-1.20.x` · **Version:** 3.6.9 · **License:** LGPL-3.0-or-later

---

## Summary

- Adds an **optional Morph mode on the existing Model keyframe track**. No new track, no new editor page: a Model keyframe with Morph enabled controls only the interval up to the **next** Model keyframe.
- Inside that interval each mapped cube is interpolated as a rigid-ish transform — translation lerp, quaternion slerp, per-component scale lerp — so the model visibly grows into the next one instead of hard-cutting.
- Three mapping shapes are supported and **inferred automatically from selection counts**: `1:1`, `1:N` (clone / split) and `N:1` (full convergence). `N:N` is rejected with an explicit error instead of guessing.
- Ships a **visual cube-mapping editor** plus an **auto-match** pass that proposes high-confidence `1:1` pairs from bone paths, cube names and bind-pose geometry.
- Source and target albedo are cross-faded on a 4× supersampled canvas in the **target UV layout**; vertices keep the target Cube's UV fixed throughout the morph.
- **Morph off (the default) keeps vanilla behavior**: no cube matching, no geometry generation, no texture work, unchanged rendering output.

## What's new

| Area | Change |
| --- | --- |
| Keyframe panel | Morph toggle, live mapping summary, texture-sync toggle, "Edit Cube Mapping" and "Auto Match" buttons, added to the native Model keyframe panel |
| Mapping editor | `UICubeMappingEditorPanel`: full-screen BBS Dashboard page with source/target cube lists, mapping groups, create/delete/clear, search, auto-match, preview progress, allow-incomplete toggle |
| Mapping model | `MappingGroup` holds a source set and a target set; the type is derived from the two list sizes, never chosen by the user |
| Geometry | `MutationCube` precomputes one source→target relation per edge (endpoint frames, per-face corner identity, UVs, winding) and only interpolates at playback |
| Textures | `MorphTextures` blends source/target albedo with a quantized, cached texture |
| Persistence | Morph config is a subtree inside the keyframe's own data (`bbsppp_model_morph`), so save, undo snapshots and copy-paste work without extra history code |

## Design

### Hooks into the existing Model track (no new track)

BBS's Model channel stores a model-id string and its keyframe factory is stepped in practice, so between two keyframes the model value does not change and the switch happens at the next keyframe. The code checks `factory.isStepped()` explicitly and falls back to the keyframe's own interpolation curve when a factory is not stepped. Model Morph reuses exactly that structure:

- a Model keyframe's Morph flag ON means "animate from this keyframe's model to the next Model keyframe's model";
- the switch point itself is untouched — BBS still performs its own stepped change at the next keyframe;
- only the *inside* of the interval is replaced: mapped source cubes emit generated geometry instead of their original quads.

Both ends are real models: at progress `0` the state is inactive (`progress > 0F` requirement) so the **real source model** renders, and at the next keyframe the model value becomes the target, so `beginRender` sees a model-id mismatch and steps aside, letting the **real target model** render. Generated geometry therefore exists only in the open interval `0 < p < 1`.

### Piggybacks on the keyframe for serialization

Same technique as the existing Texture Tween feature: an interface on the keyframe (`IModelMorphKeyframe`) plus five injections on `Keyframe` — `toData`, `fromData`, `copy`, `copyOverExtra`, `equals`. Consequences:

- legacy films (no subtree) load with Morph off and are byte-identical on save;
- `equals` participation makes undo snapshots notice mapping edits, not just model-id edits;
- copy-paste of keyframes carries the mapping.

### Per-point evaluation, no accumulated state

```
raw = segment.getInterpolationProgress()
p   = clamp01(raw)                                      // model factory is stepped
      /* or clamp01(keyframe.getInterpolation().interpolate(0, 1, raw)) for a non-stepped factory */
```

Nothing is carried between frames, so scrubbing, reverse playback, looping and timeline jumps are correct by construction rather than by bookkeeping. `FormProperties.apply` clears all per-form morph state every frame to prevent stale geometry after keyframe deletion or leaving the interval.

### Geometry solve

- Units: model pixels → blocks via `1/16`; the skeleton root frame carries the same `rotateY(π)` half-turn convention BBS uses.
- Endpoints are decomposed into **TRS** and interpolated as such — never element-wise matrix lerp, which would produce non-rigid "ghost scaling" mid-transition.
- Cross-bone rebinding (source cube and target cube usually live on different bones):
  `inverse(sourceBoneRestFrame) * targetBoneRestFrame * targetCubePivotFrame`, with the per-bone inverse cached.
- Faces are matched by **canonical corner identity bits** (sign of each axis relative to the cube centre), not by vertex array order — so differing vertex order, reversed winding and partially present faces all work. A face present on only one side collapses to that cube's centre.
- Mirrored / zero-scale / shear / singular / non-finite cases **fall back to per-vertex linear interpolation** instead of forcing an unsafe inverse. Safeguards: `|det| < 1e-8` and non-finite rejected before inverting; `< 1e-6` scale and `> 1e-4` shear rejected on decompose; mirrored basis keeps its sign on X (matching BBS `Transform.fromMatrix`).
- Normals go through a proper normal matrix (`inverse(RS)ᵀ`) with a degenerate fallback, then are renormalized with a fallback.

### Rendering lifecycle

| Injection point | Role |
| --- | --- |
| `PropertyTrack#apply` (HEAD) | read the current segment and write the per-form morph state (Model tracks only) |
| `FormProperties#apply` (HEAD) | clear all morph states every frame |
| `ModelForm` | owns the `ModelMorphState` field (mixin-implemented interface) |
| `ModelFormRenderer` | scopes the current form before BBS selects the VAO shader |
| `ModelInstance#render` (HEAD/RETURN) | push/pop the render context; bind and restore the blended texture |
| `ModelInstance#isVAORendered` (RETURN) | force VAO off **only while a morph frame is active** (VAO is baked at load time) |
| `CubicCubeRenderer#renderCube` (HEAD, cancel) | emit the generated vertices per face/quadrant, honouring the face mask and recorded winding |
| `TextureManager#delete` / `#accept` | invalidate plan + texture caches on F6 reload / file-watcher events |

Nothing is ever written into shared `Model` / `ModelCube` assets: intermediate vertices live in per-form `float[]` buffers, and the context is a stack so nested renders pair correctly. Welded models (`getWeldBindings()` non-empty) fall back to the normal path in v1.

### Multi-instance isolation

`ModelMorphState` is attached to the `ModelForm` instance, not to the shared `ModelInstance`/model asset. A renderer scope identifies the exact form before BBS chooses a shader; sampling buffers remain per form instance.

### 1.20.x-only layout

- New code lives in `src/main/java-1.20.x/.../client/morph/`, `.../keyframes/IModelMorphKeyframe.java` and `.../mixin/morph/`.
- The 10 morph mixin/accessor classes are registered in the shared `bbsplusplus.mixins.json` for the 1.20.x target. 1.21.x is outside this change's scope.

## Performance notes

- `MorphPlanCache`: plan computed once per `(source model, target model, mapping hash)` with an LRU cap of 32; **no cube matching per frame**.
- Sampling buffers are per form instance and reused; sampling is skipped entirely when the progress value is unchanged.
- Texture blending is quantized to 32 steps, cached 8 entries, and reuses the same `Texture` object (pixel update instead of re-creation). No blended texture is produced at either endpoint.
- VAO rendering is restored the moment the morph frame ends, so the CPU vertex path is not paid outside the interval.
- Morph off: the hooks do only constant-time guards (context null check / state inactive check). No plan build, no geometry work, no texture work, and the rendering output is unchanged.
- Caches are invalidated on texture/model reload (F6, file watcher), so edited assets are not served from stale plans.

## Scope & limitations

- **1.20.1–1.20.4 only.** 1.21.11 is out of scope.
- **LabPBR `_n` / `_s` companion textures are not blended** in v1; only albedo cross-fades.
- **Welded models** fall back to the normal path (v1).
- **OBJ / mesh models unsupported**: both endpoint models must be cube-only.
- **`N:N` mappings are rejected**; split them into `1:N` + `N:1` groups.
- **Unmapped target cubes do not appear during the transition** (they only arrive with the target model at the next keyframe), so the recommendation is to complete the mapping.
- **Mapping editor is selection-and-action driven**: pairing uses the selected source/target cubes and the BBS action bar; drag-and-drop pairing is not implemented. The editor has Ctrl+Z/Y and right-click mapping presets, but no per-material/UV/face mapping or explicit camera-reset button. Preview camera is refit when the model pair changes and preserved when progress crosses the 100% endpoint; tree expansion/search are not persisted after closing.
- **Overlap handling is honest, not magical**: clone ghosts get a staggered start (0.12 total) and convergence duplicates are suppressed after 98% progress; identical coplanar surfaces with stable draw order are deterministic under `GL_LEQUAL`, but near-identical surfaces can still z-fight inside a narrow progress band. Advanced fusion (Spatial Assembly / Primary Merge) is **deferred**.
- Progress is linear inside the segment because the Model keyframe factory is stepped; keyframe easing curves are only honoured for non-stepped factories.
- `allowIncomplete` defaults to `true` (morph plays with warnings rather than falling back to a hard cut).

## Testing

### Unit tests

- 16 test classes / **128 tests** across `src/test/java/gbeic/bbsplusplus/client/morph/`, `client/ui/morph/`, and `mixin/morph/`.
- **Result: 128/128 passed, 0 failures** via `bash gradlew test --offline` (JUnit Jupiter 5.11.4).
- The test runtime includes the BBS 2.7 jar and JOML (`testImplementation files(mcVer.bbs_jar)` and `org.joml:joml:1.10.5`), so it runs without manual classpath assembly.
- Not covered: serialization round-trip (`toData`/`fromData` need `MapType`) and end-to-end `MutationCube` geometry (needs `ModelCube`).

### Build verification

- `bash gradlew test --offline` also compiles the 1.20.1 main source. `bash gradlew compileJava -Pmc_version=1.20.4 --offline` is the compatibility regression target.
- `bash gradlew build1201 --offline` produces the single 1.20.1–1.20.4 jar.
- The 22 Model Morph `@Inject` points use `require = 1`, so a missing injection target fails loudly instead of silently disabling the feature.
- **In-game (runtime, not visual):** playing a Bed → Boat test film logs `ready: 6 mapped source cube(s), 9 transition(s)` — mapping resolution, cross-bone rebinding and transition-geometry construction all succeed — with zero texture-blend failures and zero `NullPointerException`s. Five real defects were found and fixed this way. First round: `Texture.uploadTexture(Pixels)` taking ownership of (and deleting) the reused blend buffer (one NPE per frame); an unplayable mapping rebuilding its skeleton and re-logging every frame (now negatively cached); and a `clear()` that discarded the sampling buffers each frame, defeating the documented buffer reuse (state now separates transient fields from cached geometry). A `@Shadow` visibility mismatch on `CubicCubeRenderer.captureOnly` was corrected at the same time.

Second round, after a screenshot showed the morphed model rendering as red/black shards: an offline probe first proved the geometry is exact (per-vertex error 0.000000 at both endpoints for identical, moved+resized, pivot-rotated and cross-bone cases), which isolated the problem to texturing. A GPU read-back probe then exposed two more bugs — the blend texture was uploaded while *unbound*, so it kept uninitialized VRAM (the red/black noise), and alpha transitions were being treated as cutout. Alpha handling changed again later; the current behavior is documented in the twelfth-round note below. Clone visibility was also tightened so coincident clones stop z-fighting at the start of an interval.

Third round, from user feedback: the texture blend returned `null` at the very end of an interval, so the last frame kept the *source* texture while the geometry had already reached the target shape (a one-frame flash of the source model) — endpoints now return the real texture for their side. UV is now **fixed for the whole interval**, matching how UV behaves under ordinary bone animation: a UV coordinate only says which atlas region to sample, so scaling or reshaping geometry must never move it. Interpolating UVs between two unrelated atlas layouts swept the sampling window across unrelated texture content ("uncontrolled UV drift"). Content transition is instead done on the texture side: each face's target region is remapped into its source region before cross-fading, so the sampling window stays put while the content still converges. There is deliberately no toggle for this — an earlier `uvLerp` option was removed because allowing UV motion is simply the wrong model for cross-model morphing.

Fourth round, from user feedback: the morph geometry was leaking into BBS's stencil picking pass, so moving the mouse (which redraws that pass) changed the morphed geometry's lighting; picking now always uses the real geometry. And because UV is fixed to the source region, the end of the interval can no longer return the raw target atlas — that sampled the target atlas with source UVs and produced badly wrong texture placement on the last frame. The remapped target content is now used for the whole interval, including the endpoint, which makes "fixed UV" and "target content at progress 1" self-consistent.

Fifth round: `isVAORendered()` also drives BBS's shader choice and is queried before `ModelInstance.render`, so deciding it from the render stack (still empty at that point) made shader and geometry take different paths and the lighting jittered with render order. Sixth round, and a design correction: the blur was not an endpoint problem — the texture was unclear for the *whole* transition. Squeezing target content into the source UV rectangle stretches it (measured: bed `head/front` is 16×16 while boat `front/front` content is 16×6, a 2.67× vertical stretch), and that stretched canvas participates in every blended frame. These were intermediate experiments; the final target-driven UV/canvas behavior is summarized at the end of this history.

Eighth round: lighting still jittered with mouse movement because `isMorphing()` read `ModelInstance.form`, a mutable public field on a *shared* per-model resource that BBS's renderer rewrites every frame. Render scope and texture binding moved to thread-local state. The current code also scopes the exact `ModelFormRenderer` form before BBS selects its VAO shader, so another form using the same model id does not force the CPU shader path. Separately, the first few frames after a source keyframe showed badly wrong textures: progress quantised to step 0 returned the *raw source atlas* while the transition geometry samples with *target UVs*. The atlas layout must always match the UVs in use, so the source atlas is never bound during an interval; the canvas with source content remapped into target regions is used from the very first frame, which equals the source model's look at progress 0.

Ninth round: the first frame after a source keyframe still popped. Two causes — clone visibility used a fixed progress threshold, so clones switched from "not drawn" to "drawn" on a single tick, and source content was squeezed into a *smaller* target UV rect (measured 16×16 → 16×6), losing vertical detail irreversibly, so even though the geometry was back to the source shape the image was visibly softer. Visibility now keys off geometric displacement (which grows continuously from zero, crossing a sub-pixel threshold), the clone stagger span dropped from 0.35 to 0.12, and the blend canvas is stored at 4× supersampling — measured endpoint fidelity against the real source model went from 17.0/255 to 0.0/255 mean channel error. A stale model name in the mapping is also no longer an error that blocks playback; mappings key off stable cube identities and geometry signatures, so it is now a warning and the runtime keeps morphing.

Tenth round: after replacing a model the panel only showed `source cube not found: front:#0` (leftover bone names from the previous model) plus a redundant `empty or invalid mapping group <uuid>`, which is not actionable. When an entire side fails to resolve the errors are now folded into one message that says the model was probably replaced and the mapping should be cleared and re-created; a single bad key still reports precisely, so partial mistakes remain easy to locate.

Eleventh round: a film looked abrupt when cross-fading. Unpacking it showed the mapping data itself was wrong, not the blend — the keyframe recorded `target_model: "cube"` (a directory holding only `config.json`, no model and no texture) while the interval actually ended on `model_morph_test/cube_test`. Morph geometry grew into A's shape and sampled A's atlas, then hard-switched to B. Validation now compares the recorded morph target against the model the interval ends on and emits a warning (it does not block playback); the next keyframe's value is threaded through the runtime, the plan cache and the editor summary so the problem surfaces before playback.

Twelfth round, one attempted fix for the visible cross-fade pop: the two atlases have very different alpha coverage (`cube_test` is fully opaque; `steve` is 60% fully transparent), and the then-current alpha path jumped across a cutout threshold. The current implementation instead smoothly interpolates alpha and synchronizes `Texture.translucent`, letting BBS's `FormTranslucentQueue` render intermediate values; `MorphAlphaTest` locks this behavior.

Thirteenth round, usability: cube names like `bone/cube_3` gave no clue which part of the model they refer to, so the editor now supports picking directly in the 3D preview. BBS's own stencil picking cannot do this — `StencilMap` is keyed by bone name and `CubicCubeRenderer` writes `group.index`, so its granularity is the bone, not the cube; it also swaps render state, which fights the morph vertex submission. The first CPU ray-cast prototype was superseded by a render-time screen projection cache: `MorphScreenPicks` records each Cube's projected faces, depth and full group path while the normal renderer draws it, so picking never rebuilds a second camera transform or touches GL state. Clicking a cube selects it in the matching tree and the selection is outlined in the preview.

Fourteenth round refines that: picking moved out of the morph preview (which exists to judge the transition) into two dedicated pickable viewports, one per side, with the dialog enlarged to 1000×660; the viewports show static models only, because a morphing silhouette makes clicking imprecise. It also turned out BBS's model editor cannot be reused for this — despite `setCubePicking`/`onPick`, its picking resolves to a `ModelGroup` (bone), not a cube, and it depends on gizmo editor state. Groups holding no cubes are now skipped entirely: on `player/steve`, 30 of 37 groups are empty, so the tree drops from 49 rows to 19.

### Current implementation notes from the full audit

- Geometry is target-driven: target Cube vertices and UVs remain the basis throughout; source atlas content is remapped into the target UV layout on the cached 4× canvas.
- `texture_sync` now gates runtime blending. When enabled, RGBA alpha is smoothly interpolated and the blend texture's `translucent` flag is synchronized for BBS's translucent queue.
- VAO decisions use a thread-local current `ModelFormRenderer` form scope before BBS writes its shared `ModelInstance.form` field. This keeps unrelated forms sharing a model id on their own render path.
- Shear/degenerate TRS fallback retains the original endpoint matrices; N:1 secondary slots stop drawing at convergence; screen-pick bounds include Cube Pivot rotation.
- Editor undo now includes `allowIncomplete`; invalid N:N attempts do not create undo entries; stale geometry signatures are highlighted; a missing source preview falls back to the target model; the endpoint preview uses the target model.
- The mapping editor now uses BBS 2.7's full-screen `UIDashboard` / `UIDashboardPanel` host and returns to the previous BBS menu when closed; picking checks projected Cube faces/depth, full group paths and the Cube's real group ordinal. Screen-pick caches are scoped to both the view and model.
- Its actions now use BBS's native icon action bar, and the pane tree uses BBS-FS's `UIDockLayout`/`EditorLayoutNode` implementation, including hover lines, linked intersection drags, double-click reset, minimum sizes, undo, and persistence.
- The page follows the BBS-FS-style workflow: the left column stacks the source preview above its structure tree, the middle column does the same for the target, and the right column holds the mapping creation card and relation list.
- The dock now has three top preview panels (source, target, morph) and three bottom panels (source tree, target tree, mapping groups); the morph panel loops the current transition at a fixed preview speed.
- Mapping edit mode hides the morph preview and progress footer entirely, so the mapping list and paired tree panes use the full available height.
- Morph preview mode also hides the footer controls and uses the full content area for the model view.
- A shared vertical splitter now controls the preview/tree boundary across the source and target columns; together with the two horizontal splitters, the center intersection resizes all four panes in both axes.
- The preview uses BBS's `UISliderTrackpad` and keeps its orbit camera when it switches to the exact target model at 100%; it refits only when the source/target pair changes. The incomplete-mapping toggle now explains how unmapped source and target cubes behave.
- Same-model remapping is supported: identical source/target model IDs no longer bypass runtime evaluation or plan construction, so cube reorganization can morph within one model asset.
- The keyframe editor now selects the next Model key at a later tick even when it references the same model; same-model intervals are no longer skipped while choosing the target.
- Mapping warning/error detail text uses the selected locale and derives unmapped counts from the validation result rather than parsing English strings.
- The full-screen Dashboard page uses BBS's native slider for preview progress and preserves the orbit camera across the target endpoint switch. Offline tests/builds pass; manual in-game slider, endpoint-view, incomplete-mapping, splitter, locale, and source-pick behavior still need confirmation.
- **Not verified:** the *visual quality* of every in-game scenario (Bed→Boat appearance, Player→Block convergence, clone stamping, scrub/reverse/loop, save-reload, undo, two-instance isolation, morph-off regression, video export parity). The pipeline runs and produces geometry, but whether it looks right needs human eyes. See the test matrix in `docs/MODEL_MORPH_1.20.x.md` §13.3.

### In-game manual verification

**None of the following has been performed.** They are listed so reviewers can tick them off, not as claims:

| # | Scenario |
| --- | --- |
| 1 | Bed → Boat basic morph, no pop at either endpoint |
| 2 | Player → Block convergence, UVs converge correctly |
| 3 | `1:N` clone/split — clones separate with the stagger, no long overlap |
| 4 | `N:1` full convergence — no double surface at the end |
| 5 | Timeline manual scrub back and forth |
| 6 | Reverse playback |
| 7 | Loop playback |
| 8 | Save film → reload, mapping and toggles restored |
| 9 | Undo / redo after editing a mapping |
| 10 | Copy-paste keyframes carrying morph config |
| 11 | Two playback instances sharing one model, only one morphing |
| 12 | Morph-off regression (pixel-identical to before) |
| 13 | Video export matches editor preview |
| 14 | Multi-select keyframes → batch mapping applies to all |
| 15 | Texture sync off (geometry only) |
| 16 | Mismatched atlas sizes (e.g. 64×64 → 128×128) |
| 17 | F6 resource reload invalidates cached plans and blended textures |
| 18 | OBJ-mesh model falls back to a hard cut without crashing |
| 19 | Welded-attachment model falls back without crashing |
| 20 | Conflicting mapping (same cube in two groups) reports a problem and falls back |

## Checklist

- [x] `bash gradlew build1201 --offline` compiles (1.20.1)
- [x] `bash gradlew compileJava -Pmc_version=1.20.4 --offline` compiles (regression target)
- [x] `bash gradlew test --offline` is green (128/128)
- [x] No new runtime dependencies: the only additions are `junit-jupiter:5.11.4` and `junit-platform-launcher:1.11.4` for tests
- [x] All 10 morph mixin/accessor classes are registered in the 1.20.x `bbsplusplus.mixins.json`
- [ ] LGPL-3.0-or-later compliance — confirm the repository `LICENSE` covers the new files, and that `LICENSES/` ships with the distributed jar
- [x] MIT attribution for the Bone Anchor-derived math guards: `LICENSES/MIT-bone-anchor.txt` contains the full MIT notice, and `MorphMath` credits the design in its class comment. (Optional follow-up: add `See LICENSES/MIT-bone-anchor.txt` to that comment.)
- [ ] Morph-off rendering parity spot-checked in game
- [ ] At least the four core in-game scenarios (Bed→Boat, Player→Block, scrub/reverse, save-reload) manually verified

> Ticked items were verified by actually running the commands shown. Nothing that requires playing the game has been ticked; the in-game scenarios remain unchecked on purpose.

---

[![](https://img.shields.io/badge/powered_by-dsh-4D6BFE?style=flat-square&logo=deepseek&logoColor=white)](https://github.com/deepseek-ai/deepseek-harness)
