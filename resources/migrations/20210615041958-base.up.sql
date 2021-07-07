create table text (
  id integer primary key,
  name text unique,
  content text,
  created datetime,
  modified datetime
);

--;;

create table knowledge (
  lemma_pos integer,
  lemma_writing text,
  lemma_reading text,
  familiarity integer check(familiarity between 0 and 2),
  created datetime,
  modified datetime,
  primary key(lemma_pos, lemma_reading, lemma_writing)
);
