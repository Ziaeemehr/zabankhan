# Video and dictionary layout redesign

## Intent

Give the transcript dictionary more room while keeping video playback easy to control. Make the compact player discoverable and make video shadowing controls feel like the compact controls in the read-only French Reader reference.

## Goals

- Put an always-visible compact/expand toggle in the watch header.
- Limit the standard player to about 35% of the available screen height; keep the compact player around 96dp.
- Reduce the vertical footprint of the four frequent video actions while keeping their accessibility labels.
- In the watch dictionary, combine or compact the save/edit and provider actions, and reduce provider icon size while keeping wrapped layout and usable touch targets.
- Present shadowing actions as a compact icon control row similar to French Reader's video shadow controls.

## Non-goals

- Change playback, transcript selection, translation, dictionary lookup, or shadowing recognition behavior.
- Change dictionary layouts outside the embedded watch dictionary.
- Add a floating player overlay or horizontal scrolling controls.

## Approaches

1. **French Reader hybrid (chosen):** constrain the player height, expose a direct compact toggle, compact the frequent actions, and adapt the embedded dictionary and shadowing controls to the smaller watch viewport. This addresses both the lost dictionary space and the hard-to-find player control while matching the reference the user selected.
2. **Player-only adjustment:** shrink the video and expose the toggle, leaving the current dictionary and shadowing control layouts unchanged. This is smaller but does not address the requested control density.

## Design decisions

- The compact toggle sits in the watch header beside the title/actions and switches between the existing standard and compact player modes.
- Standard player height is capped at 35% of the available screen height; compact mode keeps the current approximately 96dp player.
- The subtitles, translation, shadowing, and export actions remain available in a compact icon-first row with semantic labels and wrapped layout.
- Subtitle size remains in the existing overflow menu and uses explicit `A−` and `A+` controls around the current size indicator.
- Only the dictionary embedded in the watch screen uses the compact arrangement. Word actions and dictionary provider choices share a compact, wrapping region, and the meaning/list/save controls use one slim row where width permits.
- Shadowing retains its current practice flow and recognition logic, but its controls use compact icon buttons modeled on French Reader's 40dp video-shadowing actions.
- The reference directory `/Users/ziaee/git/FrenchReaderSpike` is read-only.

## Constraints and verification

- Shared Kotlin/Compose implementation; no platform-specific APIs in `commonMain`.
- Preserve all existing callbacks and behavior; no horizontal scrolling for controls.
- Verify by building Android and iOS and manually inspecting the watch, compact player, dictionary, and shadowing views on connected devices.
- Do not add or run tests unless the user asks for tests.
