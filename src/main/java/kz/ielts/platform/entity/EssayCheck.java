package kz.ielts.platform.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Результат ИИ-проверки эссе: баллы по 4 критериям, разбор, рекомендации.
 * Хранит эталонную версию критериев, по которой прошла проверка.
 * Преподаватель может скорректировать оценку — тогда original_* сохраняют
 * версию ИИ, а итоговые поля перезаписываются.
 */
@Entity
@Table(name = "essay_check",
        indexes = {
                @Index(name = "ix_check_essay", columnList = "essay_id"),
                @Index(name = "ix_check_criteria", columnList = "criteria_set_id")
        })
public class EssayCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "essay_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_check_essay"))
    private Essay essay;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criteria_set_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_check_criteria"))
    private CriteriaSet criteriaSet;

    /** Баллы ИИ по критериям (0–9 с шагом 0.5). */
    @Column(name = "ai_task_response", nullable = false)     private double aiTaskResponse;
    @Column(name = "ai_coherence", nullable = false)         private double aiCoherence;
    @Column(name = "ai_lexical", nullable = false)           private double aiLexical;
    @Column(name = "ai_grammar", nullable = false)           private double aiGrammar;
    @Column(name = "ai_overall", nullable = false)           private double aiOverall;

    /** Итоговые баллы: после проверки совпадают с ИИ, после корректировки — с преподавателем. */
    @Column(name = "final_task_response", nullable = false) private double finalTaskResponse;
    @Column(name = "final_coherence", nullable = false)      private double finalCoherence;
    @Column(name = "final_lexical", nullable = false)        private double finalLexical;
    @Column(name = "final_grammar", nullable = false)        private double finalGrammar;
    @Column(name = "final_overall", nullable = false)        private double finalOverall;

    /** Полный текст отчёта ИИ (markdown). */
    @Column(nullable = false, length = 30000)
    private String report;

    /** Комментарий преподавателя при корректировке. */
    @Column(length = 4000)
    private String teacherComment;

    @Column(name = "checked_at", nullable = false, updatable = false)
    private Instant checkedAt = Instant.now();

    public EssayCheck() {}

    public EssayCheck(Essay essay, CriteriaSet criteriaSet,
                      double tr, double cc, double lr, double gra, String report) {
        this.essay = essay;
        this.criteriaSet = criteriaSet;
        this.aiTaskResponse = tr; this.aiCoherence = cc;
        this.aiLexical = lr; this.aiGrammar = gra;
        this.aiOverall = IeltsBandMath.round((tr + cc + lr + gra) / 4.0);
        this.finalTaskResponse = tr; this.finalCoherence = cc;
        this.finalLexical = lr; this.finalGrammar = gra;
        this.finalOverall = this.aiOverall;
        this.report = report;
    }

    public Long getId() { return id; }
    public Essay getEssay() { return essay; }
    public CriteriaSet getCriteriaSet() { return criteriaSet; }

    public double getAiTaskResponse() { return aiTaskResponse; }
    public double getAiCoherence() { return aiCoherence; }
    public double getAiLexical() { return aiLexical; }
    public double getAiGrammar() { return aiGrammar; }
    public double getAiOverall() { return aiOverall; }

    public double getFinalTaskResponse() { return finalTaskResponse; }
    public double getFinalCoherence() { return finalCoherence; }
    public double getFinalLexical() { return finalLexical; }
    public double getFinalGrammar() { return finalGrammar; }
    public double getFinalOverall() { return finalOverall; }

    public String getReport() { return report; }
    public void setReport(String report) { this.report = report; }
    public String getTeacherComment() { return teacherComment; }
    public void setTeacherComment(String teacherComment) { this.teacherComment = teacherComment; }
    public Instant getCheckedAt() { return checkedAt; }

    /** Корректировка преподавателем: итоговые баллы меняются, ИИ-версия остаётся. */
    public void applyTeacherOverride(double tr, double cc, double lr, double gra,
                                     String comment) {
        this.finalTaskResponse = tr; this.finalCoherence = cc;
        this.finalLexical = lr; this.finalGrammar = gra;
        this.finalOverall = IeltsBandMath.round((tr + cc + lr + gra) / 4.0);
        this.teacherComment = comment;
    }
}
