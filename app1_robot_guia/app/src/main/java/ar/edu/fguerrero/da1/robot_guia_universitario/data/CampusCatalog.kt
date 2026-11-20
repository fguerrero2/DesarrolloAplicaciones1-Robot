package ar.edu.fguerrero.da1.robot_guia_universitario.data

import ar.edu.fguerrero.da1.robot_guia_universitario.domain.Destination
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.DestinationType
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SafePoint

/** Catálogo local de destinos del campus: disponible siempre, aun sin conexión. Coordenadas en metros. */
object CampusCatalog {
    const val WIDTH_M = 30.0
    const val HEIGHT_M = 20.0
    const val SAFE_X = SafePoint.X
    const val SAFE_Y = SafePoint.Y
    const val SAFE_POINT_NAME = SafePoint.NAME

    val destinations = listOf(
        Destination("aula-101", "Aula 101", DestinationType.AULA, "Edificio A", 1, 6.0, 4.0),
        Destination("aula-204", "Aula 204", DestinationType.AULA, "Edificio A", 2, 10.0, 14.0),
        Destination("aula-305", "Aula 305", DestinationType.AULA, "Edificio A", 3, 12.0, 8.0),
        Destination("lab-robotica", "Laboratorio de Robótica", DestinationType.LABORATORIO, "Edificio B", 1, 22.0, 5.0),
        Destination("lab-info3", "Laboratorio de Informática 3", DestinationType.LABORATORIO, "Edificio B", 2, 25.0, 13.0),
        Destination("aud-magna", "Auditorio Magna", DestinationType.AUDITORIO, "Edificio C", 0, 15.0, 18.0),
        Destination("of-alumnos", "Oficina de Alumnos", DestinationType.OFICINA, "Edificio A", 0, 3.0, 12.0),
        Destination("of-decanato", "Decanato", DestinationType.OFICINA, "Edificio C", 1, 27.0, 18.0),
    )
}
