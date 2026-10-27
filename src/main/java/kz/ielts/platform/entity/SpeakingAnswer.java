package kz.ielts.platform.entity;

import jakarta.persistence.*;

/**
 * Один ответ в Speaking-сессии: вопрос, транскрипция ответа ученика,
 * длительность и позиция в сессии.
 */
@Entity
@Table(name = "speaking_answer",
        indexes = @Index(name = "ix_speaking_answer_session", columnList = "session_id"))
public class SpeakingAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_speaking_answer_session"))
    private SpeakingSession session;

    @Column(nullable = false, length = 1000)
    private String question;

    @Column(length = 8000)
    private String answer;

    @Column(name = "duration_sec")
    private Integer durationSec;

    /** Часть экзамена: PART_1 / PART_2 / PART_3 (в full test — часть конкретного вопроса). */
    @Column(length = 20)
    private String part;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    public SpeakingAnswer() {}

    public SpeakingAnswer(String part, String question, String answer, Integer durationSec, int orderIndex) {
        this.part = part;
        this.question = question == null ? "" : question;
        this.answer = answer == null ? "" : answer;
        this.durationSec = durationSec == null ? 0 : durationSec;
        this.orderIndex = orderIndex;
    }

    public Long getId() { return id; }
    public SpeakingSession getSession() { return session; }
    public void setSession(SpeakingSession session) { this.session = session; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public Integer getDurationSec() { return durationSec; }
    public String getPart() { return part; }
    public int getOrderIndex() { return orderIndex; }
}
