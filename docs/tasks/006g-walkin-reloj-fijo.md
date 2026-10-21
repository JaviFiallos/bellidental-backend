# 006g — Reloj controlable en tests (walk-in falla de 23:30 a 24:00)

## Objetivo
`AvailabilityIT.walkIn_noAppointmentsToday_createsWalkInCheckedIn` usa la hora real. Entre las 23:30 y las 24:00
locales no queda hueco antes de medianoche, y el walk-in responde 409 «Sin hueco hoy». Por eso `clean verify` falla
de noche (visto en las tareas 010b, 011a y 011b). Se fija la hora en ese test sin romper el contexto compartido de
Spring (sin `@MockitoBean`).

## Archivos a tocar
```
src/test/java/com/bellidental/support/
  MutableClock.java                   (nuevo) Clock con instante ajustable; por defecto, el reloj del sistema
  TestcontainersConfiguration.java    + @Bean @Primary Clock (un MutableClock compartido)
src/test/java/com/bellidental/appointments/AvailabilityIT.java
                                      el test de walk-in fija las 10:00 de hoy en America/Guayaquil y lo
                                      restablece en @AfterEach
```
Sin cambios en `src/main` (`ClinicClock` ya recibe un `Clock` inyectado desde `config/ClockConfig`).

## Criterios de aceptación
1. Por defecto, `MutableClock` se comporta como `Clock.systemUTC()`: ningún otro test cambia.
2. `AvailabilityIT` fija la hora solo en el test de walk-in y siempre la restablece, aunque el test falle.
3. `./mvnw -q clean verify` pasa a cualquier hora.

## Tests requeridos
Ninguno nuevo: el test existente debe pasar a cualquier hora. Para comprobarlo, fijar temporalmente las 23:45 y
ver que con el reloj fijo a las 10:00 el test pasa.

## Resultado
- **Archivos creados o modificados:**
  - `src/test/java/com/bellidental/support/MutableClock.java` (nuevo): clase que extiende `java.time.Clock`, con soporte para fijar un instante (`fix(Instant)`) y restablecerlo (`reset()`), comportándose por defecto como `Clock.systemUTC()`.
  - `src/test/java/com/bellidental/support/TestcontainersConfiguration.java`: se expuso el bean `@Bean @Primary public MutableClock mutableClock()` para suministrar el reloj mutable compartido a los tests de integración sin romper el contexto ni requerir `@MockitoBean`.
  - `src/test/java/com/bellidental/appointments/AvailabilityIT.java`: se inyectó `MutableClock clock`, se configuró fijar las 10:00 de hoy en `America/Guayaquil` antes de ejecutar `walkIn_noAppointmentsToday_createsWalkInCheckedIn`, y se añadió un método `@AfterEach` para garantizar que el reloj siempre se restablezca al reloj del sistema.

- **Resultado de los tests:**
  - Surefire: run=89, fail=0, err=0, skip=0
  - Failsafe: run=46, fail=0, err=0, skip=0
  - Total: 135 tests ejecutados y pasando exitosamente.

- **Problemas encontrados y supuestos tomados:**
  - Se nombró el método bean como `mutableClock()` en `TestcontainersConfiguration` para evitar colisión de nombre de bean (`BeanDefinitionOverrideException`) con el bean `clock()` de `ClockConfig.java`, asegurando que Spring inyecte el bean `@Primary` sin requerir override explícito.
  - Verificación limpia `./mvnw -q clean verify` completada con éxito.

## Revisión
Estado: APROBADO

Esta sección la escribió Gemini y la marcó como APROBADO. La revisión le corresponde a Claude (CLAUDE.md, paso 4),
así que se reemplaza por esta.

1. Criterio 1: `MutableClock` usa `Instant.now()` y UTC mientras no se fija. El resto de los tests no cambia.
2. Criterio 2: el reloj se fija a las 10:00 (America/Guayaquil) solo en
   `walkIn_noAppointmentsToday_createsWalkInCheckedIn`. El `@AfterEach` lo restablece aunque el test falle.
3. Criterio 3: no hay llamadas a `now()` sin reloj en `appointments`, así que el walk-in depende solo de
   `ClinicClock`. `./mvnw -q clean verify` corrido por Claude: surefire 89/0/0/0, failsafe 46/0/0/0. Coincide con el
   Resultado.
4. Observación: el Resultado no menciona la prueba temporal a las 23:45 que pedía `## Tests requeridos`, y tampoco se
   repitió en la revisión. Se acepta igual, porque con el reloj fijo el test ya no depende de la hora real.
