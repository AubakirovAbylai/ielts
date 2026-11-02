package kz.ielts.platform.controller;

import kz.ielts.platform.entity.CriteriaSet;
import kz.ielts.platform.entity.EssayCheck;
import kz.ielts.platform.entity.User;
import kz.ielts.platform.entity.WritingTask;
import kz.ielts.platform.repository.CriteriaSetRepository;
import kz.ielts.platform.repository.EssayCheckRepository;
import kz.ielts.platform.repository.UserRepository;
import kz.ielts.platform.repository.WritingTaskRepository;
import kz.ielts.platform.security.JwtAuthFilter.CurrentUser;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
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
    private final EssayCheckRepository checks;
    private final UserRepository users;

    public WritingController(WritingTaskRepository tasks, CriteriaSetRepository criteria,
                             EssayCheckRepository checks, UserRepository users) {
        this.tasks = tasks;
        this.criteria = criteria;
        this.checks = checks;
        this.users = users;
    }

    // ---------- Банк заданий ----------

    public record TaskRequest(String taskType, String moduleType, String promptKind,
                              String prompt, String imageUrl, String topic) {}

    /** Плоский DTO задания: без entity User-владельца. */
    public record TaskDto(Long id, String taskType, String moduleType, String promptKind,
                          String prompt, String imageUrl, int minWords, int timeLimitMin,
                          String createdAt, String topic) {}

    private static TaskDto toDto(WritingTask t) {
        return new TaskDto(t.getId(),
                t.getTaskType() == null ? null : t.getTaskType().name(),
                t.getModuleType() == null ? null : t.getModuleType().name(),
                t.getPromptKind(), t.getPrompt(),
                t.getImageUrl() == null ? "" : t.getImageUrl(),
                t.getMinWords(), t.getTimeLimitMin(),
                t.getCreatedAt() == null ? null : t.getCreatedAt().toString(),
                t.getTopic() == null || t.getTopic().isBlank() ? "General" : t.getTopic());
    }

    /** Плоский DTO набора критериев. taskType: TASK_1 / TASK_2 / null (оба задания). */
    public record CriteriaDto(Long id, String title, int version, String content,
                              boolean active, String createdAt, String taskType) {}

    private static CriteriaDto toDto(CriteriaSet c) {
        return new CriteriaDto(c.getId(), c.getTitle(), c.getVersion(), c.getContent(),
                c.isActive(), c.getCreatedAt() == null ? null : c.getCreatedAt().toString(),
                c.getTaskType());
    }

    /** "TASK_1" / "TASK_2" / null (= для обоих заданий). Всё прочее — 400. */
    private static String normTaskType(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("ANY") || raw.equalsIgnoreCase("BOTH")) {
            return null;
        }
        String v = raw.trim().toUpperCase();
        if (!v.equals("TASK_1") && !v.equals("TASK_2")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taskType must be TASK_1, TASK_2 or empty");
        }
        return v;
    }

    private static boolean sameScope(CriteriaSet c, String taskType) {
        return java.util.Objects.equals(c.getTaskType(), taskType);
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
        task.setTopic(req.topic() == null || req.topic().isBlank() ? null : req.topic().trim());
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

    public record CriteriaRequest(String title, String content, Boolean newVersion, String taskType) {}

    @GetMapping("/criteria")
    public List<CriteriaDto> listCriteria(CurrentUser me) {
        requireTeacher(me);
        return criteria.findByOwnerIdOrderByCreatedAtDesc(me.id())
                .stream().filter(c -> !c.isArchived()).map(WritingController::toDto).toList();
    }

    /** Создать набор критериев; если набор с таким title существует — новая версия. */
    @PostMapping("/criteria")
    public CriteriaDto createCriteria(@RequestBody CriteriaRequest req, CurrentUser me) {
        requireTeacher(me);
        if (req.content() == null || req.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Criteria content is empty");
        }
        User owner = users.findById(me.id()).orElseThrow(this::notFound);
        return toDto(saveNewVersion(owner, req.title(), req.content().trim(), normTaskType(req.taskType())));
    }

    /**
     * Загрузка критериев из PDF (официальные band-дескрипторы и т.п.):
     * извлекает текст и сохраняет как новый (или новую версию) набор критериев.
     * multipart: file (PDF), title (optional — по умолчанию имя файла).
     */
    @PostMapping("/criteria/upload")
    public CriteriaDto uploadCriteriaPdf(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "title", required = false) String title,
                                         @RequestParam(value = "taskType", required = false) String taskType,
                                         CurrentUser me) {
        requireTeacher(me);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF file is required");
        }
        String text;
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            text = new PDFTextStripper().getText(doc);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not read the PDF: " + e.getMessage());
        }
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not extract text from the PDF (it may be a scan without a text layer)");
        }
        String effectiveTitle = (title == null || title.isBlank())
                ? stripExtension(file.getOriginalFilename()) : title.trim();
        User owner = users.findById(me.id()).orElseThrow(this::notFound);
        return toDto(saveNewVersion(owner, effectiveTitle, text.trim(), normTaskType(taskType)));
    }

    private CriteriaSet saveNewVersion(User owner, String title, String content, String taskType) {
        List<CriteriaSet> all = criteria.findByOwnerIdOrderByCreatedAtDesc(owner.getId());
        String effectiveTitle = (title == null || title.isBlank()) ? "Criteria" : title.trim();
        int nextVersion = all.stream()
                .filter(c -> c.getTitle().equals(effectiveTitle))
                .mapToInt(CriteriaSet::getVersion).max().orElse(0) + 1;
        // Один активный набор на задание: новый набор для Task 1 деактивирует
        // только прежние наборы Task 1 (Task 2 — аналогично; общий — только общие).
        all.stream().filter(c -> sameScope(c, taskType) && c.isActive())
                .forEach(c -> { c.setActive(false); criteria.save(c); });

        CriteriaSet set = new CriteriaSet(owner, effectiveTitle, content);
        set.setVersion(nextVersion);
        set.setTaskType(taskType);
        return criteria.save(set);
    }

    private static String stripExtension(String name) {
        if (name == null || name.isBlank()) return "Criteria from PDF";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @PutMapping("/criteria/{id}/activate")
    public void activateCriteria(@PathVariable Long id, CurrentUser me) {
        requireTeacher(me);
        CriteriaSet set = criteria.findById(id).orElseThrow(this::notFound);
        if (!set.getOwner().getId().equals(me.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        // Активация исключающая в пределах задания (Task 1 / Task 2 / общий).
        criteria.findByOwnerIdOrderByCreatedAtDesc(me.id()).stream()
                .filter(c -> sameScope(c, set.getTaskType()))
                .forEach(c -> {
            boolean activate = c.getId().equals(id);
            if (c.isActive() != activate) {
                c.setActive(activate);
                criteria.save(c);
            }
        });
    }

    /**
     * Удалить набор критериев. Если по нему уже проверялись эссе (FK из essay_check),
     * физически не удаляем — архивируем: набор пропадает из списка и перестаёт быть
     * активным, а старые проверки сохраняют ссылку на свою версию критериев.
     */
    @DeleteMapping("/criteria/{id}")
    public java.util.Map<String, Object> deleteCriteria(@PathVariable Long id, CurrentUser me) {
        requireTeacher(me);
        CriteriaSet set = criteria.findById(id).orElseThrow(this::notFound);
        if (!set.getOwner().getId().equals(me.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        List<EssayCheck> refs = checks.findByCriteriaSetId(id);
        if (!refs.isEmpty()) {
            set.setActive(false);
            set.setArchived(true);
            criteria.save(set);
            return java.util.Map.of("deleted", true, "archived", true, "usedInChecks", refs.size());
        }
        criteria.delete(set);
        return java.util.Map.of("deleted", true, "archived", false, "usedInChecks", 0);
    }

    @GetMapping("/active-criteria")
    public CriteriaDto getActiveCriteria(@RequestParam(value = "taskType", required = false) String taskType,
                                         CurrentUser me) {
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
        String tt = normTaskType(taskType);
        CriteriaSet set = (tt == null
                ? criteria.findFirstByOwnerIdAndActiveTrueOrderByIdDesc(teacherId)
                : criteria.findActiveFor(teacherId, tt))
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
