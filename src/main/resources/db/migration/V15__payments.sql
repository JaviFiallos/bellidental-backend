CREATE SEQUENCE payment_receipt_seq;

CREATE TABLE payments (
    payment_id     uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id     uuid          NOT NULL REFERENCES patients(patient_id),
    encounter_id   uuid          REFERENCES encounters(encounter_id),
    amount         decimal(10,2) NOT NULL CHECK (amount > 0),
    method         varchar(20)   NOT NULL CHECK (method IN ('EFECTIVO','TRANSFERENCIA','TARJETA')),
    receipt_number varchar(30)   UNIQUE,
    paid_at        timestamptz   NOT NULL DEFAULT now(),
    received_by    uuid          NOT NULL REFERENCES users(user_id),
    voided_at      timestamptz,
    void_reason    text
);

CREATE INDEX idx_payments_patient_id ON payments(patient_id);

CREATE FUNCTION prevent_payment_delete() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Los pagos no se eliminan: se anulan con voided_at.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payments_no_delete BEFORE DELETE ON payments
    FOR EACH ROW EXECUTE FUNCTION prevent_payment_delete();
