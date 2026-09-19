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
| Blockbench executable path | Empty | Before right-clicking a user model in the disguise panel to open it in Blockbench, select the location of Blockbench.exe here. |
| Prefer .bbmodel file       | On    | When the model folder contains a .bbmodel project, right-click opens it preferentially; otherwise opens the .geo.json file. |
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
| **Performance Optimization** |         |                                                                                                                                                                                                                                |
| Model block render distance | 0       | Vanilla defaults to 512 blocks. Lower this value to reduce rendering and picking overhead for distant model blocks. Set to 0 to use the vanilla default.                                                                 |

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

### Enchant Glint

- The pose editor page gains an `Enchant Glint` toggle that adds a vanilla-style enchant glint to the currently selected bone, looping seamlessly forever.
- Below the toggle sits a `glint color` picker for customizing the glint color; the default white matches the vanilla look, and the alpha slider doubles as a quick way to dim the effect.
- **Right-click** the toggle or the picker and choose `Apply to children` to spread the state to every child bone of the selection (including children that were never edited).
- Known limitation: **the glint currently does not render while an Iris shader pack is active** (it works with shaders disabled and in the editor preview).
  This is caused by an inherent compatibility limitation in how Iris handles mod-provided core shaders — see Iris' documentation at
  `docs/development/compatibility/core-shaders.md`. A compatible path may be provided in a future version.

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
| Configurable custom hotkeys   | Custom hotkeys such as Shift+W (select keyframes at current time) are now integrated into the BBS UI keybind settings under the "Keyframe Editor" category, allowing users to freely remap them. |

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

#### 3.5.1 (IRL Localization & Compatibility Fix)
* **IRLights localization complete**: rewrote the Chinese translation table covering all new IRL 1.1.7 UI text (light types/parameters/preset labels) and track editor short names (Thickness, Volumetric, Back rim, Front rim strength, Outlined replays, Lit replays, etc.)
* **IRL 1.1.7 compatibility**: fixed `IRLiteGuideVisibilityMixin` `renderGuide` injection signature by adding the `boolean guideVisible` parameter to match the new method descriptor

#### 3.5.0 (BBSFS 2.6 Adaptation)
* **Adapted to BBSFS 2.6.1**: completed runtime adaptation, fixed multiple Mixin target drift and crash chains during startup and rendering
* **Track localization**: added Chinese translations for 2.6 material property tracks (smoothness, metalness, subsurface scattering, emission, etc.), IK tracks (target, pole, weight, chain length, etc.), physics tracks (damping, wind strength, collision radius, etc.), and bone constraint tracks; now supports both `/` and `.` track ID separator formats
* **Fixed hidden disguise form**: after the 2.6 rendering chain change, the Mixin target migrated from BaseFilmController to FilmEntityRenderer, restoring the "hide disguise form" feature
* **Removed conflicting features**: structure disguise, video disguise, per-bone PBR, pose material panel, follow track camera mode, and other features conflicting with 2.6 vanilla have been removed
* **Cleaned up dead code**: removed TextureThumbnailManager, UIGridFileLinkList, PBR UI, and other deprecated modules; removed Gizmo rework settings
* **Texture tween enhancements**: pixel dissolve mode gains flash and PBR emission support; 3D spread origin picker adapted to the 2.6 drag system

#### 3.3
- **Edit models in Blockbench**: right-click a user model in the disguise panel to open it directly in Blockbench. First set the Blockbench.exe path in Settings -> UI Enhancements. If a `.bbmodel` project exists in the model folder it is opened preferentially, otherwise the `.geo.json` is opened (toggle in settings). The menu item stays grayed when the path is unset/invalid or the model is not `.geo.json`; built-in BBS models do not show this item.
- **Movie editor**: `R` now toggles between the current and the previous camera mode. The orbit controller is a persistent member, so camera angle, distance, and bound entity are preserved when switching back.
- **Keyframe track localization**: filled in missing track names for hotbar item slots, velocity vX/vY/vZ, VFX enhancement, fluid, PBR, camera clip, Mob, and item spray tracks.


#### 3.2.2
- **Enchant Glint**: pose editor gains a vanilla-style enchant glint per bone, with custom color/alpha and apply-to-children via right-click (currently hidden under Iris shaders)
- **AAA particles**: fixed depth-state pollution causing editor preview black screen; added fallback rendering for textureless models; cleaned up dead code
- **Performance**: optimized model block opening lag — idle Dashboard warm-up, frame-split VAO baking, UI palette caching, Gizmo skip-redraw when idle, configurable render distance, global toggle no longer reloads the world
- **Build**: supports dual-target builds for 1.20.1 / 1.20.4; updated Fabric API and dependency paths
- **Configurable hotkeys**: Shift+W (select keyframes at current time) is now integrated into UI keybind settings under the "Keyframe Editor" category, allowing users to remap it

#### 3.2.1
- Added 605 missing English keyframe track translations; fixed tracks still showing Chinese under English game language
- Fixed hardcoded Chinese in follow-orbit menu; language detection now prioritizes BBS's own setting

#### 3.2
- Disguise interface: list/grid mode toggle, Ctrl+scroll to zoom icons, drag models to category folders to move files
- Fixed Blockbench Bedrock animation step keyframes being played as linear interpolation

#### 3.1.1
- Removed texture tint/whiten from pose editor
- Fixed OrbitViewGizmo incorrectly hidden in vanilla track mode
- Follow track mode gains right-click menu support; hotfix removed Mixin UTF-8 BOM that crashed startup

#### 3.1
- Merged all bbs_snow (CML) features: fluid simulation, Particle Plus, PBR texture tiering, bone textures, Premiere export, three clip types, action overlay, Additive animation, mini windows, and more
- Refactored settings into an independent module: BBS Enhancements, Enhanced UI, Item Spray, Gizmo Rework, CML Extensions, Export Enhancements
- Follow track supports XYZ axis direction selection

#### 3.0
- Renamed from BBS++ to BBS Cubed; collaboration release

#### 2.7.2
- Optimized preset auto-save: debounced serialization, background disk writes, content comparison to skip unnecessary writes

#### 2.7.1
- Added model block preset auto-save: enable via preset right-click menu on IK chain / physics bones / constraints / pose pages; 500ms debounce auto-writes

#### 2.7
- Merged all BBSPPP features: texture tweening (color / pixel dissolve / dissipate), 3D spread origin picker, audio subtitle export
- Refactored settings into an independent module

#### 2.6.3
- Added Video Disguise (requires MediaPlayer-BBS, Windows x64 only)
- Completed IRLights localization; fixed vfx shader errors and item spray conflicts

#### 2.6.x
- Pose frame enhancements: bone parameter brush, modified-bone orange indicator, skip current frame key value, multi-select paste, tree bone list
- Improved Destruction Wand UX; fixed broken keyframe color bars
- Added audio output device switch listener to prevent muting

#### 2.5.x
- Merged tools plugin: structure disguise, shader curve fixes, sun/moon declination keyframes
- New movie library interface, BBS dedicated clipboard, Alt+scroll timeline behavior
- AAA particle window resizable & persisted; allow extending clip tracks
- Adapted to BBSFS 2.4; completed image disguise UV offset

#### 2.4.x
- Added Item Spray disguise with multiple items and emitter shapes; ongoing shadow & collision optimization
- New shader curve selection interface; world playback applies shader curves
- Improved undo history panel with configurable max steps

#### 2.3.x
- Added shader control button in movie editor (left-click toggle, right-click picker)
- Fixed clip drag snapping back; fixed playhead projectile veer
- Texture manager grid mode; ESC cancels hotkeys
- vfx plugin localization
