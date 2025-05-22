

drop table if exists seq;
drop table if exists system_process_lsof;
drop table if exists system_process;
drop table if exists cluster_member;
drop table if exists web_host;
drop table if exists app_conf_entry;
drop table if exists app_conf;
drop table if exists word_dictionary;
drop table if exists env_type;

CREATE TABLE if not exists seq (
  name varchar(200) NOT NULL,
  value bigint NOT NULL,
  last_ms bigint DEFAULT NULL,
  PRIMARY KEY (name)
) engine=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;


create table if not exists env_type (
	id int not null,
    name varchar(64) not null,
    primary key (id),
    unique index env_type_name_idx(name)
) engine=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;


insert into env_type values (1, 'prod');
insert into env_type values (2, 'beta');
insert into env_type values (3, 'stg');
insert into env_type values (4, 'alpha');
insert into env_type values (5, 'dev');
insert into env_type values (6, 'localdev');

create table if not exists app_conf (
	id int not null,
	env_type_id int not null,
    name varchar(64) not null,
    last_ms bigint not null,
    primary key (id),
    unique index app_conf_name_idx(name, env_type_id),
    constraint app_conf_env_type_fk foreign key (env_type_id) references env_type(id)
) engine=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

create table if not exists app_conf_entry (
	app_conf_id int not null,
	position int not null default 0,
    config varchar(64) not null,
    meta mediumtext not null,
    last_ms bigint not null,
    primary key (app_conf_id,position),
    unique index app_conf_name_idx(config),
    constraint app_conf_entry_app_conf_fk foreign key (app_conf_id) references app_conf(id)
) engine=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;



insert into app_conf values (1, 6, 'testapp1', unix_timestamp()*1000);
insert into app_conf values (2, 6, 'test-task-client', unix_timestamp()*1000);

insert into app_conf_entry (app_conf_id,position,config,meta,last_ms) values (1,0,'my_entry','{\"some value\":\"some override\"}',unix_timestamp()*1000);



create table if not exists cluster_member (
	id int not null,
    hostname varchar(300) not null,
    member_type varchar(64) not null,
    tcp_port int not null,
	meta mediumtext not null,
    app_conf_id int not null default 0,
    last_ms bigint not null,
    primary key (id),
    unique index cluster_member_idx (hostname, tcp_port),
    index cluster_member_app_conf_idx (app_conf_id),
    constraint cluster_member_app_conf_fk foreign key (app_conf_id) references app_conf(id) 
) engine=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

INSERT INTO cluster_member (id, hostname, member_type, tcp_port, meta, app_conf_id, last_ms) VALUES (1, 'z440', 'WEBSERVER', 8080, '{}', 1, unix_timestamp()*1000);



create table if not exists system_process (
	id bigint not null,
    is_active bool not null,
    app_conf_id int null,
    hostname varchar(300) not null,
    pid bigint null,
    cmd mediumtext null,
    cluster_member_id int null,
    start_ms bigint not null,
    ping_ms  bigint not null,
    dead_ms  bigint null,
    primary key (id),
    index process_dead_idx (is_active, dead_ms),
    index process_cluster_idx (cluster_member_id, is_active),
    constraint system_process_cluster_member_fk foreign key (cluster_member_id) references cluster_member(id),
    constraint system_process_app_conf_fk foreign key (app_conf_id) references app_conf(id)
) engine=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;



drop table if exists word_dictionary;
create table if not exists word_dictionary (
	id int not null,
    word varchar(700) not null,
    last_ms bigint null,
    primary key (id),
    unique index word_dictionary_idx (word)
) engine=InnoDB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;


