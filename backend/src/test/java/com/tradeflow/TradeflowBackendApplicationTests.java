package com.tradeflow;

import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TradeflowBackendApplicationTests {
	@Autowired
	private MeterRegistry meterRegistry;

	@Test
	void contextLoads() {
	}

	@Test
	void registersOutboxBacklogMetrics() {
		assertThat(meterRegistry.find("tradeflow.outbox.events").tag("status", "pending").gauge()).isNotNull();
		assertThat(meterRegistry.find("tradeflow.outbox.events").tag("status", "dead").gauge()).isNotNull();
	}

}
