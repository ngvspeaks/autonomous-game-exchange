package com.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simulation.model.SimulationTick;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SimulationApplicationTests {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
        assertNotNull(objectMapper);
    }

    @Test
    void testSimulationTickSerialization() throws Exception {
        SimulationTick tick = new SimulationTick(
                100L,
                System.nanoTime(),
                5,
                new double[]{1.23, 4.56, 7.89},
                "ACTIVE"
        );

        String json = objectMapper.writeValueAsString(tick);
        assertNotNull(json);
        assertTrue(json.contains("\"tickId\":100"));
        assertTrue(json.contains("\"status\":\"ACTIVE\""));

        SimulationTick deserialized = objectMapper.readValue(json, SimulationTick.class);
        assertEquals(100L, deserialized.tickId());
        assertEquals("ACTIVE", deserialized.status());
        assertEquals(3, deserialized.metrics().length);
    }
}
