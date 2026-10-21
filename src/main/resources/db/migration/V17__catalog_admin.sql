ALTER TABLE treatment_catalog ADD COLUMN specialty_id bigint REFERENCES specialties(specialty_id);
CREATE INDEX idx_treatment_catalog_specialty_id ON treatment_catalog (specialty_id);

CREATE TABLE treatment_catalog_aud (
    treatment_id bigint      NOT NULL,
    rev          bigint      NOT NULL REFERENCES revision_info(revision_id),
    revtype      smallint,
    code         varchar(30),
    name         varchar(150),
    description  text,
    base_price   decimal(10,2),
    active       boolean,
    specialty_id bigint,
    PRIMARY KEY (treatment_id, rev)
);

CREATE TABLE specialties_aud (
    specialty_id            bigint      NOT NULL,
    rev                     bigint      NOT NULL REFERENCES revision_info(revision_id),
    revtype                 smallint,
    name                    varchar(100),
    default_duration_minutes int,
    active                  boolean,
    PRIMARY KEY (specialty_id, rev)
);
