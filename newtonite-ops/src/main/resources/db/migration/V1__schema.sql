create table users(id bigserial primary key, email text unique not null, password text not null, name text not null);
create table teams(id bigserial primary key, name text not null);
create table memberships(user_id bigint references users, team_id bigint references teams, role text not null, primary key(user_id, team_id));
create table work_items(
  id bigserial primary key, title text not null, description text,
  type text not null, priority text not null, status text not null default 'OPEN',
  team_id bigint not null references teams, assignee_id bigint references users,
  created_by bigint not null references users, due_date date,
  version bigint not null default 0,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now());
create index ix_items_team on work_items(team_id, status, priority, id desc);
create index ix_items_assignee on work_items(assignee_id, status);
create index ix_items_unassigned_urgent on work_items(team_id, id desc) where assignee_id is null and priority = 'URGENT';
create index ix_items_search on work_items using gin (to_tsvector('english', title || ' ' || coalesce(description, '')));
create table activity_events(  -- append-only: the app never updates or deletes rows here
  id bigserial primary key, item_id bigint not null references work_items, actor_id bigint not null references users,
  event_type text not null, old_value text, new_value text, created_at timestamptz not null default now());
create index ix_activity_item on activity_events(item_id, id);
create table idempotency_keys(user_id bigint not null, idem_key text not null, item_id bigint, primary key(user_id, idem_key));
