SET enable_hashjoin=on;
SET enable_mergejoin=on;
SET enable_seqscan=on;
SET random_page_cost=1;
explain  select pickup.task_type_id, pickup.ticket from (
select 0 as task_type_id, ticket from (
select q.ticket from (select ticket, task_type_id from task_queue q where env_type_id=6 and task_type_id=0 and task_state_id=0 and process_at_ms<=1747399839259 limit 128) as q 
where not exists (select 1 from task_queue_process p where p.task_type_id=q.task_type_id and p.ticket=q.ticket)
) as t_0
union all
select 1 as task_type_id, ticket from (
select q.ticket from (select ticket, task_type_id from task_queue q where env_type_id=6 and task_type_id=1 and task_state_id=0 and process_at_ms<=1747399839259 limit 16) as q where not exists (select 1 from task_queue_process p where p.task_type_id=q.task_type_id and p.ticket=q.ticket)
) as t_1
) as pickup

select * from task_type


SET enable_hashjoin=off;
SET enable_mergejoin=off;
SET enable_seqscan=off;
SET random_page_cost=1;
explain select pickup.task_type_id, pickup.ticket from (
select 0 as task_type_id, ticket from (
select q.ticket from (select ticket, task_type_id from task_queue q where env_type_id=6 and task_type_id=0 and task_state_id=0 and process_at_ms<=1747439635132 limit 128 for update skip locked) as q 
where not exists (select 1 from task_queue_process p where p.task_type_id=0 and p.ticket=q.ticket)
) as t_0
union all
select 1 as task_type_id, ticket from (
select q.ticket from (select ticket, task_type_id from task_queue q where env_type_id=6 and task_type_id=1 and task_state_id=0 and process_at_ms<=1747439635132 limit 16 for update skip locked) as q 
where not exists (select 1 from task_queue_process p where p.task_type_id=q.task_type_id and p.ticket=q.ticket)
) as t_1
) as pickup

select max (process_at_ms) from task_queue q where env_type_id=6 and task_type_id=1 

VACUUM  (FULL,INDEX_CLEANUP, ANALYSE) task_queue, task_queue_process


SELECT name, setting FROM pg_settings where name in ('random_page_cost', 'seq_page_cost', 'effective_cache_size')

ALTER SYSTEM SET <parameter_name> TO <value>;

SET enable_hashjoin=on;
SET enable_mergejoin=on;
explain select ticket, error_count, system_process_id from task_queue q where env_type_id=6 and task_type_id=1 and  task_state_id=1 and not exists (select 1 from system_process p where p.is_active=1 and q.system_process_id=p.id)

drop index process_dead_idx 
create index process_dead_idx on system_process(is_active, dead_ms, id);


select task_type_id, task_state_id, count(*) from task_queue group by task_type_id, task_state_id 
select * from task_queue_error

select * from task_queue q left join system_process p on (p.id=q.system_process_id) where task_state_id=1

select ticket, error_count, system_process_id from task_queue q where env_type_id=6 and task_type_id=1 and task_state_id=1 and not exists (select 1 from system_process p where p.is_active=1 and q.system_process_id=p.id) limit 1024 for update skip locked

select * from task_state

select count(*) from task_queue_error where task_type_id=1

select * from task_queue_error

SET enable_hashjoin=on;
SET enable_mergejoin=on;
SET enable_seqscan=on;
SET random_page_cost=4;
select ticket, error_count, system_process_id from task_queue q where env_type_id=6 and task_type_id=1 and task_state_id=1 and not exists (select 1 from system_process p where p.is_active=1 and q.system_process_id=p.id) limit 1024 for update skip locked 

WITH t as (
select task_type_id, ticket, error_count from task_queue where env_type_id=6 and task_type_id=1 and task_state_id=3 and error_count<=20 limit 10 for update skip locked 
) update task_queue q set task_state_id=0, error_count=t.error_count from t where q.task_type_id=t.task_type_id and q.ticket=t.ticket

select * from task_queue q join task_queue_error e  on (q.task_type_id=e.task_type_id and q.ticket=e.ticket) order by q.ticket

select * from task_queue_error

update task_queue set task_state_id=0 , error_count=0 where task_state_id=4 

select * from task_queue_process where system_process_id=1

delete from task_queue_error

SET enable_hashjoin=off;
SET enable_mergejoin=off;
SET enable_seqscan=off;
SET random_page_cost=1;
select count(*) from (
select 1 from (
select 1 from (select ticket, task_type_id from task_queue q where env_type_id=6 and task_type_id=0 and task_state_id=0 and process_at_ms<=1747511796667 limit 1) as q where not exists (select 1 from task_queue_process p where p.task_type_id=q.task_type_id and p.ticket=q.ticket)
) as t_0
union all
select 1 from (
select 1 from (select ticket, task_type_id from task_queue q where env_type_id=6 and task_type_id=1 and task_state_id=0 and process_at_ms<=1747511796667 limit 1) as q where not exists (select 1 from task_queue_process p where p.task_type_id=q.task_type_id and p.ticket=q.ticket)
) as t_1
) as pickup;
SET enable_seqscan=on;


[6, 0, 0, 1747511796667, 6, 1, 0, 1747511796667]


select 1 from (select ticket, task_type_id from task_queue q where env_type_id=6 and task_type_id=1 and task_state_id=0 and process_at_ms<=1747511796667] limit 1) as q where not exists (select 1 from task_queue_process p where p.task_type_id=q.task_type_id and p.ticket=q.ticket)


