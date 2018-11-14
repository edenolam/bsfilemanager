alter table bsfile_information
  add is_content_no_virus_trusted BOOLEAN NOT NULL DEFAULT 0;

update bsfile_information
  set is_content_no_virus_trusted = 0;
