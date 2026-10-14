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
        let links = call(&engine, "list_object_links", json!({}));
        let todo_json =
            agenda_mobile::task_from_engine_object(objects[0].to_string(), links.to_string())
                .expect("read task");
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

/// A project is a `project` object link, not a prop. The Engine keeps the
/// link rows and never puts them on the object, so reads must join them from
/// `list_object_links` (desktop `project_links`).
#[test]
fn project_membership_is_an_object_link() {
    let dir = tempfile::tempdir().expect("tempdir");
    let data_dir = dir.path().to_string_lossy().into_owned();
    let now = "2026-10-10T12:00:00+00:00";

    let engine = agenda_mobile::MobileEngine::open(data_dir.clone()).expect("open");
    // The link's foreign keys: the project objects must exist first.
    for id in ["proj-home", "proj-work"] {
        call(
            &engine,
            "upsert_object",
            json!({"object": project_object(id, now)}),
        );
    }
    let object = to_object(&new_task("task-1", "Call mom"));
    call(
        &engine,
        "upsert_object",
        json!({"object": serde_json::from_str::<Value>(&object).unwrap()}),
    );
    assert_eq!(
        read_engine_task(&engine, "task-1")["project_id"],
        Value::Null
    );

    // Placing the task: the link is written and the read picks it up.
    let home = project_link("task-1", "proj-home", now);
    assert_eq!(home["id"], json!("lnk:task-1:project:proj-home"));
    call(&engine, "upsert_object_link", json!({"object_link": home}));
    assert_eq!(
        read_engine_task(&engine, "task-1")["project_id"],
        json!("proj-home")
    );

    // Relink: write the new link, delete the old one (desktop relink_project).
    // A link of another type on the same task is not membership.
    let work = project_link("task-1", "proj-work", now);
    call(&engine, "upsert_object_link", json!({"object_link": work}));
    call(&engine, "delete_object_link", json!({"id": home["id"]}));
    call(
        &engine,
        "upsert_object_link",
        json!({"object_link": {
            "id": "lnk:task-1:related:proj-home",
            "sourceObjectId": "task-1",
            "targetObjectId": "proj-home",
            "linkType": "related",
            "createdAt": now,
        }}),
    );
    assert_eq!(
        read_engine_task(&engine, "task-1")["project_id"],
        json!("proj-work")
    );

    // The board path (one call for the whole list) agrees with the single read.
    let listed = call(
        &engine,
        "list_objects_by_type",
        json!({"type_id": "core.task"}),
    );
    let links = call(&engine, "list_object_links", json!({}));
    let board: Vec<String> = serde_json::from_str(
        &agenda_mobile::tasks_from_engine_objects(listed.to_string(), links.to_string())
            .expect("board read"),
    )
    .expect("board json");
    let board: Value = serde_json::from_str(&board[0]).expect("todo json");
    assert_eq!(board["project_id"], json!("proj-work"));
    engine.shutdown();

    // The placement survives a reopen.
    let engine = agenda_mobile::MobileEngine::open(data_dir).expect("reopen");
    assert_eq!(
        read_engine_task(&engine, "task-1")["project_id"],
        json!("proj-work")
    );

    // Clearing the project deletes the link and nothing else.
    call(&engine, "delete_object_link", json!({"id": work["id"]}));
    assert_eq!(
        read_engine_task(&engine, "task-1")["project_id"],
        Value::Null
    );
    engine.shutdown();
}

fn project_object(id: &str, now: &str) -> Value {
    json!({
        "id": id,
        "typeId": "com.kosmos.project",
        "typeVersion": "1.1.0",
        "title": id,
        "contentJson": {"type": "doc", "content": []},
        "propsJson": {
            "color": null,
            "dueAt": null,
            "extensions": {"sortOrder": 0},
            "scheduledAt": null,
            "status": "active",
        },
        "createdAt": now,
        "updatedAt": now,
        "deletedAt": null,
    })
}

fn project_link(task: &str, project: &str, now: &str) -> Value {
    serde_json::from_str(&agenda_mobile::project_link_json(
        task.to_string(),
        project.to_string(),
        now.to_string(),
    ))
    .expect("link json")
}

fn read_engine_task(engine: &agenda_mobile::MobileEngine, id: &str) -> Value {
    let object = call(engine, "get_object", json!({"id": id}));
    let links = call(engine, "list_object_links", json!({}));
    let todo = agenda_mobile::task_from_engine_object(object.to_string(), links.to_string())
        .expect("read task");
    serde_json::from_str(&todo).expect("todo json")
}

/// The Engine enforces the link's foreign keys, which is why the repository
/// checks that the project object exists before it links a task to it.
#[test]
fn link_to_missing_project_is_rejected() {
    let dir = tempfile::tempdir().expect("tempdir");
    let engine =
        agenda_mobile::MobileEngine::open(dir.path().to_string_lossy().into_owned()).expect("open");
    let object = to_object(&new_task("task-1", "Call mom"));
    call(
        &engine,
        "upsert_object",
        json!({"object": serde_json::from_str::<Value>(&object).unwrap()}),
    );
    let link = project_link("task-1", "proj-missing", "2026-10-10T12:00:00+00:00");
    let response: Value = serde_json::from_str(
        &engine
            .call(
                "upsert_object_link".to_string(),
                json!({"object_link": link}).to_string(),
            )
            .expect("call transport"),
    )
    .expect("response json");
    assert_eq!(response["ok"], json!(false), "{response}");
    engine.shutdown();
}
