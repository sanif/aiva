"""Full todo-tracker API: projects, notes, tags, subtasks, filters."""
from __future__ import annotations

from tests.conftest import AUTH


async def make(client, **kw) -> dict:
    body = {"title": "t", **kw}
    r = await client.post("/api/tasks", json=body, headers=AUTH)
    assert r.status_code == 201, r.text
    return r.json()


async def test_task_carries_tracker_fields(client):
    t = await make(
        client,
        title="Ship Aiva v1",
        project="aiva",
        notes="remember the changelog",
        tags="release,android",
        priority="high",
    )
    assert t["project"] == "aiva"
    assert t["notes"] == "remember the changelog"
    assert t["tags"] == "release,android"
    assert t["parent_id"] is None
    assert t["completed_at"] is None


async def test_subtasks_reference_their_parent(client):
    parent = await make(client, title="Release")
    child = await make(client, title="Write changelog", parent_id=parent["id"])
    assert child["parent_id"] == parent["id"]


async def test_completed_at_set_and_cleared(client):
    t = await make(client, title="finishable")
    r = await client.patch(f"/api/tasks/{t['id']}", json={"status": "done"}, headers=AUTH)
    assert r.json()["completed_at"] is not None

    r = await client.patch(f"/api/tasks/{t['id']}", json={"status": "today"}, headers=AUTH)
    assert r.json()["completed_at"] is None


async def test_filter_by_project_search_and_tag(client):
    await make(client, title="alpha work item", project="proj-x", tags="deep")
    await make(client, title="beta other item", project="proj-y", tags="shallow")

    r = await client.get("/api/tasks", params={"project": "proj-x"}, headers=AUTH)
    titles = [t["title"] for t in r.json()]
    assert "alpha work item" in titles and "beta other item" not in titles

    r = await client.get("/api/tasks", params={"q": "beta"}, headers=AUTH)
    titles = [t["title"] for t in r.json()]
    assert titles and all("beta" in x for x in titles)

    r = await client.get("/api/tasks", params={"tag": "deep"}, headers=AUTH)
    assert all("deep" in (t["tags"] or "") for t in r.json())
    assert r.json()


async def test_projects_endpoint_counts(client):
    await make(client, title="a", project="counted")
    await make(client, title="b", project="counted")
    done = await make(client, title="c", project="counted")
    await client.patch(f"/api/tasks/{done['id']}", json={"status": "done"}, headers=AUTH)

    r = await client.get("/api/projects", headers=AUTH)
    assert r.status_code == 200
    proj = next(p for p in r.json() if p["name"] == "counted")
    assert proj["total"] == 3
    assert proj["done"] == 1
