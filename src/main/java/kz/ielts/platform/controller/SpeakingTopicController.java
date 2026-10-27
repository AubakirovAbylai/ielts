package kz.ielts.platform.controller;

import kz.ielts.platform.entity.SpeakingTopic;
import kz.ielts.platform.entity.User;
import kz.ielts.platform.repository.SpeakingTopicRepository;
import kz.ielts.platform.repository.UserRepository;
import kz.ielts.platform.security.JwtAuthFilter.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
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

    public record TopicRequest(String title, String description,
                               List<String> part1Questions, String cueCard,
                               List<String> part3Questions) {}

    /** Плоский DTO темы: без entity User-владельца. */
    public record TopicDto(Long id, String title, String description,
                           List<String> part1Questions, String cueCard,
                           List<String> part3Questions, String createdAt) {}

    private static TopicDto toDto(SpeakingTopic t) {
        return new TopicDto(t.getId(), t.getTitle(), t.getDescription(),
                t.getPart1Questions(), t.getCueCard(), t.getPart3Questions(),
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Teachers only");
        }
        validate(req);
        User owner = users.findById(me.id()).orElseThrow(this::notFound);
        SpeakingTopic topic = new SpeakingTopic(owner, req.title().trim());
        applyRequest(topic, req);
        return toDto(topics.save(topic));
    }

    @PutMapping("/{id}")
    public TopicDto update(@PathVariable Long id, @RequestBody TopicRequest req, CurrentUser me) {
        if (!me.isTeacher()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Teachers only");
        }
        validate(req);
        SpeakingTopic topic = topics.findById(id).orElseThrow(this::notFound);
        if (!topic.getOwner().getId().equals(me.id())) throw forbidden();
        topic.setTitle(req.title().trim());
        applyRequest(topic, req);
        return toDto(topics.save(topic));
    }

    private void validate(TopicRequest req) {
        if (req.title() == null || req.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required");
        }
        boolean empty = isEmpty(req.part1Questions()) && isEmpty(req.part3Questions())
                && (req.cueCard() == null || req.cueCard().isBlank());
        if (empty) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Add at least one question, or a cue card");
        }
    }

    private void applyRequest(SpeakingTopic topic, TopicRequest req) {
        topic.setDescription(req.description());
        topic.setPart1Questions(clean(req.part1Questions()));
        topic.setCueCard(req.cueCard() == null ? null : req.cueCard().trim());
        topic.setPart3Questions(clean(req.part3Questions()));
    }

    private static List<String> clean(List<String> questions) {
        if (questions == null) return new ArrayList<>();
        return questions.stream().map(String::trim).filter(q -> !q.isEmpty()).toList();
    }

    private static boolean isEmpty(List<String> questions) {
        return questions == null || questions.stream()
                .allMatch(q -> q == null || q.trim().isEmpty());
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
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
    }
}
