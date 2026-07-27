package de.urr4.rp.roleplayer.web;

import de.urr4.rp.roleplayer.application.SessionService;
import de.urr4.rp.roleplayer.web.dto.CreateSessionRequest;
import de.urr4.rp.roleplayer.web.dto.SessionDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public List<SessionDto> list() {
        return sessionService.listSessions().stream().map(SessionDto::from).toList();
    }

    @PostMapping
    public SessionDto create(@Valid @RequestBody CreateSessionRequest request) {
        return SessionDto.from(sessionService.createSession(request.name()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionDto> get(@PathVariable String id) {
        return sessionService.getSession(id)
                .map(SessionDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
