select e.name as env, t.task_type_name, s.task_state_name, c.cnt from
(select env_type_id, task_type_id, task_state_id , count(*) as cnt from task_queue group by env_type_id, task_type_id, task_state_id) as c
join env_type e on (e.id=env_type_id)
join task_type t on (t.id=task_type_id)
join task_state s on (s.id=task_state_id)
