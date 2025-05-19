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
);


create table if not exists env_type (
	id int not null,
    name varchar(64) not null,
    primary key (id)
);
create unique index env_type_name_idx on env_type(name);


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
    primary key (id)
);
create unique index app_conf_name_idx on app_conf(name, env_type_id);
alter table app_conf add constraint app_conf_env_type_fk foreign key (env_type_id) references env_type(id);

create table if not exists app_conf_entry (
	app_conf_id int not null,
	position int not null default 0,
    config varchar(64) not null,
    meta text not null COLLATE "C.utf8",
    last_ms bigint not null,
    primary key (app_conf_id,position)
);
create unique index app_conf_entry_name_idx on app_conf_entry (config);
alter table app_conf_entry add constraint app_conf_entry_app_conf_fk foreign key (app_conf_id) references app_conf(id);

insert into app_conf values (1, 6, 'testapp1', 1000*extract(epoch from now()));
insert into app_conf values (2, 6, 'test-task-client', 1000*extract(epoch from now()));
insert into app_conf values (3, 6, 'test-task-server', 1000*extract(epoch from now()));



insert into app_conf_entry (app_conf_id,position,config,meta,last_ms) values (1,0,'my_entry','{\"some value\":\"some override\"}',1000*extract(epoch from now()));



create table if not exists cluster_member (
	id int not null,
    hostname varchar(300) not null,
    member_type varchar(64) not null,
    tcp_port int not null,
	meta text not null COLLATE "C.utf8",
    app_conf_id int not null default 0,
    last_ms bigint not null,
    primary key (id)
);
create unique index cluster_member_idx on cluster_member(hostname, tcp_port);
create index cluster_member_app_conf_idx on cluster_member(app_conf_id);
alter table cluster_member add constraint cluster_member_app_conf_fk foreign key (app_conf_id) references app_conf(id);



INSERT INTO cluster_member (id, hostname, member_type, tcp_port, meta, app_conf_id, last_ms) VALUES (1, 'z440', 'WEBSERVER', 8080, '{}', 1, 1000*extract(epoch from now()));

create table if not exists system_process (
	id bigint not null,
    is_active int not null,
    app_conf_id int null,
    hostname varchar(300) not null,
    pid bigint null,
    cmd text null COLLATE "C.utf8",
    cluster_member_id int null,
    start_ms bigint not null,
    ping_ms  bigint not null,
    dead_ms  bigint null,
    primary key (id)
);
create index process_dead_idx on system_process(is_active, dead_ms, id);
create index process_cluster_idx on system_process(cluster_member_id, is_active);
alter table system_process add constraint system_process_cluster_member_fk foreign key (cluster_member_id) references cluster_member(id);
alter table system_process add constraint system_process_app_conf_fk foreign key (app_conf_id) references app_conf(id);



drop table if exists word_dictionary;
create table if not exists word_dictionary (
	id int not null,
    word varchar(700) not null COLLATE "C.utf8",
    last_ms bigint null,
    primary key (id)
);
create unique index word_dictionary_idx on word_dictionary (word);

