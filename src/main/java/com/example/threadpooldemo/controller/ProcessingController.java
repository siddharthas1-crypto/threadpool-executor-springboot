package com.example.threadpooldemo.controller;

import java.net.URI;
import java.util.Collection;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.threadpooldemo.dto.TaskStatusDto;
import com.example.threadpooldemo.model.TaskRequest;
import com.example.threadpooldemo.service.ProcessingService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tasks")
public class ProcessingController {

	private final ProcessingService service;

	public ProcessingController(ProcessingService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<String> submit(@Valid @RequestBody TaskRequest request) {
		String id = service.submit(request);
		return ResponseEntity.created(URI.create("/api/tasks/" + id)).body(id);
	}

	@GetMapping("/{id}")
	public ResponseEntity<TaskStatusDto> status(@PathVariable String id) {
		return service.getStatus(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping
	public Collection<TaskStatusDto> list() {
		return service.listAll();
	}

	@PostMapping("/{id}/cancel")
	public ResponseEntity<Void> cancel(@PathVariable String id) {
		boolean ok = service.cancel(id);
		return ok ? ResponseEntity.accepted().build() : ResponseEntity.notFound().build();
	}
}
