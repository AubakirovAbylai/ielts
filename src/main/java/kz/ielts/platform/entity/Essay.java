package kz.ielts.platform.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Эссе ученика + служебные метрики честности (античитинг).
 * Результат проверки хранится в EssayCheck.
 */
@Entity
@Table(name = "essay",
        indexes = {
                @Index(name = "ix_essay_student", columnList = "student_id"),
                @Index(name = "ix_essay_task", columnList = "task_id")
        })
public class Essay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_essay_student"))
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_essay_task"))
    private WritingTask task;

    @Column(nullable = false, length = 20000)
    private String text;

    @Column(nullable = false)
    private int wordCount;

    /** Античитинг: сколько миллисекунд заняло написание (по данным редактора). */
    @Column(name = "typing_ms", nullable = false)
    private long typingMs;

    /** Античитинг: количество вставок из буфера обмена. */
    @Column(name = "paste_count", nullable = false)
    private int pasteCount;

    /** Было ли написано в экзаменационном режиме с таймером. */
    @Column(name = "exam_mode", nullable = false)
    private boolean examMode;

    /** Номер попытки этого ученика на это задание (1, 2, ...). */
    @Column(nullable = false)
    private int attempt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Essay() {}

    public Essay(User student, WritingTask task, String text, long typingMs,
                 int pasteCount, boolean examMode, int attempt) {
        this.student = student;
        this.task = task;
        this.text = text;
        this.typingMs = typingMs;
        this.pasteCount = pasteCount;
        this.examMode = examMode;
        this.attempt = attempt;
        this.wordCount = countWords(text);
    }

    public static int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    public Long getId() { return id; }
    public User getStudent() { return student; }
    public WritingTask getTask() { return task; }
    public String getText() { return text; }
    public void setText(String text) {
        this.text = text;
        this.wordCount = countWords(text);
    }
    public int getWordCount() { return wordCount; }
    public long getTypingMs() { return typingMs; }
    public void setTypingMs(long typingMs) { this.typingMs = typingMs; }
    public int getPasteCount() { return pasteCount; }
    public void setPasteCount(int pasteCount) { this.pasteCount = pasteCount; }
    public boolean isExamMode() { return examMode; }
    public void setExamMode(boolean examMode) { this.examMode = examMode; }
    public int getAttempt() { return attempt; }
    public Instant getCreatedAt() { return createdAt; }
}
