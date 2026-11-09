#!/usr/bin/env python3
"""Datos demo para el manual de usuario (tarea 036). Solo usa la API; repetible sobre la misma base.

Uso: python3 docs/manual/seed-demo.py [AAAA-MM-DD]   (fecha de la demo; por defecto hoy)
Variable opcional: API_URL (por defecto http://localhost:8080/api/v1).
"""
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, datetime, timezone

API_URL = os.environ.get("API_URL", "http://localhost:8080/api/v1")
PASSWORD = "Cambiar.2026!"
DIRECTOR_ID = "55555555-5555-5555-5555-555555555555"
DENTIST_ID = "66666666-6666-6666-6666-666666666666"
GENERAL, ENDODONTICS = 1, 2
CLINIC_SHIFTS = [("08:00", "12:00"), ("14:00", "20:00")]

summary = {"profesionales": 0, "turnos": 0, "pacientes": 0, "citas": 0}


def fail(message):
    sys.exit(f"ERROR: {message}")


def call(method, path, token=None, body=None):
    request = urllib.request.Request(API_URL + path, method=method)
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    payload = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(request, payload) as response:
            text = response.read().decode()
    except urllib.error.HTTPError as error:
        fail(f"{method} {path} -> {error.code}: {error.read().decode()}")
    except urllib.error.URLError as error:
        fail(f"no responde {API_URL} ({error.reason}). ¿Está corriendo el backend?")
    return json.loads(text).get("data") if text else None


def login(email):
    return call("POST", "/auth/login", body={"email": email, "password": PASSWORD})["accessToken"]


def cedula(base9):
    """Completa una cédula ecuatoriana de 9 dígitos con su dígito verificador."""
    total = 0
    for index, digit in enumerate(base9):
        product = int(digit) * (2 if index % 2 == 0 else 1)
        total += product - 9 if product > 9 else product
    return base9 + str((10 - total % 10) % 10)


def update_seed_professionals(token):
    professionals = [
        (DIRECTOR_ID, "Andrés", "Villacís", "180412365", "MSP-1804-2011-0457", "#1E88E5"),
        (DENTIST_ID, "Carolina", "Mena", "180587234", "MSP-1805-2016-1123", "#43A047"),
    ]
    for professional_id, first_name, last_name, document, license_number, color in professionals:
        call("PUT", f"/professionals/{professional_id}", token, {
            "documentType": "CI", "documentNumber": cedula(document), "firstName": first_name,
            "lastName": last_name, "licenseNumber": license_number, "colorTag": color,
            "specialtyIds": [GENERAL],
        })
        summary["profesionales"] += 1


def ensure_endodontist(token):
    license_number = "MSP-1803-2014-0789"
    existing = [p for p in call("GET", "/professionals", token) if p["licenseNumber"] == license_number]
    if existing:
        return existing[0]["id"]
    created = call("POST", "/professionals", token, {
        "documentType": "CI", "documentNumber": cedula("180356218"), "firstName": "Paula",
        "lastName": "Ortiz", "licenseNumber": license_number, "colorTag": "#FB8C00",
        "specialtyIds": [ENDODONTICS],
    })
    call("POST", f"/professionals/{created['id']}/access", token, {
        "email": "endodoncista@bellidental.local", "temporaryPassword": PASSWORD,
    })
    summary["profesionales"] += 1
    return created["id"]


def ensure_shift(token, professional_id, start, end, weekday=None, specific_date=None):
    for shift in call("GET", f"/professionals/{professional_id}/schedules", token):
        same_day = shift.get("weekday") == weekday and shift.get("specificDate") == specific_date
        if same_day and str(shift.get("startTime", "")).startswith(start) and shift.get("active", True):
            return
    call("POST", f"/professionals/{professional_id}/schedules", token, {
        "weekday": weekday, "specificDate": specific_date, "startTime": start, "endTime": end,
        "available": True,
    })
    summary["turnos"] += 1


def create_schedules(token, endodontist_id, demo_date):
    for weekday in range(1, 6):
        for start, end in CLINIC_SHIFTS:
            ensure_shift(token, DIRECTOR_ID, start, end, weekday=weekday)
    ensure_shift(token, endodontist_id, "08:00", "12:00", weekday=2)
    ensure_shift(token, endodontist_id, "14:00", "18:00", weekday=2)
    if demo_date.isoweekday() > 5:
        ensure_shift(token, DIRECTOR_ID, "08:00", "20:00", specific_date=demo_date.isoformat())


PATIENTS = [
    ("180234567", "María José", "Paredes Lozada", "1988-03-14", "F", "593987412365", None),
    ("180498231", "Luis Fernando", "Chicaiza Toapanta", "1975-09-02", "M", "593998745123", None),
    ("180611374", "Gabriela", "Sánchez Vaca", "1996-12-21", "F", "593984561237",
     "Presupuesto de ortodoncia entregado"),
    ("180377452", "Jorge Enrique", "Altamirano Ruiz", "1962-06-30", "M", "593991237845", None),
]


def ensure_patients(token):
    patient_ids = []
    for base, first_name, last_name, birth_date, sex, phone, notes in PATIENTS:
        document = cedula(base)
        found = call("GET", f"/patients?query={document}", token)
        found = found.get("content", []) if isinstance(found, dict) else found
        match = [p for p in found if p.get("documentNumber") == document]
        if match:
            patient_ids.append(match[0]["id"])
            continue
        created = call("POST", "/patients", token, {
            "documentType": "CI", "documentNumber": document, "firstName": first_name,
            "lastName": last_name, "birthDate": birth_date, "sex": sex, "phoneE164": phone,
            "adminNotes": notes,
        })
        patient_ids.append(created["id"])
        summary["pacientes"] += 1
    return patient_ids


def has_appointments(token, demo_date):
    query = urllib.parse.urlencode({
        "professionalId": DIRECTOR_ID,
        "from": f"{demo_date.isoformat()}T00:00:00-05:00",
        "to": f"{demo_date.isoformat()}T23:59:59-05:00",
    })
    return any(a["status"] in ("PENDING", "CONFIRMED") for a in call("GET", f"/appointments?{query}", token))


def create_appointments(token, patient_ids, demo_date):
    if has_appointments(token, demo_date):
        return
    query = urllib.parse.urlencode({
        "professionalId": DIRECTOR_ID, "specialtyId": GENERAL, "date": demo_date.isoformat(),
    })
    now = datetime.now(timezone.utc)
    slots = [s for s in call("GET", f"/availability?{query}", token)
             if datetime.fromisoformat(s["startAt"]) > now]
    reasons = ["Control y limpieza", "Dolor en molar inferior", "Revisión de obturación"]
    for patient_id, slot, reason in zip(patient_ids, slots[1::3], reasons):
        call("POST", "/appointments", token, {
            "patientId": patient_id, "professionalId": DIRECTOR_ID, "specialtyId": GENERAL,
            "startAt": slot["startAt"], "reason": reason,
        })
        summary["citas"] += 1


def main():
    demo_date = date.fromisoformat(sys.argv[1]) if len(sys.argv) > 1 else date.today()
    director = login("director@bellidental.local")
    secretary = login("secretaria@bellidental.local")
    update_seed_professionals(director)
    endodontist_id = ensure_endodontist(director)
    create_schedules(secretary, endodontist_id, demo_date)
    patient_ids = ensure_patients(secretary)
    create_appointments(secretary, patient_ids, demo_date)
    print(f"Demo {demo_date}: " + ", ".join(f"{key} {value}" for key, value in summary.items()))


if __name__ == "__main__":
    main()
