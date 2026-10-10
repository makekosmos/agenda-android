//! agenda-mobile: the single `.so` the Android app links. It re-exports
//! engine-mobile's UniFFI surface (`MobileEngine`, `ChangeListener`,
//! `ChangeSubscription`, sync ops) and adds thin UniFFI wrappers over the
//! agenda-core rules Android needs now: quick-entry date parsing, smart-list
//! filtering, and the `com.kosmos.task` object mapping agenda-gpui writes.
//!
//! The clock is a host concern: every date-aware export takes `today`
//! (`YYYY-MM-DD`) and `utc_offset_seconds` from Kotlin, never reads `Local::now`.

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

/// Convert an Engine object (`get_object` / `list_objects_by_type` payload)
/// into the agenda `Todo` JSON shape (agenda-core `mapping::read`).
#[uniffi::export]
pub fn task_from_engine_object(object_json: String) -> Result<String, AgendaError> {
    let object: Value =
        serde_json::from_str(&object_json).map_err(|e| AgendaError::Invalid(e.to_string()))?;
    let todo = agenda_core::mapping::read(&object).map_err(AgendaError::Mapping)?;
    serde_json::to_string(&todo).map_err(|e| AgendaError::Mapping(e.to_string()))
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

/// «Сегодня» indices (agenda-core `today_idx`): the Week pipeline narrowed
/// to overdue + due-today. A distinct UniFFI export rather than a new
/// `SmartList` so the desktop enum stays the single source of truth.
#[uniffi::export]
pub fn today_list_indices(
    todos_json: String,
    today: String,
    utc_offset_seconds: i32,
) -> Result<Vec<u32>, AgendaError> {
    let day = local_day(&today, utc_offset_seconds)?;
    let todos = parse_todos(&todos_json)?;
    Ok(agenda_core::today_idx(&todos, &day)
        .into_iter()
        .map(|i| i as u32)
        .collect())
}

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

/// Tag filter membership: live tasks carrying the tag (trash hidden), in
/// `sort_order` board order.
#[uniffi::export]
pub fn tag_task_indices(tag_id: String, todos_json: String) -> Result<Vec<u32>, AgendaError> {
    let todos = parse_todos(&todos_json)?;
    Ok(agenda_core::tag_page_idx(&tag_id, &todos)
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
