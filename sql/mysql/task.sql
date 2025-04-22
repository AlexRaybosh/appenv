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
  meta TEXT NULL,
  PRIMARY KEY (id)
) engine=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE UNIQUE INDEX task_type_idx ON task_type (task_type_name);

insert into task_type (id,task_type_name,meta) values (0, 'dummy','{}');
insert into task_type (id,task_type_name,meta) values (1, 'daily_run','{}');


CREATE TABLE IF NOT EXISTS task_state (
  id INT NOT NULL,
  task_state_name VARCHAR(45) NOT NULL,
  PRIMARY KEY (id)
) engine=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

CREATE UNIQUE INDEX task_state_idx ON task_state (task_state_name);
insert into task_state (id,task_state_name) values (0, 'INIT');
insert into task_state (id,task_state_name) values (1, 'PROCESS');
insert into task_state (id,task_state_name) values (2, 'SUCCESS');
insert into task_state (id,task_state_name) values (3, 'ERROR');
insert into task_state (id,task_state_name) values (4, 'FATAL');


CREATE TABLE IF NOT EXISTS task_queue (
  id BIGINT NOT NULL,
  task_type_id INT NOT NULL,
  task_state_id INT NOT NULL,
  env_type_id INT NOT NULL,
  ticket VARCHAR(128) NOT NULL,
  process_at_ms BIGINT NOT NULL,
  payload BLOB NULL,
  result BLOB NULL,
  insert_ms BIGINT NOT NULL,
  expire_ms BIGINT NOT NULL,
  error_count INT NOT NULL,
  last_ms BIGINT NOT NULL,
  PRIMARY KEY (id, task_type_id)
)
engine=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
PARTITION BY HASH(task_type_id)
PARTITIONS 32;


CREATE UNIQUE INDEX task_ticket_idx ON task_queue (task_type_id, ticket);
CREATE INDEX task_expire_idx ON task_queue (env_type_id, expire_ms);
CREATE INDEX task_pickup_idx ON task_queue (env_type_id, task_type_id, task_state_id, process_at_ms);


CREATE TABLE IF NOT EXISTS task_queue_error (
  id BIGINT NOT NULL,
  task_type_id INT NOT NULL,  
  task_queue_id BIGINT NOT NULL,
  last_ms BIGINT NOT NULL,
  system_process_id BIGINT NOT NULL,
  error TEXT NULL,
  PRIMARY KEY (id, task_type_id)
) engine=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
PARTITION BY HASH(task_type_id)
PARTITIONS 32;
CREATE INDEX task_queue_error_idx ON task_queue_error (task_queue_id, last_ms);

CREATE TABLE IF NOT EXISTS task_queue_process (
  task_queue_id BIGINT NOT NULL,
  task_type_id INT NOT NULL,    
  system_process_id BIGINT NOT NULL,
  PRIMARY KEY (task_queue_id, task_type_id)
) ENGINE = InnoDB
PARTITION BY HASH(task_type_id)
PARTITIONS 32;



CREATE TABLE IF NOT EXISTS task_field_text (
  id BIGINT NOT NULL,
  task_queue_id BIGINT NOT NULL,
  task_type_id INT NOT NULL,
  field VARCHAR(128) NOT NULL,
  value VARCHAR(600) NOT NULL,
  PRIMARY KEY (id, task_type_id)
) engine=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
PARTITION BY HASH(task_type_id)
PARTITIONS 32;
CREATE INDEX task_field_text_idx ON task_field_text (task_queue_id);
CREATE INDEX task_field_text_val_idx ON task_field_text (field, value);

CREATE TABLE IF NOT EXISTS task_field_num (
  id BIGINT NOT NULL,
  task_queue_id BIGINT NOT NULL,
  task_type_id INT NOT NULL,
  field VARCHAR(128) NOT NULL,
  value BIGINT NOT NULL,
  PRIMARY KEY (id, task_type_id)
) engine=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
PARTITION BY HASH(task_type_id)
PARTITIONS 32;
CREATE INDEX task_field_num_idx ON task_field_num (task_queue_id);
CREATE INDEX task_field_num_val_idx ON task_field_num (field, value);    
