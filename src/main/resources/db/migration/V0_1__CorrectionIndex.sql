drop index bsfile_information_uidx_3_2 on bsfile_information ;

drop index bsfile_information_idx_3_4 on bsfile_information ;

create index bsfile_information_idx_4_6_3
  on bsfile_information (logical_folder, target_year, owner_key);
