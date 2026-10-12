# Design language — Linear + desktop Agenda (KOS-370 Phase 3-UI)

Public sources only: linear.app, linear.app/method, linear.app/mobile,
linear.app/now (blog/changelog), Linear Method pages. No APK inspection.

## Principles applied

1. **Purpose-built, not generic.** Linear Method's first rule is "build for
   purpose" — the app does a few things with full attention rather than
   exposing every surface. Home page mirrors that: a calm list of exactly
   the sections the desktop sidebar shows. (linear.app/method/introduction)

2. **Calm surface, quiet chrome.** Linear's UI reads as flat surfaces with
   hairline separators, muted secondary text, and color reserved for
   meaning (status, selection). We use the imago `default` palette:
   near-white `0xf8f7f9` / near-black `0x0d0c10` backgrounds, `muted_fg`
   for metadata, `border` hairlines, and accent purple only where it means
   something (checkbox fill, selection, primary action). Karri Saarinen's
   "Output isn't design" (linear.app/now, Apr 17 2026) argues for restraint
   — decoration that doesn't carry state is noise.

3. **Density with rhythm.** Linear Mobile is described as "ultraportable…
   light in weight and strong in performance" (linear.app/mobile). Rows are
   dense (44-48dp touch targets, tight lh, 12.5sp meta) but each row is a
   single horizontal rhythm: ring — title — meta, aligned to one left grid
   line (desktop `CONTENT_GRID_X`, chrome.rs:49).

4. **Speed is a feature.** Linear builds the composer "with an obsessive
   focus on speed" (linear.app/mobile). Instant tap feedback: press state
   lands the same frame (`fg 8%` highlight like the desktop hover), writes
   are optimistic, no spinners after first load.

5. **Motion is short and directional.** Desktop accordions run 350ms
   emphasized cubic-bezier(0.22,1,0.36,1); hover slides 160ms. Same on
   Android: nothing bounces, nothing lingers; transitions always answer
   "where did that go" (drill-in slides right, back slides left).

6. **Dark is designed, not inverted.** Linear ships a true dark theme.
   The imago dark palette is a separate hand-tuned table (different hues,
   not inverted lightness) — we take it verbatim, including the darker
   `sidebar_bg` step used for home sections.

7. **Typography does the hierarchy.** Inter everywhere; hierarchy comes
   from weight + muted color, not size jumps: 12sp medium section headers,
   15sp rows, 30sp bold page titles with slight negative tracking.
   IBM Plex Mono reserved for numeric/code surfaces as on desktop.

## What we deliberately did NOT copy

- Linear's issue fields (assignee, labels, estimates) — no parity target.
- Todoist-style red accents, Material FAB, cards-with-shadow, M3 nav
  drawer — explicitly banned by the brief; surfaces stay flat.
- Per-section counts — desktop sidebar shows none.
