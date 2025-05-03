DROP TABLE IF EXISTS task_type;
DROP TABLE IF EXISTS task_state;
DROP TABLE IF EXISTS task_queue;
DROP TABLE IF EXISTS task_queue_error;
DROP TABLE IF EXISTS task_queue_process;
DROP TABLE IF EXISTS task_field_text;
DROP TABLE IF EXISTS task_field_num;

CREATE TABLE IF NOT EXISTS task_type (
  id INT NOT NULL,
  task_type_name VARCHAR(200) NOT NULL,
  meta TEXT NULL COLLATE "C.utf8",
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX task_type_idx ON task_type (task_type_name);

insert into task_type (id,task_type_name,meta) values (0, 'dummy','{}');
insert into task_type (id,task_type_name,meta) values (1, 'daily_run','{}');


CREATE TABLE IF NOT EXISTS task_state (
  id INT NOT NULL,
  task_state_name VARCHAR(45) NOT NULL,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX task_state_idx ON task_state (task_state_name);

insert into task_state (id,task_state_name) values (0, 'INIT');
insert into task_state (id,task_state_name) values (1, 'PROCESS');
insert into task_state (id,task_state_name) values (2, 'SUCCESS');
insert into task_state (id,task_state_name) values (3, 'ERROR');


CREATE TABLE IF NOT EXISTS task_queue (
  ticket VARCHAR(128) NOT NULL,
  task_type_id INT NOT NULL,
  task_state_id INT NOT NULL,
  env_type_id INT NOT NULL,
  process_at_ms BIGINT NOT NULL,
  payload BYTEA NULL,
  result BYTEA NULL,
  submit_system_process_id BIGINT NULL,
  system_process_id BIGINT NULL,
  insert_ms BIGINT NOT NULL,
  last_ms BIGINT NOT NULL,
  PRIMARY KEY (task_type_id, ticket)
) partition by hash (task_type_id);
create table task_queue_p0 partition of task_queue for values with (modulus 8, remainder 0);
create table task_queue_p1 partition of task_queue for values with (modulus 8, remainder 1);
create table task_queue_p2 partition of task_queue for values with (modulus 8, remainder 2);
create table task_queue_p3 partition of task_queue for values with (modulus 8, remainder 3);
create table task_queue_p4 partition of task_queue for values with (modulus 8, remainder 4);
create table task_queue_p5 partition of task_queue for values with (modulus 8, remainder 5);
create table task_queue_p6 partition of task_queue for values with (modulus 8, remainder 6);
create table task_queue_p7 partition of task_queue for values with (modulus 8, remainder 7);

CREATE INDEX task_pickup_idx ON task_queue (env_type_id, task_type_id, task_state_id, process_at_ms);


CREATE TABLE IF NOT EXISTS task_queue_error (
  id BIGINT NOT NULL,
  task_type_id INT NOT NULL,  
  ticket VARCHAR(128) NOT NULL,
  last_ms BIGINT NOT NULL,
  system_process_id BIGINT NOT NULL,
  error TEXT NULL COLLATE "C.utf8",
  PRIMARY KEY (id)
);
CREATE INDEX task_queue_error_idx ON task_queue_error (task_type_id, ticket, last_ms);


CREATE TABLE IF NOT EXISTS task_queue_process (
  task_type_id INT NOT NULL,
  ticket VARCHAR(128) NOT NULL,    
  system_process_id BIGINT NOT NULL,
  pickup_id VARCHAR(128) NOT NULL,
  PRIMARY KEY (task_type_id, ticket)
) partition by hash (task_type_id);
create table task_queue_process_p0 partition of task_queue_process for values with (modulus 8, remainder 0);
create table task_queue_process_p1 partition of task_queue_process for values with (modulus 8, remainder 1);
create table task_queue_process_p2 partition of task_queue_process for values with (modulus 8, remainder 2);
create table task_queue_process_p3 partition of task_queue_process for values with (modulus 8, remainder 3);
create table task_queue_process_p4 partition of task_queue_process for values with (modulus 8, remainder 4);
create table task_queue_process_p5 partition of task_queue_process for values with (modulus 8, remainder 5);
create table task_queue_process_p6 partition of task_queue_process for values with (modulus 8, remainder 6);
create table task_queue_process_p7 partition of task_queue_process for values with (modulus 8, remainder 7);

CREATE INDEX task_queue_process_pickup_idx ON task_queue_process (pickup_id);



CREATE TABLE IF NOT EXISTS task_field_text (
  task_type_id INT NOT NULL,
  ticket VARCHAR(128) NOT NULL,
  id BIGINT NOT NULL,
  field VARCHAR(128) NOT NULL COLLATE "C.utf8",
  value VARCHAR(600) NOT NULL COLLATE "C.utf8",
  PRIMARY KEY (task_type_id, ticket, id)
) partition by hash (task_type_id);
create table task_field_text_p0 partition of task_field_text for values with (modulus 8, remainder 0);
create table task_field_text_p1 partition of task_field_text for values with (modulus 8, remainder 1);
create table task_field_text_p2 partition of task_field_text for values with (modulus 8, remainder 2);
create table task_field_text_p3 partition of task_field_text for values with (modulus 8, remainder 3);
create table task_field_text_p4 partition of task_field_text for values with (modulus 8, remainder 4);
create table task_field_text_p5 partition of task_field_text for values with (modulus 8, remainder 5);
create table task_field_text_p6 partition of task_field_text for values with (modulus 8, remainder 6);
create table task_field_text_p7 partition of task_field_text for values with (modulus 8, remainder 7);

CREATE INDEX task_field_text_val_idx ON task_field_text (field, value);


CREATE TABLE IF NOT EXISTS task_field_num (
  task_type_id INT NOT NULL,
  ticket VARCHAR(128) NOT NULL,
  id BIGINT NOT NULL,  
  field VARCHAR(128) NOT NULL COLLATE "C.utf8",
  value BIGINT NOT NULL,
  PRIMARY KEY (task_type_id, ticket, id)
) partition by hash (task_type_id);
create table task_field_num_p0 partition of task_field_num for values with (modulus 8, remainder 0);
create table task_field_num_p1 partition of task_field_num for values with (modulus 8, remainder 1);
create table task_field_num_p2 partition of task_field_num for values with (modulus 8, remainder 2);
create table task_field_num_p3 partition of task_field_num for values with (modulus 8, remainder 3);
create table task_field_num_p4 partition of task_field_num for values with (modulus 8, remainder 4);
create table task_field_num_p5 partition of task_field_num for values with (modulus 8, remainder 5);
create table task_field_num_p6 partition of task_field_num for values with (modulus 8, remainder 6);
create table task_field_num_p7 partition of task_field_num for values with (modulus 8, remainder 7);
CREATE INDEX task_field_num_val_idx ON task_field_num (field, value);     
