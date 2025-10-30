<<<<<<< HEAD
# threadpool-executor-springboot
=======
# ThreadPool Executor Spring Boot Demo

This project demonstrates a Spring Boot application that uses a configurable ThreadPoolExecutor to process simulated image-processing tasks.

Features:
- Configurable thread pool via `application.properties`
- Custom `RejectedExecutionHandler` that drops oldest queued tasks to accept new ones
- Task repository to query statuses
- REST endpoints to submit/cancel/query tasks
- Scheduled monitor that logs executor stats
- Unit tests for service and controller layers

Run:
```
./mvnw spring-boot:run
```

API:
- POST `/api/tasks` with `{ "fileName": "img.jpg", "complexity": 3 }` → 201 Created, returns id
- GET `/api/tasks/{id}` → 200 OK returns status
- GET `/api/tasks` → list
- POST `/api/tasks/{id}/cancel` → cancel
>>>>>>> c1af778 (Initial commit: add ThreadPoolExecutor Spring Boot project)
