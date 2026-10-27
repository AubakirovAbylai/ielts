package kz.ielts.platform.controller;

import kz.ielts.platform.entity.CriteriaSet;
import kz.ielts.platform.entity.User;
import kz.ielts.platform.entity.WritingTask;
import kz.ielts.platform.repository.CriteriaSetRepository;
import kz.ielts.platform.repository.UserRepository;
import kz.ielts.platform.repository.WritingTaskRepository;
import kz.ielts.platform.security.JwtAuthFilter.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Кабинет преподавателя, часть 1: банк заданий Writing и наборы критериев.
 * Создание/изменение — только учитель; чтение — учитель и его ученики.
 */
@RestController
@RequestMapping("/api/writing")
public class WritingController {

    private final WritingTaskRepository tasks;
    private final CriteriaSetRepository criteria;
    private final UserRepository users;

    public WritingController(WritingTaskRepository tasks, CriteriaSetRepository criteria,
                             UserRepository users) {
        this.tasks = tasks;
        this.criteria = criteria;
        this.users = users;
    }

    // ---------- Банк заданий ----------

    public record TaskRequest(String taskType, String moduleType, String promptKind,
                              String prompt, String imageUrl) {}

    /** Плоский DTO задания: без entity User-владельца. */
    public record TaskDto(Long id, String taskType, String moduleType, String promptKind,
                          String prompt, String imageUrl, int minWords, int timeLimitMin,
                          String createdAt) {}

    private static TaskDto toDto(WritingTask t) {
        return new TaskDto(t.getId(),
                t.getTaskType() == null ? null : t.getTaskType().name(),
                t.getModuleType() == null ? null : t.getModuleType().name(),
                t.getPromptKind(), t.getPrompt(),
                t.getImageUrl() == null ? "" : t.getImageUrl(),
                t.getMinWords(), t.getTimeLimitMin(),
                t.getCreatedAt() == null ? null : t.getCreatedAt().toString());
    }

    /** Плоский DTO набора критериев. */
    public record CriteriaDto(Long id, String title, int version, String content,
                              boolean active, String createdAt) {}

    private static CriteriaDto toDto(CriteriaSet c) {
        return new CriteriaDto(c.getId(), c.getTitle(), c.getVersion(), c.getContent(),
                c.isActive(), c.getCreatedAt() == null ? null : c.getCreatedAt().toString());
    }

    @GetMapping("/tasks")
    public List<TaskDto> listTasks(CurrentUser me) {
        User user = users.findById(me.id()).orElseThrow(this::notFound);
        List<WritingTask> rows = me.isTeacher()
                ? tasks.findByOwnerIdOrderByCreatedAtDesc(me.id())
                : (user.getTeacher() != null
                    ? tasks.findByOwnerIdOrderByCreatedAtDesc(user.getTeacher().getId())
                    : List.<WritingTask>of());
        return rows.stream().map(WritingController::toDto).toList();
    }

    @PostMapping("/tasks")
    public TaskDto createTask(@RequestBody TaskRequest req, CurrentUser me) {
        requireTeacher(me);
        WritingTask task = buildTask(req, null, me);
        return toDto(tasks.save(task));
    }

    @PutMapping("/tasks/{id}")
    public TaskDto updateTask(@PathVariable Long id, @RequestBody TaskRequest req, CurrentUser me) {
        requireTeacher(me);
        WritingTask task = tasks.findById(id).orElseThrow(this::notFound);
        if (!task.getOwner().getId().equals(me.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return toDto(tasks.save(buildTask(req, task, me)));
    }

    private WritingTask buildTask(TaskRequest req, WritingTask task, CurrentUser me) {
        if (req.prompt() == null || req.prompt().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prompt is required");
        }
        WritingTask.TaskType type = WritingTask.TaskType.valueOf(
                req.taskType() == null ? "TASK_2" : req.taskType().toUpperCase());
        WritingTask.ModuleType module = WritingTask.ModuleType.valueOf(
                req.moduleType() == null ? "ACADEMIC" : req.moduleType().toUpperCase());
        if (task == null) {
            User owner = users.findById(me.id()).orElseThrow(this::notFound);
            task = new WritingTask(owner, type, module, req.prompt().trim());
        }
        task.setTaskType(type);
        task.setModuleType(module);
        task.setPrompt(req.prompt().trim());
        task.setPromptKind(req.promptKind());
        task.setImageUrl(req.imageUrl() == null || req.imageUrl().isBlank() ? null : req.imageUrl().trim());
        return task;
    }

    @DeleteMapping("/tasks/{id}")
    public void deleteTask(@PathVariable Long id, CurrentUser me) {
        requireTeacher(me);
        WritingTask task = tasks.findById(id).orElseThrow(this::notFound);
        if (!task.getOwner().getId().equals(me.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        tasks.delete(task);
    }

    // ---------- Критерии ----------

    public record CriteriaRequest(String title, String content, Boolean newVersion) {}

    @GetMapping("/criteria")
    public List<CriteriaDto> listCriteria(CurrentUser me) {
        requireTeacher(me);
        return criteria.findByOwnerIdOrderByCreatedAtDesc(me.id())
                .stream().map(WritingController::toDto).toList();
    }

    /** Создать набор критериев; если набор с таким title существует — новая версия. */
    @PostMapping("/criteria")
    public CriteriaDto createCriteria(@RequestBody CriteriaRequest req, CurrentUser me) {
        requireTeacher(me);
        if (req.content() == null || req.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content is required");
        }
        User owner = users.findById(me.id()).orElseThrow(this::notFound);
        int nextVersion = criteria.findByOwnerIdOrderByCreatedAtDesc(me.id()).stream()
                .filter(c -> c.getTitle().equals(req.title()))
                .mapToInt(CriteriaSet::getVersion).max().orElse(0) + 1;
        // прежние версии деактивируются
        criteria.findByOwnerIdOrderByCreatedAtDesc(me.id()).stream()
                .filter(c -> c.getTitle().equals(req.title()))
                .forEach(c -> { c.setActive(false); criteria.save(c); });

        CriteriaSet set = new CriteriaSet(owner,
                req.title() == null || req.title().isBlank()
                        ? "Criteria v" + nextVersion : req.title(),
                req.content().trim());
        set.setVersion(nextVersion);
        return toDto(criteria.save(set));
    }

    @PutMapping("/criteria/{id}/activate")
    public void activateCriteria(@PathVariable Long id, CurrentUser me) {
        requireTeacher(me);
        CriteriaSet set = criteria.findById(id).orElseThrow(this::notFound);
        if (!set.getOwner().getId().equals(me.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        criteria.findByOwnerIdOrderByCreatedAtDesc(me.id()).stream()
                .filter(c -> c.getTitle().equals(set.getTitle()))
                .forEach(c -> { c.setActive(c.getId().equals(id)); criteria.save(c); });
    }

    @GetMapping("/active-criteria")
    public CriteriaDto getActiveCriteria(CurrentUser me) {
        Long teacherId;
        if (me.isTeacher()) {
            teacherId = me.id();
        } else {
            User user = users.findById(me.id()).orElseThrow(this::notFound);
            if (user.getTeacher() == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No active criteria set found");
            }
            teacherId = user.getTeacher().getId();
        }
        CriteriaSet set = criteria.findFirstByOwnerIdAndActiveTrueOrderByVersionDesc(teacherId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No active criteria set found"));
        return toDto(set);
    }

    private void requireTeacher(CurrentUser me) {
        if (!me.isTeacher()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Teachers only");
        }
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
    }
}
