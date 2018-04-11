alter table bsfile_information
  add is_public BOOLEAN NOT NULL DEFAULT 0;

alter table bsfile_information
  add is_attachment BOOLEAN NOT NULL DEFAULT 1;

update bsfile_information
  set is_public = 0,
    is_attachment = 1;
