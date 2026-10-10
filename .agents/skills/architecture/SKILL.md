Estructura de cada módulo `com.bellidental.<modulo>` (modelo: `identity`). Solo se crean las carpetas que el módulo usa.

```
<modulo>/
  <Modulo>Directory.java   interfaz pública para otros módulos (raíz del paquete)
  <Modulo>Summary.java     records que devuelve esa interfaz (raíz del paquete)
  api/                     controladores REST
  api/dto/                 records de request y response
  application/             servicios con la lógica de negocio e implementación de <Modulo>Directory
  domain/                  solo si hace falta: reglas puras sin Spring ni repositorios
                           (máquina de estados, cálculo de huecos, CPO), enums propios del módulo
```

Reglas:
- Otro módulo solo importa la raíz del paquete (`<Modulo>Directory` y sus records); nunca `api`, `application` ni `domain`.
- Entidades en `persistence/entity`, repositorios en `persistence/repository` (compartidos por todos los módulos).
- Enums que una entidad persiste van en `shared/enums` (la capa `persistence` no depende de los módulos).
- Lo transversal (excepciones, validadores, respuesta común) en `shared/`; seguridad en `security/`; configuración en `config/`.
- Sin más subcarpetas: no `impl/`, `mapper/`, `port/`, `adapter/` ni una clase por carpeta.
- Los tests replican el paquete de la clase que prueban (`<modulo>/application/<Clase>Test`).
- Los módulos existentes con estructura plana (`patients`, `professionals`, `clinicalrecords`) no se reorganizan
  salvo en una tarea dedicada.
