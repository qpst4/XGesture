# Changelog (English)

All notable changes to XGesture are documented in this file, newest first.

This is the English counterpart of [CHANGELOG.md](CHANGELOG.md). Keep the version headers
(`## [x.y.z] - YYYY-MM-DD`) and the group headers (`### Added` / `### Changed` / `### Fixed`)
identical to the Chinese file so that the release pipeline can line up both languages.

Where the two files differ, `CHANGELOG.md` is authoritative.

Release pages are published with the English notes first, then the Chinese ones. In-app update
notes follow the system language: `notesEn` is shown to non-Chinese locales and `notes` to
Chinese ones, each falling back to the other when a version is missing.

## [1.40.0] - 2026-10-11

### Added
- New "Quick wheel" gesture action: expands a custom wheel in place at the trigger point, with multiple wheels and per-container tap actions, long-press actions and icons
- New wheel setup and layout editing: two-level shapes (circle or rect), 90° sectors, summon position (follow the finger or snap to the screen edge), container size and gap, corner radius, icon and text size, text labels, horizontal and vertical offset, open animation and speed, background blur and dim; icons from the built-in library, installed apps, the gallery or text; hold a container to see the action it will run, drag to reorder containers and drop one on the delete area to remove it
- New cloud backup: WebDAV and S3-compatible object storage (AWS S3, MinIO, Cloudflare R2, Qiniu, Aliyun OSS)
- New storage backend management: add, edit and delete multiple profiles, test the connection and pick the active backup target
- New cloud backup and restore: upload now, refresh the remote list, download and restore, and delete a single backup
- New cloud retention policy: cap how many backups are kept, remove older ones automatically after each successful upload, and clean up on demand
- New cloud credentials are saved along with the settings backup
- New voice recording: tap the mic to start recording, keep the result as an audio block you can play in place, up to 5 minutes per clip
- New speech to text: long-press the mic to dictate, with the text written straight into the idea
- New idea reminders: set a reminder time for an idea, with common presets and custom date and time wheels
- New reminder notifications: a high-priority notification with vibration arrives on time, carrying "Done" and "Snooze 10 min"
- New reminder rescheduling: pending reminders are rescheduled after boot, an app update, or a clock or time-zone change
- New reminder status cues: an unhandled reminder glows along the edge handle, and an overdue one greys out as "Reminded"
- New idea tags: add, rename, recolour and delete tags, undo a deletion, and long-press to drag them into order
- New tag filtering: match all or any of the selected tags, combined with search
- New images in the composer: add several at once, inserted at the caret
- New image editing for ideas: open the built-in image editor and replace that block
- New star and mark-as-done actions on idea cards
- New clipboard tab filters: all, images, links, rich text, files
- New "tab opened from handle" setting: last used tab, always Ideas, or always clipboard
- New "Overlay blur" master switch: turning it off gives every overlay a solid panel
- New action toast inside overlays
- New "Force stop current app" gesture action: kills the foreground app process; needs Shizuku or root
- New landscape trigger "Copy portrait settings"
- New floating-ball quick launcher action can open a chosen page
- New floating-ball gesture setting "Align launcher with touch-down point"
- New long-press on an image in the pick panel copies the current page to the clipboard
- New freezer "Work mode" setting
- New freezer "Collapse all by mode"
- New corner wheel "Rings" option

### Changed
- The corner wheel's "Wheel outer diameter" becomes "Ring gap", with its range following the bubble diameter
- New triggers start with the factory-default actions
- Trigger slots no longer borrow actions from other groups, and a slot you never set falls back to the factory default only
- The freezer remembers whether each member was last frozen or paused, and batch collapse follows each member's own mode
- The freezer "Work mode" becomes the fallback for members with no record
- Freezer "Resume all" only resumes and keeps the mode records
- The side panel content stays resident, so pulling it out from the edge tracks your finger
- The side panel is redesigned: uniform cards, editing and composing in centred overlays, and a timeline grouped into today, yesterday and earlier
- The composer becomes a block editor; Enter adds a line break, and the body scrolls and follows new blocks
- Chinese strings unify on "闪念" and "侧边面板", and other languages on ideas
- The widget panel overlay and the add-widget page join the gesture-back path
- Overlays handle the back key the way the system actually dispatches it

### Fixed
- Fixed the app crashing on the back key on some devices
- Fixed gesture back doing nothing in the widget panel overlay and the add-widget page
- Fixed the turn-back slot still vibrating when no action is set
- Fixed the trigger vibrating twice in a row before the swipe threshold is reached
- Fixed the first press being ignored after the adjustment panel appears
- Fixed "Re-freeze apps" also freezing paused members
- Fixed mode records being lost when batch collapsing after "Resume all"
- Fixed the side panel not tracking the finger when pulled from the edge
- Fixed the side panel never opening again after the accessibility service is rebuilt or the panel is removed by the system
- Fixed the back key and gesture back failing to close an open panel
- Fixed back not dismissing the keyboard first when it is up
- Fixed the side panel being too wide in landscape
- Fixed idea cards having washed-out, inconsistent borders, and separators hugging the bottom buttons
- Fixed the result not coming back and the panel stalling after entering the built-in image editor
- Fixed voice playback not resetting when it finishes
- Fixed a reminder not firing on time, not firing after a restart, and not firing when the system rejects exact alarms
- Fixed "Snooze" leaving the card time unchanged, and the "yesterday" step degrading to an absolute date
- Fixed the keyboard being pushed away and the clipboard overlay sticking when it appears
- Fixed freeform windows always hugging the left edge on Xiaomi devices
- Fixed the volume slider snapping back and the volume not changing on some devices
- Fixed the edge triggers and the handle being captured in screenshots
- Fixed the keyboard yielding twice and the source chip disappearing in the pick panel's edit mode
- Fixed idea images looking soft
- Fixed the quick wheel binding without any wheel, crashing on an out-of-range container index, overlay window lifecycle defects, and missing screen-reader labels on sector rings
- Fixed the process being killed after a foreground service starts on some devices
- Fixed the external-invocation description in the English UI still naming the old panel
- Fixed missing Arabic plural forms

## [1.36.0] - 2026-10-06

### Added
- New "volume up" and "volume down" gesture actions
- New floating-ball "inward then down" and "inward then up" two-stage gestures; the gesture settings page is grouped by direction
- New freezer "Pause": the app stays installed and its launcher icon stays in place but turns grey, and tapping it shows a system dialog with a one-tap resume; the freezer list, batch actions, manager filter and search-panel quick actions can all pause and resume
- New inverted condition for the notification advanced-match JSON, with stricter JSON validation

### Changed
- The floating ball size limit is raised from 72dp to 96dp, with 1dp steps
- The first stage of the floating-ball two-stage gestures uses the short-swipe threshold
- The trigger gesture settings page is split into sections, with the slot list first
- Ring launcher settings and strings use the "ring launcher" naming; existing configuration migrates automatically on upgrade and gesture bindings are unaffected
- The ring launcher enters edit mode only when no slot is pinned and no app can fill in; otherwise it fills with recent apps
- The notification rule screen state becomes a three-way choice (any / screen on / screen off), and leaving all three charging states unchecked means no restriction
- "Floating window for the current app" first tries to move the window in place on Meizu devices and only relaunches when that fails
- In-app update notes follow the system language (Chinese or English)
- Freezer "Re-freeze" only affects apps in use and keeps paused apps paused; "Unfreeze all" no longer touches paused apps
- The freezer import also covers frozen and paused launcher apps
- Freezer pause and freeze both skip this app itself, system, SystemUI and the current launcher

### Fixed
- Fixed the trigger being drawn higher and shorter than its touchable area in the trigger settings preview
- Fixed a gesture slot losing its launch mode after saving, which still launched fullscreen when the floating window was selected
- Fixed ring launcher slots being cleared after editing
- Fixed charging states being re-checked when reopening a notification rule that had only one phone state selected
- Fixed the app-condition dropdown label and value being misaligned in notification rules
- Fixed the notification-access entry not opening this app's detail page
- Fixed occasional crashes when clipboard monitoring starts
- Fixed the flashlight action still opening the app info page when the permission was already granted
- Fixed four mojibake strings in the English UI

## [1.35.0] - 2026-10-05

### Added
- New floating-ball "lines on both sides" layout: the ball side and the opposite side share one
  line style; the ball appears on drag and the text-pick panel opens from the starting side
- New floating-ball "swipe down then inward" and "swipe up then inward" gestures, bindable to
  actions separately
- New "keep the floating-ball reminder after unlock" switch: reminders received while locked are
  retained and shown after unlock according to the auto-dismiss time
- New text-pick panel "panel position": docked to the bottom, or centered (92% width and 70% of
  screen height in portrait; the whole panel lifts with the keyboard)
- New text-pick "default pick mode" setting: remember the last used state, always on, or always
  off
- New per-app "launch mode" for bound apps: follow the global policy, always fullscreen, or
  always in a floating window, with the current mode shown on the bound row
- New "local app" text-pick translation engine: choose which translation app receives the picked
  text
- New app shortcut search: searches shortcuts declared by installed apps, matches full pinyin and
  initials, invoked with `sc` by default
- New clipboard search: searches text, links and images in clipboard history; tap to copy back to
  the clipboard, long-press to copy and paste into the focused input, invoked with `cb` by default
- New cloud OCR / translation providers Gemini, OpenAI, Claude, DeepSeek, Groq and Grok (xAI),
  bringing the cloud options to ten
- New one-tap presets for custom cloud endpoints: picking a vendor fills in the endpoint and a
  recommended model

### Changed
- The search panel is now always fullscreen: the "presentation" setting is gone, and candidates
  are organised into sectioned cards with filter chips
- Instant translation in the text-pick panel now defaults to on, including for existing users who
  never changed the switch
- With instant translation off, webpage translation becomes "jump translation": the selected
  translation app takes priority, and the web page opens only when none is selected
- Reduced stutter when dragging the edge handle or the floating ball

### Fixed
- Fixed repeated flashing caused by the overlay being rebuilt over and over when opening the
  search panel
- Fixed search panel candidates not sticking to the bottom and the bottom card being covered by
  the engine area
- Fixed "floating window for the current app" snapping back to the app home page
- Fixed the text-pick panel not being width-limited when centered or docked to the bottom in
  landscape
- Fixed the right-edge line being pushed off screen when the keyboard opens
- Fixed the selected provider mark and badge not refreshing immediately after switching the cloud
  provider
