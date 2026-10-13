//! Host proof for the agenda-mobile surface: build a task object through
//! `task_to_engine_object`, upsert it through `MobileEngine::call`, read it
//! back through `task_from_engine_object`, filter it through
//! `smart_list_indices`, and confirm the quick-entry parse resolves dates.

use serde_json::{json, Value};

const TODAY: &str = "2026-10-10";

fn new_task(id: &str, title: &str) -> String {
    agenda_mobile::new_task_json(id.to_string(), title.to_string()).expect("new task json")
}

fn to_object(todo_json: &str) -> String {
    agenda_mobile::task_to_engine_object(
        todo_json.to_string(),
        None,
        None,
        "2026-10-10T12:00:00+00:00".to_string(),
        TODAY.to_string(),
        0,
    )
    .expect("task to engine object")
}

fn call(engine: &agenda_mobile::MobileEngine, op: &str, params: Value) -> Value {
    let response: Value = serde_json::from_str(
        &engine
            .call(op.to_string(), params.to_string())
            .expect("call transport"),
    )
    .expect("response json");
    assert!(
        response["ok"].as_bool().unwrap_or(false),
        "{op} rejected: {}",
        response["error"]
    );
    response["data"].clone()
}

#[test]
fn quick_entry_resolves_russian_dates() {
    let parsed = agenda_mobile::parse_quick_entry(
        "позвонить маме завтра".to_string(),
        None,
        TODAY.to_string(),
        0,
    )
    .expect("parse");
    assert_eq!(parsed.title, "позвонить маме");
    assert_eq!(parsed.scheduled_date.as_deref(), Some("2026-10-11"));

    let parsed = agenda_mobile::parse_quick_entry(
        "позвонить маме".to_string(),
        Some("2026-12-25".to_string()),
        TODAY.to_string(),
        0,
    )
    .expect("parse with selection");
    assert_eq!(parsed.scheduled_date.as_deref(), Some("2026-12-25"));
}

#[test]
fn task_roundtrip_through_engine() {
    let dir = tempfile::tempdir().expect("tempdir");
    let data_dir = dir.path().to_string_lossy().into_owned();

    {
        let engine = agenda_mobile::MobileEngine::open(data_dir.clone()).expect("open");

        let object = to_object(&new_task("task-1", "Call mom"));
        assert_eq!(object.contains("\"typeId\":\"core.task\""), true);
        let upserted = call(
            &engine,
            "upsert_object",
            json!({"object": serde_json::from_str::<Value>(&object).unwrap()}),
        );
        assert_eq!(upserted, json!(true));

        let listed = call(
            &engine,
            "list_objects_by_type",
            json!({"type_id": "core.task"}),
        );
        let objects = listed.as_array().expect("list");
        assert_eq!(objects.len(), 1);
        let todo_json =
            agenda_mobile::task_from_engine_object(objects[0].to_string()).expect("read task");
        let todo: Value = serde_json::from_str(&todo_json).unwrap();
        assert_eq!(todo["title"], json!("Call mom"));

        let indices = agenda_mobile::smart_list_indices(
            agenda_mobile::SmartListName::Inbox,
            format!("[{todo_json}]"),
            TODAY.to_string(),
            0,
        )
        .expect("filter");
        assert_eq!(indices, vec![0]);

        engine.shutdown();
    }

    let engine = agenda_mobile::MobileEngine::open(data_dir).expect("reopen");
    let fetched = call(&engine, "get_object", json!({"id": "task-1"}));
    assert_eq!(fetched["title"], json!("Call mom"));
    engine.shutdown();
}
