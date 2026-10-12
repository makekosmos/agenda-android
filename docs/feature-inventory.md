# Desktop Agenda feature inventory (KOS-370, unit 1)

Source: `makekosmos/agenda-gpui` worktree `$AGENDA_GPUI_DIR`,
branch `kos-370-android` (based on `origin/main`). Paths are relative to that
repo; line numbers are post-extraction (agenda-core is now
`crates/agenda-core/`). This is the parity checklist for the Android app.

## Smart lists (routes)

`Route` enum, `src/app.rs:26`. `SmartList` enum + `filter_idx`,
`crates/agenda-core/src/filters.rs`. Page dispatch `src/pages/mod.rs:48`.

- Inbox «Входящие» — `src/pages/mod.rs:55` → `board_page(SmartList::Inbox)`;
  predicate `is_inbox` (status Inbox, not trashed), `filters.rs`.
- Week «Эта неделя» — `mod.rs:58` → `SmartList::Week`: overdue block sorted by
  date then sort_order, then in-week tasks + this week's completions
  (`is_overdue`, `in_week_list`, `completed_in_week`), `filters.rs`.
- Next week «Следующая неделя» — `mod.rs:59` → `SmartList::NextWeek`
  (`is_due_in_bounds` over `next_week_bounds`), `filters.rs`.
- Someday «Потом» — `mod.rs:66` → `SmartList::Someday` (`is_deferred`:
  Deferred status or `is_someday`, active only), `filters.rs`.
- Logbook «Архив» — `src/pages/logbook.rs:67` → `SmartList::Logbook`
  (`is_archived`, ordered by parsed `completed_at` instant desc).
- Trash «Корзина» — `src/pages/trash.rs:4` → `SmartList::Trash`
  (`is_trashed`, ordered by `created_at` instant desc).

There is no separate Today view; «Сегодня» is a section inside Week
(`date_group_label`, `src/pages/mod.rs:172`; row date chip
`src/pages/rows/task.rs`).

## Task model

`Todo` struct — `crates/agenda-core/src/types.rs`: id, title, notes,
priority (0–3), scheduled_date, deadline, reminder_date, is_today («эта
неделя» flag), is_evening, is_someday, status
(Inbox/Todo/Started/Deferred/Done/Canceled), system_kind (service tasks),
is_completed + completed_at, is_cancelled, is_trashed, created_at,
heading_id, project_id, area_id, tag_ids, checklist items, recurrence rule,
billable/price, significance («значимость» 1–10),
sort_order. `Project` (status 0/1/2, deadline, area_id, sort_order) and `Tag`
(title + color) in the same file. Engine object mapping
`crates/agenda-core/src/mapping.rs` (`read`/`write_at`, type
`com.kosmos.task` v1.1.0; project mapping `src/store/projects.rs`).

## Task operations

- Complete/cancel cycle: `complete_todo` `src/app.rs:1618` — stamps
  `completed_at` (RFC 3339), spawns next recurrence instance via
  `next_recurrence_date` (`crates/agenda-core/src/recurrence.rs`),
  clears reminder; `set_todo_status` `src/app.rs:1591`.
- Edit via task panel: `open_task_panel` `src/app.rs:1488`,
  `panel_persist` `src/app.rs:1496`.
- Move to week («Эта неделя» flag): `move_to_week` `src/app.rs:1661`.
- Assign project / set date / trash / restore: `run_menu_action`
  `src/app.rs:1681` (`MenuAction` enum `src/app.rs:65`), property dropdown
  `src/pages/dropdown.rs` («Эта неделя», «Следующая неделя», «Без даты»).
- Save pipeline with `before` conflict check: `save_todo`
  `src/app/storage/save.rs:38`, `Engine::save` `src/store.rs:186`.
- No undo feature exists on desktop (grep: no undo code path).
- Reminders: `reminder_date` field is stored/synced, but the desktop app has
  no notification scheduler — nothing fires it.

## Quick entry

Overlay open/close + focus: `src/app.rs:745`, render
`src/pages/quick_entry.rs`. Save: `quick_entry_save` `src/app.rs:1400` —
date words parsed by `parse_quick_entry_capture` (Russian «сегодня»,
«завтра», «послезавтра», weekday names + declensions, «через N дней/недель/
месяцев/лет», «через час» → today), `@проект` mention via
`parse_project_mention`, both in `crates/agenda-core/src/quick_entry.rs`.
Property chips: `src/pages/quick_entry/properties.rs`,
`src/pages/quick_entry/fields.rs`. Explicit dropdown date overrides parsing.

## Quick search

`render_quick_search` `src/pages/quick_search.rs:8`, matcher
`quick_matches` `src/app.rs:1758` (title match over tasks + projects;
opens task panel or navigates to project).

## Board options, sort, group

`BoardOpts` `src/app.rs:42`; per-view persistence key
`view_options_key` `src/app.rs:871`. Sort: default/date/priority/title
(`SortKey`, `sort_idx`, `crates/agenda-core/src/filters.rs`); group:
none/project/date (`GroupKey`, `src/pages/board.rs:38`). Options popover
`src/pages/options.rs:5`.

## Statistics

`src/pages/statistics.rs:7` — totals, 7/30-day counts, current/best streak,
per-year heatmap from `completed_day` (`filters.rs`); clipboard copy
`statistics/clipboard.rs`, share card `statistics/share.rs`.

## Calendar

No calendar view exists; dates appear as list sections/chips only.

## Projects, tags, areas

Sidebar project list + context menu (rename/archive/delete/restore):
`src/chrome.rs:393`+. Project page `src/pages/project.rs:6` (active + done
sections, archived projects rows in Logbook `src/pages/logbook.rs:10`).
Create/delete/area assignment `src/app/projects.rs`, Engine ops
`src/store/projects.rs`. Tags are label + color only; areas exist as ids
(`area_id`) with no dedicated UI.

## Theme / appearance

Palettes + accent override: `src/theme.rs`, `src/palettes.rs` (delegates to
`imago-gpui`). Persisted appearance patch (theme, accent, font size,
material, ui style): `src/appearance.rs`. Settings pages «Отображение» /
«Мыслетопливо» / «Энергосбережение» / «О приложении»:
`src/pages/settings.rs`, `src/pages/dev.rs:13`.

## Shell / transport

Window chrome, sidebar, drag-drop between lists: `src/chrome.rs`,
`src/pages/rows.rs`. Engine transport reads `engine.lock.json`, POST
`/v1/rpc`: `src/store/transport.rs`; worker channel `src/store.rs`.
Demo mode (`AGENDA_DEMO`) + dev task generator: `src/seed.rs`,
`src/seed/gen.rs`.

## a11y / tests

`#[gpui::test]` suites `src/*_tests.rs`, a11y snapshot
`snapshots/a11y-inbox.txt`, Engine contract fixtures `fixtures/engine/`
driven by `src/store/payload_fixtures.rs`.
