package com.proyecto.service.port.in;

import com.proyecto.dto.ProcessingResult;
import com.proyecto.model.TrafficObservation;

/** Puerto de entrada que implementará el servicio de aplicación en R2.3. */
public interface ProcessTrafficObservationUseCase {
    /**
     * Procesa una observación válida y devuelve un resultado interno no nulo.
     * IGNORED indica que no se produjo actualización, sin precisar su causa.
     * Los errores de dominio o colaboradores se propagan, sin convertirlos en éxito.
     * No implica confirmación de offsets Kafka ni una transacción entre brokers.
     *
     * @throws IllegalArgumentException si la observación es nula o viola una regla de dominio
     */
    ProcessingResult process(TrafficObservation observation);
}
