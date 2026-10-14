//! agenda-mobile: the single `.so` the Android app links. It re-exports
//! engine-mobile's UniFFI surface (`MobileEngine`, `ChangeListener`,
//! `ChangeSubscription`, sync ops) and adds thin UniFFI wrappers over the
//! agenda-core rules Android needs now: quick-entry date parsing, smart-list
//! filtering, and the `core.task` object mapping agenda-gpui writes.
//!
//! The clock is a host concern: every date-aware export takes `today`
//! (`YYYY-MM-DD`) and `utc_offset_seconds` from Kotlin, never reads `Local::now`.

use std::collections::HashMap;

use agenda_core::mapping::{Link, PROJECT_LINK};
use agenda_core::{LocalDay, SmartList, Todo};
use chrono::{FixedOffset, NaiveDate};
use serde_json::{json, Value};

uniffi::setup_scaffolding!();
// Force the linker to keep engine-mobile's scaffolding symbols and metadata
// inside this cdylib so uniffi-bindgen library mode sees both namespaces.
engine_mobile::uniffi_reexport_scaffolding!();

pub use engine_mobile::{ChangeListener, ChangeSubscription, EngineError, MobileEngine};

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum AgendaError {
    #[error("{0}")]
    Invalid(String),
    #[error("{0}")]
    Mapping(String),
}

#[derive(uniffi::Record)]
pub struct QuickEntryParse {
    pub title: String,
    pub scheduled_date: Option<String>,
}

#[derive(uniffi::Enum)]
pub enum SmartListName {
    Inbox,
    Week,
    NextWeek,
    Someday,
    Logbook,
    Trash,
}

impl From<SmartListName> for SmartList {
    fn from(list: SmartListName) -> Self {
        match list {
            SmartListName::Inbox => SmartList::Inbox,
            SmartListName::Week => SmartList::Week,
            SmartListName::NextWeek => SmartList::NextWeek,
            SmartListName::Someday => SmartList::Someday,
            SmartListName::Logbook => SmartList::Logbook,
            SmartListName::Trash => SmartList::Trash,
        }
    }
}

fn local_day(today: &str, utc_offset_seconds: i32) -> Result<LocalDay, AgendaError> {
    let today = NaiveDate::parse_from_str(today, "%Y-%m-%d")
        .map_err(|e| AgendaError::Invalid(format!("today must be YYYY-MM-DD: {e}")))?;
    let offset = FixedOffset::east_opt(utc_offset_seconds)
        .ok_or_else(|| AgendaError::Invalid("utc_offset_seconds out of range".into()))?;
    Ok(LocalDay::new(today, offset))
}

fn parse_todos(todos_json: &str) -> Result<Vec<Todo>, AgendaError> {
    serde_json::from_str(todos_json).map_err(|e| AgendaError::Invalid(e.to_string()))
}

/// Quick-entry parse (agenda-core `parse_quick_entry_capture`): strips the
/// Russian date keyword from `title` and returns the resolved day key.
/// `selected` is an explicit dropdown date that wins over parsing.
#[uniffi::export]
pub fn parse_quick_entry(
    title: String,
    selected: Option<String>,
    today: String,
    utc_offset_seconds: i32,
) -> Result<QuickEntryParse, AgendaError> {
    let day = local_day(&today, utc_offset_seconds)?;
    let (title, date) = agenda_core::parse_quick_entry_capture(&title, selected, &day);
    Ok(QuickEntryParse {
        title,
        scheduled_date: date,
    })
}

/// Smart-list membership + order (agenda-core `filter_idx`): indices into the
/// `todos_json` array, already in display order.
#[uniffi::export]
pub fn smart_list_indices(
    list: SmartListName,
    todos_json: String,
    today: String,
    utc_offset_seconds: i32,
) -> Result<Vec<u32>, AgendaError> {
    let day = local_day(&today, utc_offset_seconds)?;
    let todos = parse_todos(&todos_json)?;
    Ok(agenda_core::filter_idx(list.into(), &todos, &day)
        .into_iter()
        .map(|i| i as u32)
        .collect())
}

/// Project membership of every task, keyed by task id. The Engine never puts
/// links on an object: a task's project is a `project` object link, listed by
/// `list_object_links`. Desktop `project_links` groups that payload the same way.
fn project_links_by_task(links_json: &str) -> Result<HashMap<String, Vec<Link>>, AgendaError> {
    let links: Vec<Value> =
        serde_json::from_str(links_json).map_err(|e| AgendaError::Invalid(e.to_string()))?;
    let mut by_task: HashMap<String, Vec<Link>> = HashMap::new();
    for link in links
        .iter()
        .filter(|l| l["linkType"].as_str() == Some(PROJECT_LINK))
    {
        let (Some(id), Some(source), Some(target)) = (
            link["id"].as_str(),
            link["sourceObjectId"].as_str(),
            link["targetObjectId"].as_str(),
        ) else {
            return Err(AgendaError::Mapping("link without id or endpoints".into()));
        };
        by_task.entry(source.to_owned()).or_default().push(Link {
            id: id.to_owned(),
            kind: PROJECT_LINK.to_owned(),
            target: target.to_owned(),
        });
    }
    Ok(by_task)
}

/// One `core.task` object read with its own links (agenda-core `mapping::read`).
fn read_task(object: &Value, by_task: &HashMap<String, Vec<Link>>) -> Result<Todo, AgendaError> {
    let links = object["id"]
        .as_str()
        .and_then(|id| by_task.get(id))
        .map_or(&[][..], Vec::as_slice);
    agenda_core::mapping::read(object, links).map_err(AgendaError::Mapping)
}

/// Convert an Engine object (`get_object` payload) into the agenda `Todo`
/// JSON shape (agenda-core `mapping::read`). `links_json` is the
/// `list_object_links` payload, which carries the task's project.
#[uniffi::export]
pub fn task_from_engine_object(
    object_json: String,
    links_json: String,
) -> Result<String, AgendaError> {
    let object: Value =
        serde_json::from_str(&object_json).map_err(|e| AgendaError::Invalid(e.to_string()))?;
    let by_task = project_links_by_task(&links_json)?;
    todo_to_json(&read_task(&object, &by_task)?)
}

/// The `project` object link that places a task in a project (desktop
/// `relink_project`). The id comes from the endpoints, so the same placement
/// written twice, or from two devices, is one row.
#[uniffi::export]
pub fn project_link_json(task_id: String, project_id: String, now: String) -> String {
    json!({
        "id": format!("lnk:{task_id}:{PROJECT_LINK}:{project_id}"),
        "sourceObjectId": task_id,
        "targetObjectId": project_id,
        "linkType": PROJECT_LINK,
        "createdAt": now,
    })
    .to_string()
}

/// Build the Engine object `upsert_object` expects from a `Todo` JSON
/// (agenda-core `mapping::write_at`). `existing_object_json` retains fields
/// this app does not understand; `before_todo_json` limits the diff to
/// changed fields. `now` is an RFC 3339 stamp from the host clock.
#[uniffi::export]
pub fn task_to_engine_object(
    todo_json: String,
    existing_object_json: Option<String>,
    before_todo_json: Option<String>,
    now: String,
    today: String,
    utc_offset_seconds: i32,
) -> Result<String, AgendaError> {
    let day = local_day(&today, utc_offset_seconds)?;
    let todo: Todo =
        serde_json::from_str(&todo_json).map_err(|e| AgendaError::Invalid(e.to_string()))?;
    let existing: Value = match existing_object_json.as_deref() {
        Some(json) => {
            serde_json::from_str(json).map_err(|e| AgendaError::Invalid(e.to_string()))?
        }
        None => Value::Null,
    };
    let before: Option<Todo> = before_todo_json
        .map(|json| serde_json::from_str(&json))
        .transpose()
        .map_err(|e| AgendaError::Invalid(e.to_string()))?;
    let object = agenda_core::mapping::write_at(existing, before.as_ref(), &todo, &now, &day)
        .map_err(AgendaError::Mapping)?;
    serde_json::to_string(&object).map_err(|e| AgendaError::Mapping(e.to_string()))
}

/// Fresh `Todo` JSON (agenda-core `new_todo`) so Kotlin never hand-builds the
/// field set the writer expects.
#[uniffi::export]
pub fn new_task_json(id: String, title: String) -> Result<String, AgendaError> {
    serde_json::to_string(&agenda_core::new_todo(id, &title))
        .map_err(|e| AgendaError::Mapping(e.to_string()))
}

// ---------------------------------------------------------------------------
// Phase 1 read parity: projects, tags, per-task view fields, Today list.
// Same rule as above — every date decision happens inside agenda-core.
// ---------------------------------------------------------------------------

/// Project page membership (desktop `project_page`): tasks in the project,
/// not trashed, not archived — sorted by `sort_order` like the default
/// board order.
#[uniffi::export]
pub fn project_task_indices(
    project_id: String,
    todos_json: String,
    today: String,
    utc_offset_seconds: i32,
) -> Result<Vec<u32>, AgendaError> {
    let day = local_day(&today, utc_offset_seconds)?;
    let todos = parse_todos(&todos_json)?;
    Ok(agenda_core::project_page_idx(&project_id, &todos, &day)
        .into_iter()
        .map(|i| i as u32)
        .collect())
}

/// Per-row date fields the UI needs (desktop `date_group_label` + row
/// chip): the canonical `task_date` key, a display label («Сегодня» or
/// «18 сент»), and whether the task is overdue. Kotlin formats nothing.
#[uniffi::export]
pub fn task_date_view(
    todo_json: String,
    today: String,
    utc_offset_seconds: i32,
) -> Result<String, AgendaError> {
    let day = local_day(&today, utc_offset_seconds)?;
    let todo: Todo =
        serde_json::from_str(&todo_json).map_err(|e| AgendaError::Invalid(e.to_string()))?;
    Ok(json!(date_view_json(&todo, &day)).to_string())
}

/// Instant fields rendered as local time (date-time strings carry an
/// offset; bare dates are shown via `fmt_day_month`). Returns null when the
/// field is unset.
fn instant_label(value: &Option<String>, day: &LocalDay) -> Value {
    let Some(v) = value.as_deref() else {
        return Value::Null;
    };
    if let Ok(dt) = chrono::DateTime::parse_from_rfc3339(v) {
        let local = dt.with_timezone(&day.offset);
        return json!(local.format("%Y-%m-%d %H:%M").to_string());
    }
    match agenda_core::date_only(&Some(v.to_string()), day) {
        Some(key) => json!(agenda_core::fmt_day_month(&key)),
        None => json!(v),
    }
}

/// The JSON `task_date_view`/`task_date_views` return for one task.
fn date_view_json(todo: &Todo, day: &LocalDay) -> Value {
    let (date, conflict) = agenda_core::task_date(todo, day);
    let label = match date.as_deref() {
        None => "Без даты".to_string(),
        Some(v) if v == day.today_key() => "Сегодня".to_string(),
        Some(v) => agenda_core::fmt_day_month(v),
    };
    json!({
        "dateKey": date,
        "label": label,
        "conflict": conflict,
        "overdue": agenda_core::is_overdue(todo, day),
        "deadlineLabel": instant_label(&todo.deadline, day),
        "reminderLabel": instant_label(&todo.reminder_date, day),
        "completedLabel": instant_label(&todo.completed_at, day),
        "createdLabel": instant_label(&Some(todo.created_at.clone()), day),
    })
}

/// Batch twin of `task_date_view`: one JSON array aligned with `todos_json`,
/// so a board refresh costs one FFI call instead of N.
#[uniffi::export]
pub fn task_date_views(
    todos_json: String,
    today: String,
    utc_offset_seconds: i32,
) -> Result<String, AgendaError> {
    let day = local_day(&today, utc_offset_seconds)?;
    let todos = parse_todos(&todos_json)?;
    let views: Vec<Value> = todos.iter().map(|t| date_view_json(t, &day)).collect();
    serde_json::to_string(&views).map_err(|e| AgendaError::Invalid(e.to_string()))
}

/// Convert a `com.kosmos.project` Engine object into the `Project` JSON
/// shape (agenda-core `projects::read_project`); soft-deleted objects are
/// filtered by the caller before this.
#[uniffi::export]
pub fn project_from_engine_object(object_json: String) -> Result<String, AgendaError> {
    let object: Value =
        serde_json::from_str(&object_json).map_err(|e| AgendaError::Invalid(e.to_string()))?;
    serde_json::to_string(&agenda_core::projects::read_project(&object))
        .map_err(|e| AgendaError::Mapping(e.to_string()))
}

/// Build the `com.kosmos.project` Engine object for a `Project` JSON
/// (agenda-core `projects::project_object`).
#[uniffi::export]
pub fn project_to_engine_object(
    project_json: String,
    existing_object_json: Option<String>,
    now: String,
) -> Result<String, AgendaError> {
    let project: agenda_core::Project =
        serde_json::from_str(&project_json).map_err(|e| AgendaError::Invalid(e.to_string()))?;
    let existing: Value = match existing_object_json.as_deref() {
        Some(j) => serde_json::from_str(j).map_err(|e| AgendaError::Invalid(e.to_string()))?,
        None => Value::Null,
    };
    serde_json::to_string(&agenda_core::projects::project_object(
        &existing, &project, &now,
    ))
    .map_err(|e| AgendaError::Mapping(e.to_string()))
}

/// Read a `com.kosmos.agenda.references` object: `(legacy_projects, tags)`
/// as `[projects_json, tags_json]` (agenda-core `read_references`). The
/// caller merges legacy projects against known canonical ids like desktop.
#[uniffi::export]
pub fn references_from_engine_object(object_json: String) -> Result<String, AgendaError> {
    let object: Value =
        serde_json::from_str(&object_json).map_err(|e| AgendaError::Invalid(e.to_string()))?;
    let (projects, tags) =
        agenda_core::projects::read_references(&object).map_err(AgendaError::Mapping)?;
    Ok(json!([projects, tags]).to_string())
}

/// Convert a `list_objects_by_type` payload (array of Engine objects) into
/// an array of agenda `Todo` JSON — one FFI call for the whole board.
/// `links_json` is the `list_object_links` payload (see `task_from_engine_object`).
#[uniffi::export]
pub fn tasks_from_engine_objects(
    objects_json: String,
    links_json: String,
) -> Result<String, AgendaError> {
    let objects: Vec<Value> =
        serde_json::from_str(&objects_json).map_err(|e| AgendaError::Invalid(e.to_string()))?;
    let by_task = project_links_by_task(&links_json)?;
    let todos: Vec<String> = objects
        .iter()
        .map(|o| read_task(o, &by_task).and_then(|t| todo_to_json(&t)))
        .collect::<Result<_, _>>()?;
    Ok(json!(todos).to_string())
}

/// The whole read board in one FFI call: per-list index arrays, per-project
/// membership, and per-task date views — every rule evaluated inside
/// agenda-core on a single `todos_json` parse.
#[uniffi::export]
pub fn board_lists(
    todos_json: String,
    project_ids_json: String,
    today: String,
    utc_offset_seconds: i32,
) -> Result<String, AgendaError> {
    let day = local_day(&today, utc_offset_seconds)?;
    let todos = parse_todos(&todos_json)?;
    let project_ids: Vec<String> =
        serde_json::from_str(&project_ids_json).map_err(|e| AgendaError::Invalid(e.to_string()))?;
    let idx = |v: Vec<usize>| v.into_iter().map(|i| i as u32).collect::<Vec<_>>();
    let lists = json!({
        "inbox": idx(agenda_core::filter_idx(SmartList::Inbox, &todos, &day)),
        "week": idx(agenda_core::filter_idx(SmartList::Week, &todos, &day)),
        "nextWeek": idx(agenda_core::filter_idx(SmartList::NextWeek, &todos, &day)),
        "someday": idx(agenda_core::filter_idx(SmartList::Someday, &todos, &day)),
        "logbook": idx(agenda_core::filter_idx(SmartList::Logbook, &todos, &day)),
        "trash": idx(agenda_core::filter_idx(SmartList::Trash, &todos, &day)),
    });
    let project_tasks: serde_json::Map<String, Value> = project_ids
        .iter()
        .map(|id| {
            (
                id.clone(),
                json!(idx(agenda_core::project_page_idx(id, &todos, &day))),
            )
        })
        .collect();
    let date_views: Vec<Value> = todos.iter().map(|t| date_view_json(t, &day)).collect();
    Ok(json!({
        "lists": lists,
        "projectTasks": project_tasks,
        "dateViews": date_views,
    })
    .to_string())
}

// ---------------------------------------------------------------------------
// Phase 2 write parity: every mutation below calls the same agenda-core
// action the desktop `app.rs` calls — Kotlin only shuttles JSON and clocks.
// ---------------------------------------------------------------------------

fn parse_todo(todo_json: &str) -> Result<Todo, AgendaError> {
    serde_json::from_str(todo_json).map_err(|e| AgendaError::Invalid(e.to_string()))
}

fn todo_to_json(todo: &Todo) -> Result<String, AgendaError> {
    serde_json::to_string(todo).map_err(|e| AgendaError::Mapping(e.to_string()))
}

fn parse_status(status: &str) -> Result<agenda_core::Status, AgendaError> {
    serde_json::from_str(&format!("\"{status}\""))
        .map_err(|e| AgendaError::Invalid(format!("bad status {status}: {e}")))
}

/// Quick-entry save (desktop `quick_entry_save`): the agenda-core decision
/// applied to a fresh `new_todo`, with notes, board-append `sort_order` and
/// the host `created_at` stamp. Returns the full Todo JSON ready for
/// `task_to_engine_object`.
#[uniffi::export]
#[allow(clippy::too_many_arguments)]
pub fn quick_entry_new_task(
    id: String,
    raw_title: String,
    notes: Option<String>,
    selected_date: Option<String>,
    week: bool,
    someday: bool,
    project_id: Option<String>,
    project_touched: bool,
    projects_json: String,
    sort_order: i32,
    created_at: String,
    today: String,
    utc_offset_seconds: i32,
) -> Result<String, AgendaError> {
    let day = local_day(&today, utc_offset_seconds)?;
    let projects: Vec<agenda_core::Project> =
        serde_json::from_str(&projects_json).map_err(|e| AgendaError::Invalid(e.to_string()))?;
    let d = agenda_core::quick_entry_decision(
        raw_title.trim(),
        selected_date,
        week,
        someday,
        project_id,
        project_touched,
        &projects,
        &day,
    );
    let mut t = agenda_core::new_todo(id, &d.title);
    t.notes = notes.and_then(|n| {
        let n = n.trim().to_string();
        (!n.is_empty()).then_some(n)
    });
    t.scheduled_date = d.scheduled_date;
    t.project_id = d.project_id;
    t.status = d.status;
    t.is_today = d.is_today;
    t.is_someday = d.is_someday;
    t.sort_order = sort_order;
    t.created_at = created_at;
    todo_to_json(&t)
}

/// Checkbox toggle, done arm (desktop `complete_todo`): Done fields only —
/// recurrence spawning is `task_next_recurrence`, mirroring save_completion.
#[uniffi::export]
pub fn task_complete(todo_json: String, now: String) -> Result<String, AgendaError> {
    let mut t = parse_todo(&todo_json)?;
    agenda_core::complete_fields(&mut t, &now);
    todo_to_json(&t)
}

/// Checkbox toggle, un-complete arm + status transitions (desktop
/// `set_todo_status` non-Done branch).
#[uniffi::export]
pub fn task_set_status(
    todo_json: String,
    status: String,
    now: String,
) -> Result<String, AgendaError> {
    let mut t = parse_todo(&todo_json)?;
    agenda_core::set_status(&mut t, parse_status(&status)?, &now);
    todo_to_json(&t)
}

/// The next instance `complete_todo` writes beside the completed task —
/// None when the recurrence rule yields no date.
#[uniffi::export]
pub fn task_next_recurrence(
    before_todo_json: String,
    completed_todo_json: String,
    new_id: String,
    sort_order: i32,
    now: String,
    today: String,
    utc_offset_seconds: i32,
) -> Result<Option<String>, AgendaError> {
    let day = local_day(&today, utc_offset_seconds)?;
    let before = parse_todo(&before_todo_json)?;
    let completed = parse_todo(&completed_todo_json)?;
    agenda_core::next_recurrence_todo(&before, &completed, &day, new_id, sort_order, &now)
        .map(|t| todo_to_json(&t))
        .transpose()
}

/// «На неделю» (desktop `move_to_week`).
#[uniffi::export]
pub fn task_move_to_week(todo_json: String) -> Result<String, AgendaError> {
    let mut t = parse_todo(&todo_json)?;
    agenda_core::move_to_week(&mut t);
    todo_to_json(&t)
}

/// Project dropdown (desktop `move_to_project` / `SetProject`).
#[uniffi::export]
pub fn task_move_to_project(
    todo_json: String,
    project_id: Option<String>,
) -> Result<String, AgendaError> {
    let mut t = parse_todo(&todo_json)?;
    agenda_core::move_to_project(&mut t, project_id);
    todo_to_json(&t)
}

/// Date dropdown (desktop `SetDate`): pick or clear the scheduled date.
#[uniffi::export]
pub fn task_set_scheduled(todo_json: String, date: Option<String>) -> Result<String, AgendaError> {
    let mut t = parse_todo(&todo_json)?;
    agenda_core::set_scheduled_date(&mut t, date);
    todo_to_json(&t)
}

/// Trash / restore (desktop `TrashTodo` / `RestoreTodo`).
#[uniffi::export]
pub fn task_set_trashed(todo_json: String, trashed: bool) -> Result<String, AgendaError> {
    let mut t = parse_todo(&todo_json)?;
    agenda_core::set_trashed(&mut t, trashed);
    todo_to_json(&t)
}

/// Panel title/notes edit (desktop `panel_persist`).
#[uniffi::export]
pub fn task_edit_title_notes(
    todo_json: String,
    title: String,
    notes: Option<String>,
) -> Result<String, AgendaError> {
    let mut t = parse_todo(&todo_json)?;
    let notes = notes.and_then(|n| {
        let n = n.trim().to_string();
        (!n.is_empty()).then_some(n)
    });
    agenda_core::edit_title_notes(&mut t, &title, notes);
    todo_to_json(&t)
}

/// `task_status` as a lowercase string — the checkbox toggle decides
/// done-vs-complete exactly like desktop `MenuAction::CompleteTodo`.
#[uniffi::export]
pub fn task_status_name(todo_json: String) -> Result<String, AgendaError> {
    let t = parse_todo(&todo_json)?;
    serde_json::to_string(&agenda_core::task_status(&t))
        .map(|s| s.trim_matches('"').to_string())
        .map_err(|e| AgendaError::Mapping(e.to_string()))
}

/// Quick-entry draft preview (the chips the sheet shows while typing):
/// the agenda-core decision fields only — the UI renders, never decides.
#[uniffi::export]
#[allow(clippy::too_many_arguments)]
pub fn quick_entry_preview(
    raw_title: String,
    selected_date: Option<String>,
    week: bool,
    someday: bool,
    project_id: Option<String>,
    project_touched: bool,
    projects_json: String,
    today: String,
    utc_offset_seconds: i32,
) -> Result<String, AgendaError> {
    let day = local_day(&today, utc_offset_seconds)?;
    let projects: Vec<agenda_core::Project> =
        serde_json::from_str(&projects_json).map_err(|e| AgendaError::Invalid(e.to_string()))?;
    let d = agenda_core::quick_entry_decision(
        raw_title.trim(),
        selected_date,
        week,
        someday,
        project_id,
        project_touched,
        &projects,
        &day,
    );
    Ok(json!({
        "title": d.title,
        "scheduledDate": d.scheduled_date,
        "projectId": d.project_id,
        "status": d.status,
        "isToday": d.is_today,
        "isSomeday": d.is_someday,
    })
    .to_string())
}

/// Date property labels exactly as desktop renders them: «Сегодня» for the
/// current day, «Следующая» for next Monday, «18 сент» otherwise.
#[uniffi::export]
pub fn day_label(
    date: String,
    today: String,
    utc_offset_seconds: i32,
) -> Result<String, AgendaError> {
    let day = local_day(&today, utc_offset_seconds)?;
    Ok(if date == day.today_key() {
        "Сегодня".to_string()
    } else if Some(&date) == day.next_monday_key().as_ref() {
        "Следующая".to_string()
    } else {
        agenda_core::fmt_day_month(&date)
    })
}

/// The «Следующая неделя» target — desktop `next_monday_key`.
#[uniffi::export]
pub fn next_monday_key(today: String, utc_offset_seconds: i32) -> Result<String, AgendaError> {
    let day = local_day(&today, utc_offset_seconds)?;
    day.next_monday_key()
        .ok_or_else(|| AgendaError::Invalid("no next monday".into()))
}

/// Typo-tolerant title search (frizbee, one typo, case-insensitive, ё read
/// as е): indices into `titles`, best match first.
#[uniffi::export]
pub fn fuzzy_search(query: String, titles: Vec<String>) -> Vec<u32> {
    let fold = |s: &str| s.replace('ё', "е").replace('Ё', "Е");
    let config = frizbee::Config {
        max_typos: Some(1),
        casing: frizbee::CaseMatching::Ignore,
        ..frizbee::Config::default()
    };
    let titles: Vec<String> = titles.iter().map(|t| fold(t)).collect();
    frizbee::Matcher::new(&fold(&query), &config)
        .match_list(&titles)
        .iter()
        .map(|m| m.index)
        .collect()
}
