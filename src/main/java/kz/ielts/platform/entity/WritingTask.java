package kz.ielts.platform.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Задание Writing: Task 1 или Task 2, Academic или General Training.
 * Создаётся преподавателем (банк тем).
 */
@Entity
@Table(name = "writing_task",
        indexes = @Index(name = "ix_writing_task_owner", columnList = "owner_id"))
public class WritingTask {

    public enum TaskType { TASK_1, TASK_2 }
    public enum ModuleType { ACADEMIC, GENERAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_task_owner"))
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskType taskType = TaskType.TASK_2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ModuleType moduleType = ModuleType.ACADEMIC;

    /** Тип Task 1: график, таблица, карта, процесс, письмо (GT). Nullable для Task 2. */
    @Column(length = 40)
    private String promptKind;

    @Column(nullable = false, length = 4000)
    private String prompt;      // условие задания

    /** Минимальный лимит слов: 150 для Task 1, 250 для Task 2. */
    @Column(name = "min_words", nullable = false)
    private int minWords = 250;

    /** Рекомендуемое время в минутах: 20 для Task 1, 40 для Task 2. */
    @Column(name = "time_limit_min", nullable = false)
    private int timeLimitMin = 40;
    /** URL картинки задания (Task 1: график/таблица/карта). Отдаётся статикой из data/uploads. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public WritingTask() {}

    public WritingTask(User owner, TaskType taskType, ModuleType moduleType, String prompt) {
        this.owner = owner;
        this.taskType = taskType;
        this.moduleType = moduleType;
        this.prompt = prompt;
        this.minWords = taskType == TaskType.TASK_1 ? 150 : 250;
        this.timeLimitMin = taskType == TaskType.TASK_1 ? 20 : 40;
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public TaskType getTaskType() { return taskType; }
    public void setTaskType(TaskType taskType) {
        this.taskType = taskType;
        this.minWords = taskType == TaskType.TASK_1 ? 150 : 250;
        this.timeLimitMin = taskType == TaskType.TASK_1 ? 20 : 40;
    }
    public ModuleType getModuleType() { return moduleType; }
    public void setModuleType(ModuleType moduleType) { this.moduleType = moduleType; }
    public String getPromptKind() { return promptKind; }
    public void setPromptKind(String promptKind) { this.promptKind = promptKind; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public int getMinWords() { return minWords; }
    public int getTimeLimitMin() { return timeLimitMin; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Instant getCreatedAt() { return createdAt; }
}
