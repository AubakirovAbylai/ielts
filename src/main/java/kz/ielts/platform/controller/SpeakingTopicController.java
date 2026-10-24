package kz.ielts.platform.controller;

import kz.ielts.platform.entity.SpeakingTopic;
import kz.ielts.platform.entity.User;
import kz.ielts.platform.repository.SpeakingTopicRepository;
import kz.ielts.platform.repository.UserRepository;
import kz.ielts.platform.security.JwtAuthFilter.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Модуль 3, часть 1: банк тем Speaking.
 * Создание тем — учитель; список — учитель и его ученики.
 */
@RestController
@RequestMapping("/api/speaking/topics")
public class SpeakingTopicController {

    private final SpeakingTopicRepository topics;
    private final UserRepository users;

    public SpeakingTopicController(SpeakingTopicRepository topics, UserRepository users) {
        this.topics = topics;
        this.users = users;
    }

    public record TopicRequest(String title, String description, List<String> questions,
                               String cueCard) {}

    /** Плоский DTO темы: без entity User-владельца. */
    public record TopicDto(Long id, String title, String description,
                           List<String> questions, String cueCard, String createdAt) {}

    private static TopicDto toDto(SpeakingTopic t) {
        return new TopicDto(t.getId(), t.getTitle(), t.getDescription(),
                t.getQuestions(), t.getCueCard(),
                t.getCreatedAt() == null ? null : t.getCreatedAt().toString());
    }

    @GetMapping
    public List<TopicDto> list(CurrentUser me) {
        User user = users.findById(me.id()).orElseThrow(this::notFound);
        Long ownerId = me.isTeacher() ? me.id()
                : (user.getTeacher() != null ? user.getTeacher().getId() : null);
        return ownerId == null ? List.of()
                : topics.findByOwnerIdOrderByCreatedAtDesc(ownerId)
                    .stream().map(SpeakingTopicController::toDto).toList();
    }

    @PostMapping
    public TopicDto create(@RequestBody TopicRequest req, CurrentUser me) {
        if (!me.isTeacher()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Только для преподавателя");
        }
        if (req.title() == null || req.title().isBlank() || req.questions() == null
                || req.questions().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "title и хотя бы один question обязательны");
        }
        User owner = users.findById(me.id()).orElseThrow(this::notFound);
        SpeakingTopic topic = new SpeakingTopic(owner, req.title().trim());
        topic.setDescription(req.description());
        topic.setQuestions(req.questions().stream().map(String::trim).filter(q -> !q.isEmpty()).toList());
        topic.setCueCard(req.cueCard());
        return toDto(topics.save(topic));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, CurrentUser me) {
        if (!me.isTeacher()) throw forbidden();
        SpeakingTopic topic = topics.findById(id).orElseThrow(this::notFound);
        if (!topic.getOwner().getId().equals(me.id())) throw forbidden();
        topics.delete(topic);
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Не найдено");
    }
}
