# Design tokens (KOS-370 Phase 3-UI)

Single source: imago `default` palette — the same values desktop renders.
`imago-gpui/src/palettes.rs:40-42` (key `"default"`), token names
`imago-gpui/src/theme.rs:16-33`. Agenda's `src/theme.rs` re-exports them and
adds `BASE_RADIUS = 12` (`src/theme.rs:13`) and the 13px font base
(`text_px`, `src/theme.rs:57-59`).

## Color tokens

| Token (imago) | Light | Dark | Android use |
|---|---|---|---|
| bg | `0xf8f7f9` | `0x0d0c10` | app background |
| fg | `0x222027` | `0xe7e5ea` | primary text, icons |
| muted_fg | `0x615f67` | `0x8d8a94` | secondary text, project dot |
| border | `0xdedde0` | `0x27262a` | hairline dividers, borders |
| accent | `0x7239ca` | `0xaf94f9` | selection, checkbox fill, primary action ONLY |
| accent_fg | `0xf9f9fa` | `0x0c0a0f` | text on accent |
| accent_dim | `0x986ed7` | `0x826eb8` | accent hover/dim |
| secondary | `0xebebed` | `0x1c1b20` | subtle fill (pressed chips) |
| card | `0xf3f2f4` | `0x131216` | raised surface |
| popover | `0xfafafb` | `0x18161c` | menus, sheet surface |
| sidebar_bg | `0xf3f2f4` | `0x09080c` | home-page section surface |
| sidebar_divider | `0xe2e1e4` | `0x1b1a1e` | section separator |
| destructive | `0xce222c` | `0xfa686a` | delete, overdue text |
| warn | `0xe58212` | `0xf4a25c` | started-status ring |
| success | `0x03a14a` | `0x5bbd74` | completion states |
| qe_chip_bg | `0xd1c0ec` | `0x453c5e` | quick-entry parsed chip bg |
| qe_chip_fg | `0x5e33a1` | `0xbda8f5` | quick-entry parsed chip fg |

Derived alpha mixes used by desktop chrome (`src/chrome.rs`):
- hover bg: `rgba(FG, 0.08)`; projects header `mix(FG, 0.05, BG)`
- active row: `rgba(FG, 0.12)`; more-menu active same
- glyph alpha: 0.6 resting → 1.0 on hover/active (`chrome.rs:107` `0.6 + 0.4*t`)
- sliding highlight: `rgba(FG, 0.06)` (`chrome.rs:681`)
- task row separator: `border_mix(0.55)` — border at ~55% (`rows/task.rs`)
- row hover: `fg_mix(0.04)`

### Zeron palettes

Settings → «Тема» also offers zeron's 19 built-in theme families
(30 variants). `tools/gen_zeron_themes.py <zeron checkout>` copies the raw
seeds from `crates/theme/src/builtins.rs` into `ui/ZeronThemes.kt`;
`ui/ZeronPalette.kt` ports zeron's `variant()` / `AccentRoles::derive`
and maps each role onto the tokens above (shell → sidebarBg, dialog →
popover, accent wash → qeChipBg, ...). «Отображение» picks the light or dark
variant. A family with one variant (Dracula, Nord, ...) keeps it.

## Typography

Desktop base font is Inter, base size 13px (`theme.rs:16` `FONT_SIZE=13.0`,
`text_px` scales off it). IBM Plex Mono appears for numeric/code surfaces
(fonts dir: `IBMPlexMono-{Regular,Medium,SemiBold}.ttf`).

| Role | Desktop | Android |
|---|---|---|
| page title | ~17px semibold (M3 titleLarge 18sp kept p1) | 22sp semibold, -0.3sp letterSpacing |
| section header | 12px medium, fg alpha 0.6 (`chrome.rs:436-441`) | 12sp medium, muted |
| nav/row label | 13px, lh 15px (`chrome.rs:107`, `task.rs` row) | 15sp body |
| meta | 12px muted (`task.rs` meta block) | 12.5sp muted |
| chip/label | 11-12px | 11.5sp |

Line heights tight: desktop rows use lh ≈ size+2. Titles get slight
negative tracking (Linear convention).

## Spacing / sizing (desktop px → Android dp)

Mapping rule: 1 desktop px ≈ 1.4 dp for touch targets (a11y floor 44dp),
1:1 for purely visual offsets that must preserve the grid (icon size,
dot, divider).

| Desktop | Android |
|---|---|
| row h 32px sidebar / 36px task | min-height 48dp (touch), visual padding keeps density |
| icon 16px | 20dp optical, tinted `fg` at alpha |
| project dot 8px | 8dp, `muted_fg` |
| row padding pl10 pr8 | start 12dp, end 12dp |
| ring cell w20 ring14 | 20dp cell, 18dp ring (stroke 1.5dp) |
| content grid x11 (`chrome.rs:49`) | list content inset 16dp; ring center aligns |
| radius BASE_RADIUS 12 | surfaces 8dp (Android scale-down of r_lg≈8px used in rows), sheet 16dp top |
| border 1px | hairline (0.5dp min, `border` color) |
| sidebar top pad 40+4 | status-bar-aware padding |

## Motion

- `ease_emphasized` = cubic-bezier(0.22,1,0.36,1) — `imago-gpui/theme.rs:210-212`
  → Compose `CubicBezierEasing(0.22f,1f,0.36f,1f)`
- `ease_standard` = cubic-bezier(0.25,0.1,0.25,1) — `theme.rs:215-217`
- `ease_out_quint` = cubic-bezier(0.23,1,0.32,1) — `theme.rs:220-222`
- durations: hover 120ms, highlight slide 160ms, accordions 350ms
  (`chrome.rs:214-223`, `group_open 350`, `more_menu 350`)

## Icons (desktop SVG → VectorDrawable)

All from `$AGENDA_GPUI_DIR/assets/icons/`:

| Android id | Source file | Used for |
|---|---|---|
| ic_inbox | inbox.svg | Входящие |
| ic_calendar_01 | calendar-01.svg | Эта неделя |
| ic_calendar_02 | calendar-02.svg | Следующая неделя |
| ic_clock_01 | clock-01.svg | Потом |
| ic_book_open | book-open.svg | Архив |
| ic_delete | delete.svg | Корзина |
| ic_settings | settings.svg | Настройки (disabled) |
| ic_help_circle | help-circle.svg | О приложении (disabled) |
| ic_plus | plus.svg | quick-entry button |
| ic_arrow_left | arrow-left.svg | back in top bar |
| ic_check | check.svg | status ring check |
| ic_folder | folder.svg | Проекты header |
| ic_more_h | more-h.svg | row overflow (if needed) |
| ic_repeat | repeat.svg | recurrence meta (if needed) |
| ic_tag | tag.svg | tag meta (if needed) |

Stroke/fill and 24px viewport preserved from source SVGs; tinted at draw
time from theme tokens.

## Sidebar source map (home page parity)

- nav items + order + icons: `chrome.rs:375-385`
- projects section: `chrome.rs:389-535` (header «Проекты», status==0
  filter, 8px muted dot, no counts anywhere)
- «Другое» popover items: `chrome.rs:1054-1067` — Архив book-open,
  Корзина delete, Настройки settings, О приложении help-circle
- confirmed: no per-section counts are rendered in the sidebar (only
  `active_projects.len()` used for accordion height, `chrome.rs:518`)
