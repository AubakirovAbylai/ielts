package kz.ielts.platform.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Сессия практики Speaking (этап 1: вопрос → ответ голосом → отчёт).
 * Ответы ученика записываются фронтендом; сюда сохраняется транскрипция.
 */
@Entity
@Table(name = "speaking_session",
        indexes = {
                @Index(name = "ix_speaking_session_student", columnList = "student_id"),
                @Index(name = "ix_speaking_session_topic", columnList = "topic_id")
        })
public class SpeakingSession {

    public enum Part { PART_1, PART_2, PART_3, FULL_TEST, TOPIC_PRACTICE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_speaking_student"))
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_speaking_topic"))
    private SpeakingTopic topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Part part = Part.TOPIC_PRACTICE;

    /** Ответы сессии, упорядоченные по orderIndex. */
    @OneToMany(mappedBy = "session", fetch = FetchType.EAGER, orphanRemoval = true,
            cascade = CascadeType.ALL)
    @OrderBy("orderIndex ASC")
    private List<SpeakingAnswer> answers = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.DRAFT;

    /** Отчёт ИИ (markdown), заполняется после завершения сессии. */
    @Column(length = 30000)
    private String report;

    /** Баллы ИИ по критериям Speaking: FC, LR, GRA, Pronunciation. */
    @Column(name = "score_fluency")        private Double scoreFluency;
    @Column(name = "score_lexical")        private Double scoreLexical;
    @Column(name = "score_grammar")        private Double scoreGrammar;
    @Column(name = "score_pronunciation")  private Double scorePronunciation;
    @Column(name = "score_overall")        private Double scoreOverall;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    public enum Status { DRAFT, FINISHED, REPORTED }

    public SpeakingSession() {}

    public SpeakingSession(User student, SpeakingTopic topic, Part part) {
        this.student = student;
        this.topic = topic;
        this.part = part;
    }

    /** Добавить пару вопрос/ответ в конец сессии (part — PART_1/PART_2/PART_3 или null). */
    public void addAnswer(String part, String question, String answer, Integer durationSec) {
        SpeakingAnswer a = new SpeakingAnswer(part, question, answer, durationSec, answers.size());
        a.setSession(this);
        this.answers.add(a);
    }

    public void markFinished() {
        this.status = Status.FINISHED;
        this.finishedAt = Instant.now();
    }

    public void applyReport(String report, double fc, double lr, double gra, double pron) {
        this.report = report;
        this.scoreFluency = fc; this.scoreLexical = lr;
        this.scoreGrammar = gra; this.scorePronunciation = pron;
        this.scoreOverall = IeltsBandMath.round((fc + lr + gra + pron) / 4.0);
        this.status = Status.REPORTED;
    }

    public Long getId() { return id; }
    public User getStudent() { return student; }
    public SpeakingTopic getTopic() { return topic; }
    public Part getPart() { return part; }
    public void setPart(Part part) { this.part = part; }
    public List<SpeakingAnswer> getAnswers() { return answers; }
    public Status getStatus() { return status; }
    public String getReport() { return report; }
    public Double getScoreFluency() { return scoreFluency; }
    public Double getScoreLexical() { return scoreLexical; }
    public Double getScoreGrammar() { return scoreGrammar; }
    public Double getScorePronunciation() { return scorePronunciation; }
    public Double getScoreOverall() { return scoreOverall; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
