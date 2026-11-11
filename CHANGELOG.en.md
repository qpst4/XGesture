# Changelog (English)

All notable changes to XGesture are documented in this file, newest first.

This is the English counterpart of [CHANGELOG.md](CHANGELOG.md). Keep the version headers
(`## [x.y.z] - YYYY-MM-DD`) and the group headers (`### Added` / `### Changed` / `### Fixed`)
identical to the Chinese file so that the release pipeline can line up both languages.

Where the two files differ, `CHANGELOG.md` is authoritative.

## [1.64.1] - 2026-11-11

This release fixes the 1.64.0 crash and summarizes that release's main changes.

### Added
- Added NFC and Bluetooth toggle actions that open system settings when unprivileged
- Global touch supports the Type A protocol, so older devices and some ROMs can read fingers
- Global touch can select the touch device and protocol manually
- Global touch status is now a diagnostics panel
- Added a pure-icon bottom bar to the pick panel, with buttons that can be reordered, hidden and reset
- Pick panel offers word granularity (words / characters) and text state options
- Activity shortcut icons can be selected from third-party icon packs
- Float ball slideshow intervals are now fixed steps from 10 seconds to 7 days

### Changed
- Settings pages follow Miuix conventions: two-option rows use segments, three-option rows use dropdowns
- Shake, swipe, corner wheel, global touch and floating pointer pages are merged into single cards
- The pick bar supports classic and pure-icon styles and applies the change immediately
- Settings section icons are drawn as vectors, so the package no longer bundles an icon font
- Single and double tap slots no longer list actions that can only be held
- Notification history grouping by app is off by default
- Triggers and the float ball now appear together, removing the roughly 9-second startup delay
- Resident graphics memory drops from about 205MB to 83MB and physical memory from about 511MB to 263MB

### Fixed
- Fixed the pick panel crashing with the pure-icon bar when no image share engine is configured
- Fixed back gestures and all overlays failing after the accessibility service dropped
- Fixed a cold-start crash from a null handler in the message overlay
- Fixed overlapping trigger windows after reconnect that broke tap-through
- Fixed the volume bar being covered by the float ball so the slider missed touches
- Fixed the whole shortcut list disappearing when system shortcuts are redacted
- Fixed a crash when opening image search from the pick panel
- Fixed a crash when editing a ring launcher slot

## [1.64.0] - 2026-11-11

### Added
- Added NFC and Bluetooth toggle actions that open system settings when unprivileged
- Global touch supports the Type A protocol, so older devices and some ROMs can read fingers
- Global touch can select the touch device and protocol manually, with auto mode sniffing the real event stream
- Global touch status is now a diagnostics panel showing the device, protocol and candidates, with a restart action
- Added a pure-icon bottom bar to the pick panel, with buttons that can be reordered, hidden and reset
- Pick panel offers word granularity (words / characters) and text state options
- Activity shortcut icons can be selected from third-party icon packs
- Float ball slideshow intervals are now fixed steps from 10 seconds to 7 days, using the system photo picker
- Settings backup now covers macros, clipboard, custom gestures, action icons and quick menus
- Added accessibility link diagnostics showing drop counts and recovery results
- Macro targets and macro tiles show the target app icon with a macro badge
- Corner wheel slot icons support multiple shapes

### Changed
- Settings pages follow Miuix conventions: two-option rows use segments, three-option rows use dropdowns
- Shake, swipe, corner wheel, global touch and floating pointer pages are merged into single cards
- The pick bar supports classic and pure-icon styles and applies the change immediately
- The hover duration slider on the trigger page moved to the bottom of the card
- Single and double tap slots no longer list actions that can only be held
- Notification history grouping by app is off by default
- Triggers and the float ball now appear together, removing the roughly 9-second startup delay
- Resident graphics memory drops from about 205MB to 83MB and physical memory from about 511MB to 263MB
- Accessibility re-binding no longer rewrites the system master switch
- Settings section icons use a thin-line style

### Fixed
- Fixed back gestures and all overlays failing after the accessibility service dropped, with automatic recovery
- Fixed a cold-start crash from a null handler in the message overlay
- Fixed overlapping trigger windows after reconnect that broke tap-through
- Fixed triggers and panels missing right after the host starts
- Fixed the volume bar being covered by the float ball so the slider missed touches
- Fixed the DND icon in the volume panel not following the real state
- Fixed a crash when opening image search from the pick panel
- Fixed the pick panel not hiding during image search
- Fixed the whole shortcut list disappearing when system shortcuts are redacted
- Fixed the selection toolbar appearing in non-tap mode
- Fixed clone badges missing on some surfaces, with macro icons and badges completed for honeycomb and wheel launchers
- Fixed a crash when editing a ring launcher slot
- Fixed global touch misreading gesture directions in landscape
- Fixed the NFC toggle blocking the main thread

## [1.62.0] - 2026-11-06

### Added
- The index panel's opacity now ranges from 0 to 100%, with five marks on the slider
- New "shake menu": bind a menu to one direction, then shake that direction again to run an action from it
- Shake work modes are now multi-select, so unlocked, screen-on and screen-off can be combined
- New actions: read message, read in a floating window and clear notifications
- Images can join the system "open with" list and reuse the text picking and image editing entries
- Long-press the share button in the text panel to drag the current text or image into another app
- Search engine icons can follow a chosen app's icon, including icon-pack overrides
- New search engine icon shape that switches between rounded rectangle, circle, squircle and slightly rounded square
- Shortcuts can be scanned and run for testing from the extensions page over root or Shizuku
- Built-in drag actions can be tiled directly, with a new "show built-in actions directly" switch
- The idle opacity of the page scroll control bar is adjustable
- A second finger cancels the current text selection
- Global touch reports where the hand lands, so panels open from any side without edge triggers and appear at the finger's height
- The carousel app switcher gains a fading swipe-up menu with adjustable distance, plus direction reversal and sensitivity settings

### Changed
- Carousel app switching now follows the finger continuously and scales with distance, matching the floating menu
- The shake sensitivity slider is re-scaled so level 1 matches the old level 5 and low levels are no longer unreachable
- The search panel's engine layout moves into the "layout and behavior" page, with an in-page preview
- The shortcut create section can be collapsed, and types share one badge
- Execution mode options now list Shizuku first
- All pickers share one shortcut catalog, so text picking, gestures and slots show the same shortcuts
- The settings page drops the unused scroll mode, background presets and accent switch, explaining them in a card at the top
- The add entry is titled "add shortcut" everywhere
- Recent tasks remember the query plan and open in tens of milliseconds

### Fixed
- The back gesture width slider no longer raises ADB authorization dialogs while dragging
- Apps tapped in the index panel now launch while "instant trigger" is on
- Shake menus now bind correctly, flip without a stall and no longer crash the overlay
- Dragging text to share targets such as QQ now works
- Adding several image search engines no longer overwrites the previous one
- Shortcut and search engine icons now follow icon-pack overrides
- The app picker tab row no longer doubles its horizontal inset
- Choosing an app icon in the floating editor no longer crashes, and floating commands run in their own execution mode
- The command panel can be opened again after it went to the background
- Unexported shortcut targets are no longer flagged when privileged access is available
- Drawn gestures can keep several entries with the same finger count
- The contact point dialog for pinch, spread and rotate no longer offers one finger
- Panels opened from the top or bottom edge trigger are no longer pushed off screen
- Panels opened from outside triggers can now reach the top of the screen
- Panels on a side whose trigger is hidden now open again without a restart
- Missing Arabic, Japanese and Russian strings are filled in


## [1.60.0] - 2026-11-04

### Added
- New "global touch": trigger custom actions with one to five fingers anywhere on screen (needs root or Shizuku)
- Global touch supports swipes, taps, long press, pinch, rotate and drawn gestures, and its observer mode never intercepts the original touch
- Global touch has a dedicated gesture list and recording page, so gestures can be re-recorded, sorted by finger count and bound to actions one by one
- Global touch reports whether it runs on root or Shizuku and warns when another touch tool is enabled at the same time
- In-app shortcuts gain an edit page for package, class, intent link, label and icon
- New "show back chip after reading": a back chip appears on the left edge after a message is opened, with a configurable dwell time
- Edge cards show the sender name to the right of the group chat title
- The carousel app switcher gains split screen, floating window, close app and close all, and sliding past the edge cancels it
- New "X shake" quick settings tile that toggles shake gestures
- Widget panel gains an "after tapping a widget" setting with three options: collapse only for external apps / always collapse / never collapse

### Changed
- Global touch moves to "Featured" on the home screen, and "record gesture" becomes "draw gesture"
- Tapping a row in in-app shortcuts now opens the editor, and adding moves into the "+" button at the bottom right
- Text picking now uses the system segmenter; the bundled engine and dictionary are removed, cutting the APK by about 7 MB and removing the first-use preparation step
- Brightness dragging writes through two channels at once, following the finger better and applying immediately on more devices
- Gesture preview and macro entry icons now match the action list icons

### Fixed
- Fixed trigger buttons only vibrating on taps inside the capture area
- Fixed volume dragging jumping back and forth between adjacent steps and stuttering
- Fixed auto-brightness dragging only applying after release, and the screen not following on some devices
- Fixed touches being swallowed by the full-screen window during the trigger button exit animation
- Fixed the app switcher firing on an upward swipe too easily
- Fixed the back chip collapsing about one second after jumping from the side bar
- Fixed edge cards never showing again after their window failed to build
- Fixed the expanded edge card opening the app when its tap action was set to "ignore"
- Fixed reversed selection when swiping right on the long-press selection bar, and nothing happening when releasing in place
- Fixed widget panel taps doing nothing after reopening, and calendar or note widgets only opening the app
- Fixed the widget panel hit position being off and the next tap doing nothing
- Fixed the widget panel entering edit mode while scrolling
- Fixed widget scrolling fighting with panel scrolling
- Fixed widget size being reported too small and landing on the compact layout
- Fixed the keyboard not opening in the app underneath after turning on screen draw passthrough
- Fixed a crash from blur in the widget panel under software rendering, now falling back to a solid color
- Fixed some systems treating the idle full-screen overlay as a black-screen overlay and killing the service
- Fixed the float ball turning transparent after switching GIF styles and not refreshing after the image changed
- Fixed the quick launcher leaving its panel behind, where text picking then framed the panel itself, when global copy fires
- Fixed a crash when opening global touch from its home entry
- Added the missing zh / ja / ar / ru strings for global touch, the app switcher and the widget panel


## [1.55.5] - 2026-11-02

### Added
- Text-pick panel overflow menu gains "handle with another app"
- Text-pick panel overflow menu can generate a QR-code overlay
- New "drag the float ball to pick text" switch
- Global copy and drag translation now goes through the configured translation engine instead of always opening Google
- Russian locale entry added

### Changed
- Top and bottom trigger-button wave animation now uses the upstream formula: hugging the edge, bulging inward, depth following the finger, icon returning inside the shape
- Float ball is drawn on the display layer again, fixing the double-line hit area and the position when dragging out from a line
- Idle Compose layer disabled: the ball and lines are fully native now, so no ComposeView is created while idle

### Fixed
- Fixed a ticker leak in the retract animation that caused endless main-thread recomposition
- Fixed missing grow animation of the capsule and bubble under the top and bottom trigger buttons
- Fixed the reversed hint arrow when swiping down-left on the top trigger button, and completed the system arrow hints for top and bottom buttons
- Fixed repeated vibration while sliding the top and bottom trigger buttons
- Fixed a missing line when both sides are lines, and an extra ball appearing while idle
- Fixed the text-pick crosshair anchor not sharing the ball baseline and a leftover sideways override
- Restored the float ball drag dead zone so a long press is not interrupted by tiny movements
- Text-pick share search now uses the in-house PROCESS_TEXT list, avoiding the ROM picker
- Share-based text-pick search now writes into the search panel input and search history
- Fixed the missing leading dot in the component name of the copied am command for external invocation
- File browser back now exits multi-select first, with swipe-to-select and range selection
- Added the missing Russian language entry strings for zh / ja / ar, fixing Translation Check


Release pages are published with the English notes first, then the Chinese ones. In-app update
notes follow the system language: `notesEn` is shown to non-Chinese locales and `notes` to
Chinese ones, each falling back to the other when a version is missing.

## [1.55.0] - 2026-10-31

### Added
- New file browser overlay, opened from the quick menu, with three read tiers: Root, Shizuku and all-files access
- Tabs, favourites and a quick bar in the file browser, with per-tab sessions and the last path restored automatically
- Multi-select, copy, move, batch delete and new folder in the file browser, with a choice of overwrite or keep on name conflicts
- Deleted files go to a cache trash and can be undone from the hint bar, restoring them to their original location (latest batch only)
- Recursive search in the current folder, folder details, long-press rename and jump-to-path in the file browser
- Multiple storage volumes plus Android/obb entry points, with on-demand thumbnails for images, videos, APKs and PDFs
- An album-style media grid and image viewer with GIF playback, previous/next switching and an entry to the image editor
- Long-press a file in the file browser and drag it to another app to share it
- New global icon packs: pick an icon pack installed on the phone and override app icons across the launcher and all running screens
- New icon theme settings page to manage which screens an icon pack applies to and its override switch
- The floating share sheet now uses the built-in panel: grid layout, pinnable targets, drag and drop plus search-result drops, off by default
- New share panel settings page to reorder targets and manage pinned entries
- System share entries for OCR text, image search and image editing
- The floating ball and its touch window now share one window for smoother dragging, and the line lives in its own narrow window
- New idle hiding and idle GIF pausing for the floating ball, with 38% idle opacity by default
- New "Toggle floating ball" action, bindable to gestures, external calls and a desktop long press
- New separate vibration switch for the back gesture, on by default
- Colour picking from a frozen full-screen frame, with an adjustable cursor speed
- New "Actions" hub on the transfer bar: long press for 400 ms to open the action menu, and image sharing starts right from the bar
- Long-press a search result to drag it into the transfer bar
- Shake gesture modes now split into always, unlocked only, screen-on locked only and screen-off only, with locked-screen actions routed as one
- Direction animations and press warm-up for the touch buttons, starting at 3 dp with a 100 ms entrance
- The red-cross text-picking cursor now enters from off-screen, with a speed-up start zone
- The bottom-edge transition zone is now a closable "upper limit release area", off by default
- New vivo small-window sharing mode, using the system small-window share shell
- A yellow clone badge on clone entries in the freezer grid

### Changed
- Action icons now use the Material Symbols thin weight, aligned to a 1.5 stroke
- The ring launcher uses its own half-ring double icon, and the extensions page entry matches
- The share target list keeps a process-wide cache and loads icons for visible items only, so it opens faster
- The app picker grid drops the grey backing and enlarges icons to 58 dp
- Favourites in the file browser dodge live while dragging and settle with a spring animation
- The file browser quick bar scrolls horizontally, so reordering no longer moves the viewport
- The liquid-glass bottom bar is back to a single driver, so the slider no longer snaps back when switching tabs
- Shake icon strokes are unified at 2 dp to match the rest of the bottom bar
- Edge cards size their height to the body line limit, and settings changes apply at once
- The recording dock is smaller, and a saved video can be opened directly
- Content panels now close before going home or to recents
- File details drop the copy-path button; tapping the path copies it

### Fixed
- Long-pressing the ring launcher now auto-selects by the touch-down point
- Vertical swipes on edge cards were treated as taps, so up/down swipe actions never fired
- Edge card badges counted the whole notification bundle, and now count this conversation only
- Edge cards were misplaced after rotation, unlock and multi-card restacking
- Notification rule edits could be lost, historical timestamps were judged wrongly, and advanced matching defaults did not match their documented meaning
- Shortcut targets missing the intent: prefix made the tap engine report "action failed"
- In-app global copy skipped the current app and left the previous app's selection behind
- The share panel still showed an app after it was uninstalled
- Sharing an app failed the FileProvider grant; the package is now copied into the cache first
- While the ring took over touches, slots fired by mistake and the ball jumped to the top-left corner
- Releasing after a continuous ring trigger let the session take the hosted panel away
- The floating toolbar covered text after selecting all in the text picker
- Full-screen text picking missed QR codes, and text arriving late showed an out-of-sync copy
- File browser dialogs crashed inside the overlay window
- Trailing spaces in file names were stripped, so folders with spaces would not open
- Moving across volumes could lose the source file; it is now copied first, then deleted
- The quick bar stayed visible when favourites were empty
- Root folder size probing could hang; sizes are now computed on demand
- The last path and display preferences were not restored after a cold start
- Missing Japanese and Arabic strings in the file browser, share entries and the actions hub were filled in

## [1.52.0] - 2026-10-27

### Added
- New "Edge card" message style: one card pinned to the screen edge, showing only the avatar while collapsed and expanding for new messages, collapsed by swiping right, expanded by swiping left, dismissed by swiping up or down, with several conversations stacked at once
- New recording dock: pause, resume and stop while recording, plus a direct entry to screen drawing
- New "Gallery image editor" action: pick a photo from the gallery and open it in the built-in editor
- New app clone support: clones can be bound to gestures, the quick launcher, gesture macros, the holographic launcher and the widget panel, and clones are marked in the task switcher and search
- New per-clone hiding of apps, and clones in the freezer can be frozen and paused on their own
- New module-by-module restore for settings backups: pick the modules to restore before importing, and only those are overwritten
- New multi-select for share targets, so several handlers can be added at once
- New "Show icon labels" and "Mirror layout on right side" switches for the quick launcher
- New "Show as original" mode for custom floating-ball images: keeps the image's aspect ratio, with no crop and no round mask
- New vibration switch for floating-ball gestures, and a single tap now fires right away when double-tap has no action
- The ring launcher now pre-selects the first slot below the ball on long press, so releasing launches it
- New left margin setting for each page of the widget panel
- New "Toggle shake gestures" action

### Changed
- App pickers now share one grid layout and mark frozen, paused and cloned apps
- The app index keeps frozen and paused apps listed with badges, and tapping a frozen app unfreezes it first
- Freezing and unfreezing now run for every user, so clones are handled along with the main app
- The icon library lists apps per tab on demand and loads icons asynchronously, so long lists stay smooth
- The share target picker is grouped by app with expand animations, and searches by pinyin and initials
- The text picker's plus-button speed sliders now start at 0% and step in 1% increments
- The top bar blur stays on, so programmatic positioning no longer flashes it transparent first
- Icon rows match the column width when labels are off, making the quick launcher more compact
- The message ball and screen control panel draw their own shadows instead of using system elevation
- App pickers show real icons without the uniform background plate
- The side message style is now called "Side bubble"

### Fixed
- Fixed clone launches hanging on the trampoline, ignoring the confirmation, or swallowing the next tap
- Fixed clone entries being duplicated in the index or dropped from the widget panel by de-duplication
- Fixed a crash when the hidden apps list lists clones
- Fixed paused clones being resumable only with Root, now with a clear message
- Fixed two taps in a row doing nothing in tap-through mode
- Fixed the clipboard link marquee stealing swipe gestures
- Fixed duplicate callbacks for the same copy stacking several smart action buttons
- Fixed the floating ball's full-screen touch window not shrinking back, leaving an invisible full-screen layer
- Fixed edge cards not sitting flush after a rotation or a content update
- Fixed edge cards showing no icon when the notification has no large image
- Fixed back double-tap misfiring on the home screen, while riding and on a flat lit screen, and cut its sampling drain while idle and with the screen off
- Fixed the brightness range and lower bound being read wrong on some devices
- Fixed reading in a small window falling back to a plain free window on Meizu and Nubia
- Fixed the text engine reusing the draft left by the image share editor
- Fixed two-stage gestures on the right edge line
- Fixed the floating ball vibrating twice for Back, Home and Recents
- Fixed a white flash when the app cold-starts in dark mode
- Completed missing Arabic and Japanese strings

## [1.50.5] - 2026-10-23

### Added
- New "Ring compass" flow bar: targets sit around a circle and the one nearest your finger's angle is picked, while the centre and the outside of the ring do not respond
- New target configuration for the text flow bar: drag an icon from the text search engine page onto the flow bar card to add it, and remove an entry on its own
- The note body shows a scrollbar on the right when it runs past one screen

### Changed
- The image flow bar now orders its targets as image search, share to apps, copy image, share image, and no longer follows the hidden switch in the text picker
- The ring flow bar applies its blur to each target's own plate, so the centre is no longer blurred along with it
- Double-tapping the back of the phone now only wakes the screen by default, and the settings page notes the battery cost while the screen is off
- The accelerometer, gyroscope, proximity and light sensors are registered once, cutting duplicate listeners and standby drain

### Fixed
- Fixed custom gestures triggered from the line strip opening the panel on the wrong side of the ball
- Fixed new voice or image blocks being pushed off screen when inserted at the top of a note
- Fixed a crash when handing external files to another app; unreadable files now show a message instead
- Fixed images whose file names are purely numeric not being recognised in the gallery
- Fixed the flow bar target card staying empty, which made drops look like they did nothing
- Fixed intent links in picked text being treated as unopenable
- Fixed intent links with component information losing their target, and startup failures going unreported
- Fixed shake sensors not throttling to the configured interval
- Completed missing Arabic and Japanese strings

## [1.50.0] - 2026-10-22

### Added
- New "Global drag & drop flow": drag an image or text in any app and a frosted flow bar slides in from the screen edge, ready to hand the content to another app or save it to Ideas
- New separate "File share engine" setting, configured independently from the drag & drop flow
- New "Floating touchpad" action: a porcelain-white double-layer frame with 1:1 rounded square keys for directions, navigation and recents, and it can shrink into a ball that docks at the edge
- New "Block touches" action: swallows every touch on screen and is dismissed by drawing a circle, double-tapping to turn the screen off, or a five-minute timeout
- New payment actions: WeChat Scan, WeChat Pay Code, Alipay Pay Code and Alipay Scan
- New screen drawing tools: mosaic redaction, dashed lines, text, numbering and marquee selection, with the toolbar rebuilt as symmetric frosted capsules
- New "Smart screenshot" entry in Global Copy that switches between the accessibility and OCR engines at any time, plus support for receiving the system web search intent
- New "Icon library": three offline font icon sets (Lucide / Remix / Tabler); action icons and app icons can each be replaced
- New "Overlay icon plate" mode: dark, light or follow system, applied to the quick launcher, ring launchers and other panels
- New hand-drawn "Custom gestures" for the floating ball: match actions by trajectory, with mirrored gestures generated alongside
- New "Pin" action for the quick launcher: tapping an entry keeps the panel open, apps underneath stay usable outside the panel, and pinning can be limited to the current app
- New long-press countdown ring in the ring launcher and corner wheel, showing whether the target will open in a small window or full screen, plus a picture-in-picture badge on the icon
- New edge handle for the side panel: press and drag to resize 1:1 and snap to a standard width; Ideas favourites can be filtered by any/all
- New background blur for the clipboard popup, following the global overlay blur switch, with an adjustable radius
- New Bento two-column grid in the extension hub, with the Shell command panel moved up next to the quick wheel
- New "Keyboard trigger behaviour" setting on the home page: narrow area or full screen, configured separately for portrait and landscape
- New "Recognition & positioning" subpage holding swipe distance, long-press duration and launcher position, bringing directional gestures back to the first screen
- New four-column grid for app and action pickers, matching the size of the action list
- New trailing chips in the Activity picker: "Needs privilege" for non-exported components and "Entry" for launcher activities

### Changed
- Quick menu action icons redrawn with a 1.5 dp line style, no longer blurry at small sizes or on icon plates
- The freezer's "Manage apps" now has "In freezer / All" views, frozen members can be unfrozen and removed, and a separate "Freezer settings" page was added
- The touchpad action is consistently named "Touchpad", with direction and navigation keys enlarged to 37 dp
- The "Quick actions" group in the extension hub moved below "Tools", and the icon library entry uses a palette icon
- The about page gained a version row with a unified vX.Y.Z (build) format and no duplicated "v" prefix
- The home entry for per-app profiles now shows only the first sentence, leaving the long explanation to the profile page
- Action grid titles may wrap to two lines, with larger icons and text, so long action names are no longer always truncated
- The side panel lost its close button; tapping the search field expands it to the full row and Back collapses the search first
- Gesture macros now show the "has parameters" dot in the action picker, and the macro picker marks the current macro
- The text toolbar in the pick panel reverted to Material icons to match the image section
- The "Default" trigger mode was removed: unset triggers fire on release, and actions that can only be sustained stay sustained
- Media notification refreshes and accessibility window events are throttled to reduce idle heat

### Fixed
- Fixed the liquid-glass bottom bar turning grey on the indicator and bar ends when overscrolling on the first or last page
- Fixed the global sensitivity slider on the shake page being undraggable after horizontal swiping returned to the native pager gesture
- Fixed "Toggle auto-rotate" and "Force portrait/landscape" failing on some devices by writing through three fallbacks and verifying the result by reading it back
- Fixed the slider in the trigger settings page swallowing touches and hanging the page, and the trigger preview disappearing after leaving
- Fixed expand panel slots storing empty templates (macros and links bound to dead actions, empty wheels); parameters are now picked before saving, and the keyboard no longer covers the panel
- Fixed the floating ball vanishing after picking text with built-in animations; the ring toolbar's "Move" now works, the keyboard button uses the shared entry point, and the redundant collapse button is gone
- Fixed the disclaimer and permission pages losing their bottom buttons on short screens or large fonts, which blocked onboarding
- Fixed the number and text panels in screen drawing being clipped, and upgraded the config sliders to Miuix capsules
- Fixed long danmaku filling the screen and the bottom track being clipped in landscape
- Fixed the clipboard popup appearing translucent before turning frosted; the first frame is frosted now
- Fixed the search engine editor giving no required-name hint and looking unresponsive when saving with an empty name
- Fixed overlay icons drawing black-on-black in system light mode and caches not refreshing after changing the icon plate mode
- Fixed "Choose app icon" overwriting itself because all rows shared one file, and wired global icon overrides into every runtime renderer
- Fixed phantom hot zones vibrating when the flow bar was dragged over empty space
- Fixed the long-press progress ring appearing even when the policy is always full screen or always small window
- Filled in missing Arabic and Japanese strings: 33 icon library entries, 27 screen control entries, plus file sharing and overlay visuals

## [1.42.0] - 2026-10-16

### Added
- New per-app gesture profiles: each app keeps its own full set of edge triggers, copied from the global set when the profile is created and no longer following it
- New "Restore from global" for a whole profile, which copies the current global set again
- New edge handle docking to either side: drag it up or down to move it, and drag past the middle of the screen to snap to the other side
- New three-card onboarding guide: one card each for gestures, edge triggers and the floating ball, shown as the fourth welcome step and replayable from the about page
- New hands-on practice in the guide: the real triggers light up on screen with a "hold here" marker and each step is ticked off once performed
- New action picker opens on the action already bound to the gesture and pulses it three times
- New long press on the edge handle opens the full editor window, with the same block editor, images, voice, tags and reminders as the panel
- New "Starred" filter in ideas: starring a card files it under Starred and unstarring removes it
- New long press on a quick slot in the expand panel shows a small bar for "Open in freeform / full screen" and "Edit mode"
- New "Show bottom action bar" switch for the quick launcher, and a long press on empty panel space enters edit mode
- New "Alphabet index position" slider: continuously adjustable, with five dots marking the quarters of its travel
- New vertical scrolling for images in the pick panel when the image is taller than the viewport

### Changed
- The floating ball, edge lines, gesture hints and quick menu now follow the app accent colour, so by default they match the blue of the main UI
- A short swipe up on the floating ball opens the quick menu by default; the alphabet index length defaults to 60% with a 75% maximum
- The holographic launcher now uses one app selection list with three modes (allow all, only selected, exclude selected); the duplicate "Hidden apps" entry is gone and existing settings are folded into the selection
- "Tap on release" for the floating pointer now defaults to off
- The edge handle touch area is narrowed to 24 dp and the unused "Bar width" setting is gone
- Profile pages are titled "Profile · <app>" and only the whole profile can be restored from global
- Profile rows and the "Add app" row now have 12 dp of space between them
- The pick panel body grows with its content, so picking a few characters no longer fills the screen
- Bottom and top trigger titles read "Bottom trigger · group N" instead of a random identifier
- The idea card buttons for copy, star and done are optically balanced and the ⋮ menu lines up with the right edge of the text
- Icons inside buttons use the Miuix set (Medium weight), with fill versus outline still marking state
- Accessibility event subscriptions and window change handling are trimmed, roughly halving idle CPU use

### Fixed
- Fix the quick launcher, task switcher, app index, volume and brightness doing nothing when opened from the corner wheel or the floating ball on a side with no triggers
- Fix the quick launcher panel list in floating ball gestures showing only one panel
- Fix edge gesture back failing after the predictive back setting was turned off
- Fix landscape triggers in a profile not staying deleted and an emptied side growing back
- Fix the first binding of a gesture wiping factory gestures such as short swipe back and swipe down for the app index
- Fix the gesture master switch still being off on a fresh install when the guide is opened
- Fix third-party apps reporting "Widget ID was not passed" when creating a shortcut
- Fix a binding showing as saved and then reverting to unconfigured
- Fix copy from the pick and search panels giving no feedback
- Fix white text on a white background in the quick tools panel on a light theme with window blur off
- Fix the pick panel body filling the screen first and then shrinking back
- Fix the edge handle being invisible on dark wallpapers
- Fix the app index position snapping to five fixed values instead of moving continuously
- Fix the clipboard overlay pin button not showing whether the window was pinned
- Fix onboarding buttons being greyed out while accessibility reconnects, the unbound row showing an initial value instead of the gesture to be bound, and trigger names not telling short from long distance
- Fix the gesture angle reset button using a history and recent icon
- Fix the left edge of the leading buttons on idea cards differing from clipboard cards

## [1.41.0] - 2026-10-13

### Added
- New "Page scroll" gesture action: opens a floating control bar that keeps the page scrolling smoothly, with direction, speed, pause and close
- New "Hold and drag" page scroll mode: hold the edge and move up or down to scroll page by page
- New page scroll settings: default speed and control bar colour (dark grey, black or white) or follow the app accent colour
- New "Gesture macros": record taps and swipes in another app as a sequence of steps, then replay them with a single gesture
- New macro recording bar: pause, resume, stop and save while recording, with a count of recorded actions
- New macro editing and test run: set the name and target app, review the steps, and run a macro without binding it to a gesture first
- New "Gesture macro" gesture action: bind a macro to a gesture
- New "Quick menu" gesture action: one entry that gathers 14 tools and 10 app feature entries, fully populated with no setup
- New quick menu tools: unit converter, currency converter, QR code generator, code scanner, text toolkit, translate, password generator, stopwatch, countdown, timer reminder, timer history, pomodoro, calculator, screen colour picker
- New quick menu app feature entries: foreground activity inspector, freezer, shell command panel, search panel, clipboard panel, pin to screen, ideas, page scroll, widget panel, run macro
- New quick menu search and a "Recently used" section
- New live camera scanning in the code scanner: recognises several codes at once so you can pick one from the candidates, and also reads codes inside images
- New QR code generator: turns text, a link or a Wi-Fi network into a QR code
- New freezer list multi-select: enter selection mode to select all, unfreeze in bulk or remove several apps from the list
- New freezer side panel "Panel width" options (standard or compact)
- New image pins get a notification title that recognises the text in the image
- New "Delay after unlock" setting: wait 0 to 1500 ms before opening a message, adjustable in 100 ms steps
- New freeform "Mi Window" mode: opens freeform through the Mi Window app and falls back to standard freeform when it is not installed
- New freeform "Follow trigger position": docks the window to the side the gesture came from and centres it on the trigger height
- New freeform bottom safe margin: the window no longer covers the gesture bar or the keyboard area
- New pick-and-translate target languages: Turkish, Brazilian Portuguese, European Portuguese and Indonesian

### Changed
- The page scroll control bar uses the M3 Expressive look, shows its level as a numeric badge and drops the handle
- The quick menu action toggles between "Open" and "Collapse"
- The pin drop targets are now half-hidden and always present: available whenever you drag, fading out on release
- Overlay hint bars use the dark background style
- Temporary Do Not Disturb can now last from 1 minute to 24 hours
- The "Delay after unlock" default is now 600 ms
- Freeform size and position are now computed from the real display size and orientation, which is more reliable in landscape
- The freeform settings page explains the reliable size range under the system's second scaling
- The third-party licences page uses structured cards and documents each project's origin and ported parts

### Fixed
- Fix clipboard monitoring failing to start in Shizuku mode and staying at "Not listening"
- Fix the floating action button covering the last row of the freezer grid and the shell command panel
- Fix the back key leaving freezer multi-select entirely instead of stepping back one level
- Fix the about page appendix stopping short of the bottom when flinging to the end
- Fix overflowing origin chips, duplicated titles and developer hints leaking to users on the licences page
- Fix the minute presets in the quick menu timer reminder not showing which one is selected

## [1.40.2] - 2026-10-11

### Fixed
- Fix the app crashing on launch after app notifications are turned off in system settings or clipboard monitoring is disabled
- Fix the floating ball and edge gestures disappearing after app notifications are turned off in system settings, requiring the app to be reopened
- Fix the default idea tag names not following the interface language

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
