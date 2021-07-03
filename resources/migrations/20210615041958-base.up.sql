create table lemmas (
  id integer primary key,
  writing text,
  reading text,
  pos integer,
  unique(writing, pos)
);

--;;

create table lemma_definition (
  id integer primary key,
  lemma_id integer,
  language text,
  definition text,
  foreign key(lemma_id) references lemmas(id)
);

--;;

create table lemma_knowledge (
  lemma_id integer primary key,
  familiarity integer check(familiarity between 0 and 2),
  preferred_definition integer,
  foreign key(lemma_id) references lemmas(id),
  foreign key(preferred_definition) references lemma_definition(id)
);

--;;

create table texts (
  id integer primary key,
  name text unique,
  content text,
  created datetime,
  modified datetime
);

--;;

create table sentences (
  id integer primary key,
  text_id integer,
  text_offset int,
  length int,
  foreign key(text_id) references texts(id) on delete cascade
);

--;;

create table words(
  id integer primary key,
  sentence_id integer,
  sentence_offset integer,
  length integer,
  lemma_id integer,
  foreign key (sentence_id) references sentences(id) on delete cascade,
  foreign key(lemma_id) references lemmas(id)
);
