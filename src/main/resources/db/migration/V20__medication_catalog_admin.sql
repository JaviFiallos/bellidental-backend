-- Catálogo de medicamentos administrable (tarea 028): posologías activables y auditoría Envers.

ALTER TABLE medication_presets ADD COLUMN active boolean NOT NULL DEFAULT TRUE;

CREATE TABLE medications_aud (
    medication_id bigint      NOT NULL,
    rev           bigint      NOT NULL REFERENCES revision_info(revision_id),
    revtype       smallint,
    name          varchar(150),
    presentation  varchar(100),
    concentration varchar(100),
    route         varchar(50),
    active        boolean,
    PRIMARY KEY (medication_id, rev)
);

CREATE TABLE medication_presets_aud (
    medication_preset_id bigint  NOT NULL,
    rev                  bigint  NOT NULL REFERENCES revision_info(revision_id),
    revtype              smallint,
    medication_id        bigint,
    label                varchar(60),
    dose                 varchar(100),
    frequency            varchar(100),
    duration             varchar(100),
    instructions         text,
    active               boolean,
    PRIMARY KEY (medication_preset_id, rev)
);
