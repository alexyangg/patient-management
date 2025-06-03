package com.pm.analyticsservice.kafka;

import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import patient.events.PatientEvent;

// whenever the Spring Boot application starts, it registers this Kafka consumer as a Spring Bean
// and will automatically start the Kafka listener
@Service
public class KafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumer.class);

    // any events sent to the Patient topic are going to be consumed by this method
    @KafkaListener(topics = "patient", groupId = "analytics=service")
    public void consumeEvent(byte[] event) {
        try {
            // byte[] event may not be type compatible with the patientEvent Java class
            PatientEvent patientEvent = PatientEvent.parseFrom(event);

            // perform any business logic related to analytics here

            log.info("Received Patient Event: [PatientId={}, PatientName={}, PatientEmail={}]",
                    patientEvent.getPatientId(),
                    patientEvent.getName(),
                    patientEvent.getEmail());
        } catch (InvalidProtocolBufferException e) {
            log.error("Error deserializing event {}", e.getMessage());
        }
    }
}
