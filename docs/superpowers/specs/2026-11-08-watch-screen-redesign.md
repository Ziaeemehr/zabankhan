# Watch Screen Redesign

## Intent

Make the video watching screen easier to navigate and more polished on Android phones and iPad while keeping transcript-assisted learning fast to use.

## Goals

- Give Back a comfortable, clearly visible touch target and pair it with the video title.
- Let the standard player use the available width with a stable 16:9 frame.
- Group frequent actions into a calm toolbar and move infrequent display/playback options into a menu.
- Make the current transcript sentence easy to spot while preserving auto-follow, selection, translation, shadowing, and the bottom playback bar.
- Keep the compact-player preference and its behavior.

## Non-goals

- Changing playback, subtitle parsing, transcript timing, dictionary, translation, or history behavior.
- Adding new settings or removing existing video actions.

## Approach

Use a title/back header above the player; place the player in a full-width 16:9 frame in standard mode; group transcript, subtitle, translation, shadowing, and export actions into a compact settings-aware toolbar; emphasize the currently active transcript row; retain the existing bottom playback controls. In compact mode, retain the side-by-side small player and tools beneath the same header.

## Constraints

- Compose Multiplatform common code must remain free of platform APIs.
- Interactive targets should be at least 48dp where layout permits.
- No horizontally scrolling chip/menu rows; wrap actions or use a dropdown.
- Preserve Persian RTL and French/English content direction.
- Build Android Play/Bazaar and iOS; install Android `.debug` and the iOS app on the connected user devices when available.
