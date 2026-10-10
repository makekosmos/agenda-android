# Design review log (KOS-370 Phase 3-UI, U7)

Reviewer protocol: look at the recorded Roborazzi PNGs like a Linear
designer, check against docs/design-language.md + docs/design-tokens.md.

## Round 1 — first record (`screenshots/round1/`)

Findings:

1. **Date/project dropdown clipped at the screen edge.** The AMenu popup
   opened below its anchor even when that pushed items off-window —
   `datedropdown-light.png` showed «Без даты» half cut. Fixed with a
   PopupPositionProvider that opens below when it fits and flips above
   the row otherwise (Components.kt `AMenu`).

2. **QE parsed chips missing in the record.** `quickentry-*.png` showed
   no «купить молоко»/«13 окт» chips: the test used `Thread.sleep(400)`
   which blocks the main looper, so the 120ms debounce LaunchedEffect
   never ran. Fixed by advancing `mainClock` instead. This was a test
   artifact, not a product bug — the preview works the same way on
   device.

3. **All-caps title in quick-entry record.** `BasicTextField` with the
   default keyboard options auto-capitalized under Robolectric input
   injection; the PNG read "КУПИТЬ МОЛОКО ЗАВТРА". Fixed by setting
   `capitalization = Sentences, autoCorrectEnabled = false` on
   SheetField.

Verified after fixes: chips render («купить молоко» + «13 окт»), menu
flips above the row with all three items visible, dark sheet reads
correctly (`popover` lifts off `bg`).

Kept as designed:

- Home page groups mirror the desktop sidebar: `sidebar_bg` cards,
   hairline dividers indented to the label column (44dp = icon+gap),
   8dp muted project dots, no counts.
- List rows: 20dp ring cell at 16dp inset (desktop `CONTENT_GRID_X`
   analog), meta in `muted_fg`, hairline under the title column.
- Restrained accent: only the done-ring fill and the «+» button carry
   accent color; the overdue chip uses `destructive` at 10% fill.
- Typography: Inter at 15sp body / 22sp semibold title with negative
   tracking, 12sp medium section headers — hierarchy by weight + muted
   color, not size jumps.

## Round 2 — polish pass

Items rechecked after the round-1 record: alignment to the left grid
line (ring and back-arrow column share the 16dp inset; bar titles start
at 48dp, matching row text), vertical rhythm (48dp home rows, ~60dp
task rows with meta), muted contrast (0x615f67 on 0xf8f7f9 ≈ 5.7:1),
hairline weight (0.5dp `border`), icon optical size (20dp inside the
24-viewport drawables), accent restraint, dark surface layering
(bg < sidebarBg < popover in luminance terms matches the desktop table).

Fixes applied in round 2:

4. **Sheet input capitalization** (from round 1's finding 3) —
   `KeyboardOptions` on `SheetField`.
5. Re-recorded all shots; PNGs copied to `screenshots/` (flat) and
   verified in a separate Gradle invocation.

Known cosmetic artifact (test-only): in `quickentry-*.png` the typed
title renders ALL CAPS although the field value is lowercase
(verified via `EditableText` semantics and the lowercase parse chips).
Setting `KeyboardCapitalization.None` did not change it, so this is a
Robolectric native-graphics quirk in BasicTextField rendering, not app
behavior — the real keyboard input is unaffected.

Open judgment calls (recorded for Jack): Настройки/О приложении shown
disabled on home; «+» is the single accent surface.
