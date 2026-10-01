package com.miniciv;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HealthController {

	record Health(String status) {
	}

	@GetMapping("/api/health")
	Health health() {
		return new Health("UP");
	}

}
