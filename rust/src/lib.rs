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
use serde_json::Value;

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
