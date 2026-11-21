# Materia 
Desarrollo de aplicaciones 1

# DesarrolloAplicaciones1-Robot
TPO obligatorio en donde utilicemos el robot de UADE Unitree G1 y realice tareas definidas



> Repositorio de trabajo, documentación y trazabilidad de los TPO de la materia
> **Desarrollo de aplicaciones 1* — UADE — **2C 2026** — 


**Incluye docs y código fuente para cada TPO.** 


## TPO2_UnitreeG1

TPO obligatorio en donde utilicemos el robot de UADE Unitree G1 y realice tareas definidas. Tendremos que desarrollar y probar mediante el gemelo digital que se disponibiliza para dicho robot. 

La idea es separar el proyecto en dos partes: 
* Un backend desarrollado en VsCode para el control y/o simulación del Unitree G1 en una PC con Ubuntu + SDK2/MuJoCo
* Una app desarrollada en Android Studio que actúe como interfaz de mando y visualización del área. 

No tendremos todo junto en una app para Android, ya que esto sería muy pesado y dificil de implementar.

El robot G1 dispone de soporte en Unitree SDK2, incluyendo una API de locomoción de alto nivel para acciones como ponerse de pie, detenerse, caminar con velocidades vx, vy y rotación vyaw, y ejecutar acciones como saludar con la mano. El repositorio oficial incluye específicamente ejemplos para G1


### Definiciones generales

Se define un area con ancho, largo y un punto de partida, luego se debe mostrar un mapa con la ubicacion del rotot y se presenta una opción que permita moverlo hacia otra posición.  

Por ejemplo, se define un area y punto de partida: 
- Ancho: 6 metros
- Largo: 8 metros
- Punto de partida: (0,0)
- Movimiento X = 2 mt , Y = 5 mt

### Arquitectura
Android app (Kotlin + Compose) --> Backend (Unitree SDK2/Python) --> Gemelo digital (MuJoCo G1 3D)



### Link a figma para diseño de los TPO


Link para TPO2_UnitreeG1

Primero bocetamos las 7 pantallas en wireframes de baja fidelidad, para validar el vocabulario, la jerarquía de la información y qué acciones aparecen en cada estado del robot. Después armamos un prototipo de alta fidelidad navegable que simula el robot y permite forzar los escenarios de falla.
Link al archivo en Figma con el wireframe de la idea inicial: 
https://www.figma.com/design/XL3EylIQDrTokhjvvMEqtp/DA1-TPO2-Robot?node-id=8-2&t=BpP7dSTk4UsgpuJp-1

Link al archivo en Figma con el prototipo de pantallas del flujo normal:
https://www.figma.com/design/XL3EylIQDrTokhjvvMEqtp/DA1-TPO2-Robot?node-id=2-3&t=BpP7dSTk4UsgpuJp-1

Link al archivo en Figma con el prototipo de pantallas del flujo de fallas:
https://www.figma.com/design/XL3EylIQDrTokhjvvMEqtp/DA1-TPO2-Robot?node-id=14-2&t=BpP7dSTk4UsgpuJp-1




### Link a Trello para el seguimiento de las tareas requeridas

🗂️ **Tablero TPO2_UnitreeG1:** 

### Backend inicial

Se agregó un backend REST en [backend](backend/) con FastAPI. Arranca en modo `mock` para validar el contrato con Android y permite configurar el área, consultar la posición del G1, moverlo dentro de los límites y detenerlo.

Para iniciar el servicio:

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload
```

El gemelo digital oficial disponible para este trabajo es [Unitree MuJoCo](https://github.com/unitreerobotics/unitree_mujoco), que incluye el modelo `g1`, escenas MJCF y un simulador Python. La integración y las referencias de licencia están documentadas en [backend/README.md](backend/README.md).


## Problema elegido para resolver

### Servicio
Robot guía física para visitantes, alumnos nuevos e invitados dentro del campus universitario, el punto de partida es desde la recepción y el destino puede ser un aula, laboratorio, auditorio u oficina configurada en el mapa.


### Situacion
Es comun en una visita a la universidad o en la primer semana se cursada, estar perdido y no saber como llegar hasta el aula de la materia que debemos cursar. Sería de muchoa ayuda contar con apoyo de un guia que nos pueda indicar como llegar a nuestro destino o incluso nos pueda acompañar en el cambio para no perdernos. Los edificios tienen varios pisos y numeraciones poco intuitivas, los carteles de direcciones son pocas y el personal de la recepción no puede abandonar su puesto para acompañar a cada persona.


### Cómo se resuelve hoy
Se dan indicaciones verbales por parte del personal, siguiendo carteles dispnibles en las paredes o techo de los pasillos, mirando mapas impresos o el mapa del sitio web o app movil. Solo en muy pocos casos se acompaña a la persona hasta su destino, el resto muchas veces se pierde, vuelve a recepción o llega tarde.

### Tarea del robot
Recibir en la recepción la orden para compañar a una persona o grupo (1 a 8 acompañados) hasta un destino indicado por el operador de la recepción, caminar delante del grupo o persona a paso de visitante, esperar si alguien se queda atras, avisar si la ruta está bloqueada y regresar al punto seguro (Recepción) al finalizar el recorrido o bien esperar en el destino hasta la orden de volver.





