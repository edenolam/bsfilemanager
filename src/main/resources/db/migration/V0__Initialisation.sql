create table bsfile_information
(
  id bigint auto_increment primary key,
  file_key varchar(50) not null,
  owner_key varchar(20) not null,
  logical_folder varchar(256) not null,
  is_special BOOLEAN NOT NULL DEFAULT 0,
  target_year int not null,
  original_file_name varchar(255) not null,
  file_content_hash varchar(64) not null,
  file_content_size int not null,
  file_content_type varchar(255) not null,
  storage_date date not null,
  storage_hashed_file_name varchar(64) not null,
  external_ref varchar(30) null,
  status int not null,
  status_linked_data VARCHAR(10000)
)
  engine=InnoDB;

create unique index bsfile_information_uidx_2
  on bsfile_information (file_key);

create unique index bsfile_information_uidx_12
  on bsfile_information (storage_hashed_file_name);

create unique index bsfile_information_uidx_3_2
  on bsfile_information (owner_key, file_key);

create index bsfile_information_idx_3_4
  on bsfile_information (owner_key, logical_folder);
