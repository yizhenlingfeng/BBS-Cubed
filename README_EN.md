# BBS Cubed

> An enhancement plugin for [BBSFS](https://github.com/Wemppy4/bbs-fs)

**[English]** | [简体中文](README.md)

<div>
  <img src="docs/AAA.png" width="1000">
</div>

## Feature Overview

### Configurable Options

| Feature                      | Default | Description                                                                                                                                                                                                                    |
| ---------------------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **BBS Enhancements**         |         |                                                                                                                                                                                                                                |
| Chinese keyframe track names | Off     | Displays track names in the keyframe editor in Chinese. Translations can be added/modified via the language file `bbspp.keyframe.*`, with a hardcoded table as fallback                                                        |
| Auto-switch to spectator in editor | Off | Automatically switches to spectator mode when opening the movie editor, and restores the previous mode when closing.                                                                                                           |
| Disallow negative keyframes  | Off     | When enabled, prevents dragging or adding keyframes before 0 seconds in the keyframe editor.                                                                                                                                   |
| Shift directly selects parent | Off    | When enabled, holding Shift and clicking a model part in the "playback editor" or "model editor" no longer pops up the hierarchy selection menu — it directly selects the parent bone.                                          |
| Pose bone tree list          | Off     | When enabled, the bone list in the pose editor is indented by the model's parent-child hierarchy with tree connector lines; search results are still shown as a flat list of matches                                              |
| First-person view sway       | Off     | When enabled, restores the original walking view sway effect while playing `first-person playback`.                                                                                                                             |
| New disguise interface layout | Off    | When enabled, the disguise interface uses a brand-new two-column layout. When disabled, it uses the original vertical layout.                                                                                                   |
| New movie library interface  | Off     | When enabled, the movie selection interface uses the new movie library layout and interaction.                                                                                                                                  |
| Reverse timeline scroll direction | Off | When enabled, reverses the direction of the timeline driven by the scroll wheel in the movie editor, including Ctrl+scroll to move the playhead, Alt+scroll to move selected keyframes/clips, and Alt+scroll to scroll the timeline left/right. |
| Shader control button        | Off     | Adds a button in the `movie editor`: left-click toggles on/off, right-click opens the `shader selection interface`.                                                                                                            |
| New shader curve selection interface | Off | When enabled, adding shader curves in curve clips uses a new interface categorized by shader settings. When disabled, it uses the BBS vanilla flat list.                                                                       |
| Keyframe layout deep lock    | Off     | When enabled, if the `keyframe editor` layout is locked, the resize handle between the left track names and the right tracks is also hidden, and the width can no longer be adjusted.                                           |
| World playback applies shader curves | Off | When enabled, while playing movies in-world with right Ctrl, the sun, weather, sky color, and Iris shader pack parameters in curve clips also take effect.                                                                   |
| Allow dragging to extend clip tracks | Off | When enabled, dragging an entire clip beyond the top of the timeline no longer restricts the track hierarchy, allowing more tracks to be created like vanilla BBS.                                                            |
| Use BBS Cubed dedicated clipboard | Off  | When enabled, BBS-specific copy data (playback, keyframes, shapes, transforms, etc.) is stored inside BBS Cubed instead of being written to the system clipboard.                                                               |
| Alt+scroll timeline behavior | Default  | Sets the behavior of Alt+scroll wheel in the movie editor's keyframe view when no keyframe is selected.                                                                                                                         |
| **Item Spray**               |         |                                                                                                                                                                                                                                |
| Skip rendering off-screen    | On      | When enabled, item spray particles skip item rendering once they leave the player's view, reducing frame rate pressure with large numbers of particles.                                                                         |
| Max render distance          | 0       | Item spray particles beyond this distance skip rendering. Set to 0 to automatically follow the client's render distance.                                                                                                       |
| Max render count per frame   | 1024    | Limits how many item spray particles can be drawn per frame. Set to 0 for no limit.                                                                                                                                             |
| IRL shadow max item count    | 1024    | When IRLights light shadows are enabled, limits how many item spray particles participate in projection. Set to 0 to disable item spray projection.                                                                             |
| **Gizmo Rework**             |         |                                                                                                                                                                                                                                |
| Blockbench-flavored Gizmo    | Off     | When enabled, the Gizmo display mode defaults to translate; G/S/R only switch the Gizmo display mode (no editing), and the T key cycles only through translate, scale, and rotate.                                               |
| T key cycle includes combined mode | Off | Makes the T key cycle include the combined Gizmo. Only takes effect when the option above is enabled.                                                                                                                           |
| Keep original hotkey behavior | Off    | Pressing G/S/R a second time does not restore the mode; instead it executes the original hotkey function. Only takes effect when the option above is enabled.                                                                   |

### AAA Particles

> Prerequisite: requires `aaa_particles-2.2.0` + `architectury` installed; the feature auto-disables if they are not installed.

- Adds AAA particle disguise; particle files must be placed in the `assets/effeks/` directory.
- New command: `/bbsplusplus aaa_particle trigger <x> <y> <z> <trigger ID>`
  - Example: `/bbsplusplus aaa_particle trigger 100 64 200 0` (triggers "trigger 0" of the model block particle at the specified coordinates).
- Known limitations and notes:
  - The feature has passed basic stability testing (no crashes). Non-crash bugs may no longer be maintained (the current version already satisfies personal use needs).
  - If the game crashes after loading particles: try re-exporting with a newer version of `Effekseer`; if it still crashes, I'm out of ideas (:

### Item Spray

<div>
  <img src="docs/img.png" width="1000">
</div>

- Adds an item spray disguise, with a wide selection of sprayable items. Adjustable parameters include:
  - Item, count, frequency, lifetime, range, radius, speed, speed spread, position spread, gravity, collision, gravity speed, real-time mode, simulation time, random seed, always face camera, item pitch, item yaw, item roll, X/Y/Z axis rotation speed, random rotation speed, scale, scale spread, show guide lines, color, emitter shape

### Pose Frame Enhancements

- Added multi-select paste for `pose` frames.
- Added a `bone parameter brush` to copy bone parameters from one bone to another.
- Added a `modified bone indicator` — modified bones show an orange diamond indicator.
- Added `skip current frame bone key value` — hovering over the right edge of a bone name reveals a diamond; click to toggle. When skipped, the bone keeps its parameters for the current frame, but animation calculation ignores this key value and interpolates directly between the valid pose frames before and after; a gray slanted diamond indicates a skipped frame.
- Ported the `tree bone` feature from FS2.5 with optimized display; optional, off by default.

### Structure Disguise

> Merged from the `tools` plugin by `卫巾纸薄`.

- Adds structure sticks and structure disguise.
- Future ++ releases will improve: operation optimization, biomes.

### Video Disguise

> Prerequisite: requires `MediaPlayer-BBS` installed; the feature auto-disables if it is not installed. Only supports **Windows x64** platforms!

- Video files go into the `<game directory>/config/bbs/assets/video/` folder (supports .mp4/.mov/.mkv/.webm/.avi/.m4v). The folder can be opened from the "Select Video" panel in the disguise editor.
- Compatibility notes: `MediaPlayer-BBS 1.0.1`'s native timeline frame-seeking interface has a crash defect (access violation on some machines). BBS Cubed automatically disables its timeline frame-seeking and falls back to the continuous decoding path; `MediaPlayer-BBS 1.0.2+` (fixed build) automatically enables timeline frame-seeking for frame-accurate positioning when dragging the playhead. **When publishing the fixed build, change the version number to 1.0.2 or higher.**
- More improvements to come.

### Texture Tweening

<div>
  <img src="docs/img_1.png" width="1000">
</div>

- Adds texture tweening for smooth transitions between texture keyframes, with three tween modes:
  - **Color**: smoothly interpolates colors between two textures by progress.
  - **Pixel dissolve**: pixels dissolve randomly in blocks, transitioning to the next texture.
  - **Dissipate**: dissipates block by block outward from the spread origin.
- Dissipate mode can be paired with `dissipate reverse` (gradually recover from transparent) and `dissipate flash` (show a flash color before becoming transparent, with custom colors and LabPBR emission support).
- Supports adjusting pixel block size, spread strength, flash color, PBR emission brightness, and more.
- New 3D `spread origin picker`: right-click directly on the model surface to pick the dissipate origin; left-click to rotate, middle-click to pan, Ctrl+scroll to zoom, with X/Y/Z numeric fine-tuning.
- Compatible with old BBSPPP texture tween keyframe data; auto-migrates.

### CML Features (merged from bbs_snow)

> All features of the original `bbs_snow` plugin have been merged into BBS Cubed. The mod ID is unified as `bbsplusplus` with the resource namespace `bbspp`. After installing this mod, there is no need to install bbs_snow separately.

- **Fluid simulation**: new fluid disguise with fluid physics simulation rendering.
- **Particle Plus**: enhanced particle system — particle morphing (Morph), collision appearance and tinting, favorites browser, additional render textures, Additive materials.
- **PBR texture tiering**: per-bone/per-group PBR parameter overrides (smoothness, metalness, porosity, emission, normal intensity).
- **Bone textures**: assign independent textures per bone.
- **Premiere export**: export movies as Premiere Pro XML project files, with SRT subtitle files generated alongside.
- **Three clip types**: hotbar clips, movie clips, and replay clips, enriching the timeline.
- **Action overlay**: each model form can configure action overlay layers (`actions_overlay`) with extra action slots, blended with the base actions during playback.
- **Additive animation**: overlay animation support with multi-action-layer blending.
- **Molang variable sharing**: interop toggle for variable action models; when enabled, Molang variables are shared across different actions.
- **Animation state editor**: animation state editing with an enhanced layout, supporting pose insertion.
- **Mini windows**: dockable mini window system (movie panel, dashboard, etc.), supporting dragging and layout memory.
- **Playback time control**: replay clips support speed adjustment, reverse playback, and propagation range settings.
- **Clip visibility**: clip visibility control to temporarily hide specific clips.
- **Keyframe editor enhancements**: split view, embedded inspector, action timeline evaluation, PBR keyframe factory.
- **CML settings panel**: independent settings module with categories including General, Appearance, Editor, Display, Fluid Simulation, Model Block, Pose Track Selection, Replay Editor, and more.

### Vanilla Enhancements

| Feature                       | Description                                                                                                                       |
| ----------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| Animation-to-pose search box  | Added an animation search box to the animation-to-pose window.                                                                    |
| Sound selector tree list      | The sound selector interface displays in a tree structure for easier browsing.                                                    |
| Double-click clip to edit (NYK) | Double-click a clip on the timeline to enter the edit interface.                                                                 |
| Glowing chroma block (NYK)    | Added a glowing chroma block.                                                                                                     |
| IRLights plugin localization  | Localization of the IRLights plugin.                                                                                              |
| Improved texture manager      | Added grid mode and thumbnails.                                                                                                   |
| vfx plugin localization & enhancements | Localization and enhancement of the vfx plugin.                                                                           |
| Playback category operations  | Added multi-select drag and category renaming.                                                                                    |
| Undo history panel improvement | The undo history panel shows more readable operation summaries and a widened history popup.                                       |
| Shader curve selection UI improvements | Hmm...                                                                                                                        |
| Block ctrl+B recap feature    | Nonsensical Mojang.                                                                                                                |
| Complete vanilla image disguise | Completed UV offset support.                                                                                                     |
| Shader curve track name optimization | Merged from the tools plugin.                                                                                                    |
| Sun/moon declination & focus curve keyframes | Merged from the tools plugin.                                                                                            |
| ItemStack track improvements  | Added transform functionality.                                                                                                    |
| Auto-save presets             | Model block presets gain an "auto-save" toggle: after enabling it via the preset right-click menu on the IK chain, physics bones, bone constraints, and pose pages, parameter changes are automatically written to the currently selected preset with 500ms debounce — no manual save needed. |

### Vanilla Fixes

| Feature                         | Description                                                                                                                                                    |
| ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Input method conflict fix       | Compatible with IMBlocker; fixes the issue of being unable to switch to Chinese input in BBS interfaces.                                                       |
| Curve crash fix                 | Fixed the crash when enabling fly mode in BBS 2.2 curve clips.                                                                                                 |
| Fly mode curve fix (NYK)        | Curve keyframe modifications still take effect after enabling fly mode.                                                                                        |
| Timeline ruler occlusion fix    | Fixed the issue where the timeline ruler occlusion still allowed keyframes/clips to be clicked.                                                                 |
| Clip dragging fix               | Fixed the issue where dragging clip segments in BBSFS sometimes snapped back or got stuck.                                                                      |
| Playhead offset fix             | Fixed wemppy's projectile trajectory veering left.                                                                                                              |
| Model texture error fix         | Fixed the issue where BBS might use `_S`-suffixed textures as default model textures when the texture name differs from the model name.                         |
| Color inflation fix             | Hmm......                                                                                                                                                      |
| Fixed shader curves             | Merged from the tools plugin, and fixed the issues present in tools.                                                                                            |
| Block BBS F10 blackboard feature | Hmm.........                                                                                                                                                  |
| Mute issue fix                  | BBS would go silent when switching audio output devices.                                                                                                       |
| Keyframe color bar break fix    | Keyframe color bars for the same parameter would break apart in BBS.                                                                                            |
| Blockbench stepped animation fix | Fixed Blockbench-exported Bedrock animations where step keyframes were played as linear interpolation.                                                         |

## Open Source License

- This project is open source under **GNU LGPL-3.0-or-later**; the full license text can be found in [LICENSE](LICENSE).
- You are free to use, study, modify, and distribute this project; modified derivative works must also be released under LGPL-3.0 or later.
- Copyright (C) 一阵泠风, Gbeic, snowstar.
- This project is an extension plugin for [BBSFS](https://github.com/Wemppy4/bbs-fs), implemented via Mixin.

### Mod Changelog

#### 3.2.1

- Translation sync and fixes:
  - Added 605 missing English keyframe track translations to `bbspp/strings/en_us.json` (VFX effects, item spray, video disguise, lighting, etc.)
  - Fixed hardcoded Chinese "跟随轨道" in camera track mode menu; changed to `bbspp.ui.film.follow_orbit` translation key
  - Created `assets/xavin/lang/en_us.json` with English translation for the Destruction Wand
- Bug fixes:
  - Fixed keyframe track names still showing in Chinese when game language is English, even with the "Chinese Keyframe Track Names" toggle enabled
  - Root cause: keyframe tracks have two creation paths; the second path (`KeyframeTrackStyle.apply()`) uses `localizeWithMap()`, which did not check the current game language
  - Fix: added `isChineseLanguage()` check to both `localize()` and `localizeWithMap()`; non-Chinese languages force English track names
  - Language detection prioritizes `BBSModClient.getLanguageKey()` (BBS's own language setting), falling back to Minecraft `LanguageManager`
- Code cleanup:
  - Removed unused `IKey` import from `UIFilmControllerFollowOrbitMixin`

#### 3.2

- Disguise interface layout improvements:
  - Added a layout toggle button at the top right of the page, supporting switching between list mode and grid mode.
  - Hold Ctrl + scroll wheel to zoom the icon size in both list and grid modes across the entire page.
  - List mode supports side-by-side arrangement, with a minimum of 1 and a maximum of 10 models per row.
  - In grid mode, hovering over a model shows its name and model ID.
  - Dragging a model to a different category folder on the left cuts and moves the model file to the target folder.
- Bug fixes:
  - Fixed some crash issues.
  - Fixed Blockbench-exported Bedrock animations where step keyframes were played as linear interpolation.

#### 3.1.1

- Feature removal:
  - Removed **texture_tint** and **texture_whiten** from the pose editor; deleted the related API interface `PoseTextureGradeEditorHolder`; trimmed ~200 lines of related code in `UIPoseEditorMixin` and `UIPoseKeyframeFactoryMixin`.
- Bug fixes:
  - Fixed OrbitViewGizmo being incorrectly hidden in vanilla track mode — the root cause was a false-positive risk in the `@Shadow controller` HEAD NPE guard added in 3.1 (controller is actually never null), which forced `isActive()` to return false. Fix: removed the guard and used `@Accessor` to safely read the target class field.
- Feature enhancements:
  - Follow track mode (mode 6) now supports a **perspective right-click menu** — it shows the same right-click options as vanilla tracks (teleport center to recording point, attach track, toggle orthographic). Previously only vanilla track mode (mode 2) had this menu.
- Project maintenance:
  - Cleaned up the unused nested resource directory `assets/bbs/assets/` (870 unreferenced translation keys + unreferenced textures, including broken JSON).
  - Cleaned up temporary debug files (684MB .hprof heap dump, issue screenshots, `_memcheck/`, `mchorse/` decompiled classes) and build outputs (`build/`, `.gradle/`, `run/`), freeing ~2GB in total.
  - Updated `.gitignore` with `*.hprof`, `issue_screenshot*.png`, `_memcheck/`, `mchorse/` rules to prevent regression.
  - Build verification: `gradlew compileJava` BUILD SUCCESSFUL.

#### 3.1

- Merged all contents of the `bbs_FSloveCML` (bbs_snow) plugin — uninstall bbs_FSloveCML!
  - Mod ID unified as `bbsplusplus`, resource namespace unified as `bbspp`, package name unified as `gbeic.bbsplusplus`.
  - Added fluid simulation disguise, Particle Plus (morph/collision tinting/favorites), PBR texture tiering (per-bone/per-group PBR overrides), bone textures.
  - Added Premiere Pro XML project export + SRT subtitle export.
  - Added hotbar/movie/replay three clip types, action overlay layers, Additive animation, Molang variable sharing.
  - Added animation state editor, dockable mini windows, playback time control (speed/reverse/propagation range), clip visibility.
  - Added keyframe editor enhancements (split view/embedded inspector/action timeline/PBR keyframes).
- Refactored the settings panel (6 categories):
  - `BBS Enhancements`: the original snow settings' first 4 toggles (Chinese track names/auto-spectate/no negative keyframes/Shift select parent) + the original BBS++ options.
  - `Enhanced UI` (new subcategory): new disguise interface layout, new movie library interface, new shader curve selection interface.
  - `Item Spray`, `Gizmo Rework`: unchanged.
  - `CML Extensions` (independent left menu, divider characters removed): follow track, bone texture tinting, and other CML-specific options.
  - `Export Enhancements` (renamed from premiere-export, independent left menu, divider characters removed): Premiere XML export + audio subtitle export settings.
  - The BBSCubed settings module displays entirely below the vanilla BBS settings.
- Feature improvements:
  - Follow track (mode 6) now supports vanilla track XYZ axis direction selection (the track view widget OrbitViewGizmo also activates in follow mode).
- Bug fixes:
  - ~~Fixed texture_tint and texture_whiten not taking effect on the pose page~~ (removed in 3.1.1).
  - ~~Fixed texture tint/whiten visible on the pose edit page but not showing after exiting~~ (removed in 3.1.1).
  - Fixed the overlap between the loop icon and the visibility icon at the top right of the movie editor — the loop icon offset was increased by one visibility button width.
  - ~~Fixed OrbitViewGizmo.isActive() null pointer crash (NPE when controller is null) — added a HEAD null guard to return false early~~ (3.1.1 found this guard had a false-positive bug that hid the vanilla track Gizmo; switched to a safe @Accessor read and removed the guard).
- Settings panel enhancements:
  - Added hover tooltips for the "Enhanced UI", "CML Extensions", and "Export Enhancements" categories.
  - The "CML Enhancements" category was renamed to "CML Extensions".
- Compile parameter adjustment: because Particle Plus uses `sun.misc.Unsafe` to inject enums, the compile method changed from `--release 17` to the equivalent `-source/-target 17 + -XDignore.symbol.file`.

#### 3.0

- Display name changed from BBS++ to BBS Cubed; version bumped to 3.0.
- This version is a collaboration between 一阵泠风, Gbeic, and snowstar.

#### 2.7.2

- Optimized preset auto-save performance:
  - Data snapshots are now built only after the debounce triggers; dragging sliders no longer performs full serialization every frame, eliminating lag and GC pressure while dragging.
  - Preset file writing (serialization + fsync to disk) moved to a background thread, no longer blocking the game's main thread; releasing the slider no longer drops frames.
  - Added content comparison before writing to disk; skips writing when the preset data hasn't changed, avoiding useless disk writes.
  - Streamlined the auto-save trigger points across the four pages, eliminating duplicate triggers; state auto-resets when switching poses/models to prevent hung tasks from writing incorrectly.

#### 2.7.1

- Added model block preset auto-save:
  - Added an "auto-save" toggle to the preset right-click menu on the IK chain, physics bones, bone constraints, and pose pages.
  - When enabled and a preset is selected, parameter changes are auto-written to the currently selected preset with 500ms debounce — no manual save needed.
  - The button dims when off and highlights when on; the target preset stays selected during auto-save.
  - State is maintained in runtime memory only, not persisted; it defaults to off each time the editor opens.

#### 2.7

- Merged all contents of the `BBSPPP` mod — uninstall BBSPPP!
  - Added texture tweening: color, pixel dissolve, and dissipate modes, smoothly transitioning between texture keyframes.
  - Added a 3D spread origin picker: pick the origin position of the dissipate effect directly on the model.
  - Added audio subtitle export: video export automatically generates SRT subtitle files with audio file names.
  - Compatible with old BBSPPP texture tween keyframe data; auto-migrates.
- Refactored the settings interface:
  - BBS++ settings promoted to an independent settings module, displayed below the BBS main settings in the settings list.
  - Internally divided into three categories: `BBS Enhancements`, `Item Spray`, `Gizmo Rework`; the left category bar supports hover descriptions.
  - BBSPPP options merged into the `BBS Enhancements` category, removing all divider characters (`==============` fake titles).
- Fixed the translation key prefix for settings items, ensuring all settings names and descriptions display correctly.

#### 2.6.3

- Added `Video Disguise`, depending on the prerequisite mod `MediaPlayer-BBS`; operations still to be improved. Only supports **Windows x64** platforms!
- Completed irl 1.1.5 localization.
- Removed the vfx localization because the mod itself already ships a localized version.
- Completed keyframe track localization for vfx and vfxlight.
- Fixed vfx shader errors.
- Fixed the issue where ++'s improved destruction wand stopped working in new vfx versions.
- Fixed the conflict between vfx and item spray.

#### 2.6.2

- Improved `pose` frames: added skip-current-frame for bones.
- Optimized `pose` frame bone list scrolling.
- Standardized `/bbsplusplus` commands.
- Improved the VFX plugin's `Destruction Wand` operation experience; hides the preview box when not held.
- Fixed the issue where keyframe color bars for the same parameter broke apart.
- Optimized the save logic of the new UI's `open by default` feature.
- Refactored the item spray disguise.
- Blocked the BBS F10 blackboard feature.
- Added an audio output device switch listener so BBS no longer goes silent after switching devices.
- Added a video preset feature to the BBS Settings > Video Recording interface.

#### 2.6.1

- Improved `pose` frames: added multi-select paste.
- Corrected keyframe track translations.
- Fixed the color array inflation issue again.

#### 2.6.0

- Improved `pose` frames:
  - Added a `bone parameter brush` to copy bone parameters to another bone.
  - Added a `modified bone indicator`; modified bones show an orange diamond indicator.
  - Ported FS2.5's `tree bone` feature into the plugin with optimized display; optional, off by default.

#### 2.5.9

- Fixed a mysterious crash bug reported by Lingfeng (his client is way too mysterious).
- Added UV offset, also completing the image disguise UV offset.
- Added alt+click-to-select clips and moved the reversed direction into the `reverse timeline scroll direction` feature.

#### 2.5.8

- Improved the VFX plugin's `Destruction Wand` operation experience.
- Added missing keyframe track localization for the VFX plugin.
- Fixed compatibility between item spray particles and irl, restoring shadow display.

#### 2.5.7

- Optimized the performance of the motion path feature; currently only the `current frame only` mode is optimized.
- Temporarily removed the BBS history improvements due to a bug.

#### 2.5.6

- Merged `tools` plugin features: shader curve fixes, structure disguise. Uninstall tools and fix!
- Compatible with `Revoxelation` shaders, but there is an overexposure issue when first entering a world; rotate the view downward or restart the shader to recover.

#### 2.5.5

- Adapted to FS 2.4.
- No longer supports previous versions!!!

#### 2.5.4

- The new movie library and disguise interface add a `set as default open` feature.
- Enhanced `IMBlocker` compatibility.

#### 2.5.3

- Brand-new movie library interface, off by default.
- Added game mode recovery after abnormal exit.
- Added the BBS++ dedicated clipboard feature.
- Blocked ctrl+B opening the recap feature.

#### 2.5.2

- Fixed a small issue with clip tracks.
- Added transform functionality to the model's 6 ItemStack tracks.

#### 2.5.1

- AAA particle selection window size is now persisted.
- Optimized clip paste logic; clips no longer paste onto tracks outside the screen.
- Added `allow dragging to extend clip tracks`, off by default. When enabled, dragging a clip upward can expand more tracks. Feedback came from a guy on DC building a ladder!

#### 2.5.0

- Added `Alt scroll wheel timeline behavior` with three modes: default, disabled, scroll left/right.
- Fixed the texture filter state restoration issue.
- Restricted `aaa_particles` versions to 2.2.0/2.2.1; higher versions have a black screen bug.
- The AAA particle selection window can now be resized.
- Category icons on the new disguise interface home page now support right-click customization.
- The playhead can now be dragged with the left mouse button on the timeline ruler.
- Optimized clip segment dragging.

#### 2.4.9

- Fixed the conflict between `shader curves` and `Bliss-Shader-Unstable`.
- Added a `compact folders` feature to the `new shader curve selection interface`.

#### 2.4.8

- Added the `new shader curve selection interface` toggle.
- Under the `new shader curve selection interface`, adding a curve no longer closes the interface; instead the curve is highlighted, and clicking again cancels it.

#### 2.4.7

- Added the keyframe track definition interface.
- Fixed a bug where external recordings could not be properly undone.

#### 2.4.6

> If you need to use `AAA particles`, do not install the new `aaa_particles` — it will cause a black screen. `bbs++` is incompatible with new `aaa_particles` versions.

- Improved bbs's `shader curve selection interface`, also compatible with `tools`.
- Fixed wemppy's bad habit of obsessively saving recently-used colors.
- Optimized AAA particles.
- Added the `world playback applies shader curves` toggle; when enabled, curve changes also take effect while playing movies in-world.

#### 2.4.5

- Pressing esc while not in the `main texture manager` now directly closes the texture manager.
- Improved `undo history` to record in more detail, and added a setting for the maximum undo history steps.

#### 2.4.4

- Fixed `item spray` collision issues.
- Made the `pbr texture interception logic` more robust.
- Added a `clip overlap fixer` to improve clip collision interception logic, making it more robust (untested because the bug cannot be reproduced).
- Fixed the issue where a paused playhead still took effect at the boundary of a `disabled camera clip`.
- Removed the `merge stop particles` feature of `item spray`.

#### 2.4.3

- Added more emitter shapes for `item spray`.
- Added scale fade-in time for `item spray`.
- Optimized `item spray` irl shadow effects.

#### 2.4.2

- Fixed shadow issues between `item spray` and irl.
- Added a max shadow count parameter.

#### 2.4.1

- `playback category` operation optimization: added multi-select drag and category renaming.
- Adjusted `item spray` guide line display.
- Fixed the bug where `item spray` particles shook with the view when shaders were enabled.
- Fixed issues between `item spray` and irl.

#### 2.4.0

- Added the `item spray` disguise.
- Added the `keyframe layout deep lock` toggle.

#### 2.3.5

- vfx plugin localization.

#### 2.3.4

- Fixed two bugs I never encountered.
- Restored the `model texture error fix` feature.
- Restored the `hide keyframe label column width handle` feature.

#### 2.3.3

- Fixed wemppy's projectile trajectory veering left.
- Added a `filter` button to the `new disguise interface layout` when opened in several other interfaces.

#### 2.3.2

- Fixed the issue where dragging clip segments in BBSFS sometimes snapped back or got stuck.
- Fixed bugs I never encountered......
- Improved the texture manager with grid mode.
- ESC can now cancel BBS hotkeys.
- Added an `open model folder` button to the new disguise interface.

#### 2.3.1

- Added the `shader control` button.
- Added settings items for the `shader control` button.

#### 2.2.9

- Added the `reverse timeline scroll` feature.

#### 2.2.8

- Adapted to BBSFS 2.3.1.
- Removed redundant features:
  - Removed the "streamlined editor loop" feature.
  - Removed the "layout lock fix".
  - Removed the "model texture error fix".
- Changed:
  - The `first-person view sway` feature now blocks BBSFS's original sway when enabled.

#### 2.2.6

- Fixed the issue where timeline ruler occlusion still allowed keyframes/clips to be clicked.
- Completed `IRLights` plugin localization.

#### ~~2.2.5~~

- ~~Model texture error fix.~~

#### 2.2.4

- Added `IRLights` plugin localization and keyframe track localization.

#### 2.2.3

- Improved the vanilla bbsfs `disguise` interface UI.
- Fixed the crash bug with negative speed keyframes in `AAA particles`, now limited to `0.01-10`.
- Changed file sorting in the `audio` and `AAA particle` selection interfaces to natural sort.

#### 2.2.0

- Improved `start frame` and `end frame` for loop mode, optimizing the special effects playback experience.
- Added a `smart freeze` toggle to the `AAA particle` edit interface to fix particle rendering errors when `start/end frames` are identical. When enabled, an extra frame is appended and the particle pauses after the first loop completes. Why is it a toggle? I don't know why I made it a toggle either.
- `end frame` keyframes set below the `start frame` value are now force-set to the same value as `start frame`.
- `start/end frames` now max out at `500`.
- Added the `first-person view sway` feature; when enabled, playing `first-person playback` restores the original walking view sway effect, though not 100% identical.
- Added `piercing render` for `AAA particles`; when enabled, particles render through **all blocks and entities**. Since this is not implemented through `aaaparticles` itself, **there may be a slight performance cost**.
- Fixed the issue where `AAA particles` rendered through the right hand in first-person view after installing `Sodium`. **If both `piercing render` and `shaders` are enabled, this bug still exists.**

#### 2.1.2

- Optimized AAA particles — a whole **1KB** smaller than the previous version!!

#### 2.1.1

- Improved the `Shift directly select parent` feature; it now also works in the `model editor` interface.
- Fixed flickering in `AAA particle` loop mode.
- Added `start frame` and `end frame` keyframes for `AAA particles`, enabling more precise loop control and solving the seamless-loop issue in particle loop mode (e.g., magic circles).

#### 2.0.7

- Fixed compatibility between `BBS++` and the `PoseCurve` plugin.

#### 2.0.5

- Improved the `status icon` display position when pressing hotkey `L` to open loop mode in `BBSFS`'s `camera editor`, and added click-to-close functionality.

#### 2.0.4

- Fixed the crash bug when loading/unloading resource packs while `AAA particles` exist in the world.
- Added the `Shift directly select parent` feature.

#### 2.0.0

- Added AAA particle disguise.

#### 1.6.3

- Curve keyframe modifications still take effect after enabling fly mode (NYK).

#### 1.6.2

- Fixed the issue where actors failed to switch modes when the editor auto-switched to adventure mode.
- Double-click a clip on the timeline to enter the edit interface (NYK).
- Fixed the crash when enabling fly mode in BBS 2.2 curve clips.

#### 1.6.0

- Fixed the bug where windows could still be dragged after locking the layout.
- Added BBS-flavored gizmo interaction (toggleable):
  - Hotkeys G/S/R only switch modes, removing the edit-after-multiple-presses behavior (optional).
  - Hotkey T cycles between translate/scale/rotate; the vanilla FS `combined` mode can also be enabled via settings to make it a four-mode cycle.

#### 1.5.0

- Corrected localization of several keyframe names.
- Disabled the plugin's NBT fix after BBSFS upgraded to 2.2.1.

#### 1.4.0

- Added LumenCore plugin keyframe name localization.
- Auto-switch to spectator mode when entering the camera interface.
- Improved the sound selection interface.

#### 1.0.0

- Changed the `cycle editor` hotkey "~": originally it cycled through three interfaces; changed it to only cycle between camera and playback. Toggleable.
- Added search functionality to the animation-to-pose interface.
- Fixed the issue where Chinese could not be switched in BBS interfaces with the input method conflict fix (IMBlocker) installed.
- Added Chinese keyframe names, also editable via the mod's json (`assets\bbs\assets\strings`). Toggleable.
- Fixed the bug where item keyframes lost detailed NBT.
