package kz.ielts.platform.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Результат пробного IELTS-теста, внесённый учеником.
 * Оба типа дат хранятся: дата теста (по словам ученика) и момент записи.
 */
@Entity
@Table(name = "test_result",
        indexes = {
                @Index(name = "ix_test_result_student", columnList = "student_id"),
                @Index(name = "ix_test_result_date", columnList = "testDate")
        })
public class TestResult {

    public enum ExamType { ACADEMIC, GENERAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_result_student"))
    private User student;

    @Column(name = "test_date", nullable = false)
    private LocalDate testDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExamType examType = ExamType.ACADEMIC;

    /** Навыки в баллах IELTS (шаг 0.5, диапазон 1.0–9.0). Nullable — навык не сдавался. */
    @Column private Double listening;
    @Column private Double reading;
    @Column private Double writing;
    @Column private Double speaking;

    /** Вычисляется сервером по правилам IELTS; null, если не все 4 навыка внесены. */
    @Column(name = "overall_band")
    private Double overallBand;

    @Column(length = 160)
    private String source;      // например "Cambridge IELTS 19, Test 2"

    @Column(length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public TestResult() {}

    public TestResult(User student, LocalDate testDate) {
        this.student = student;
        this.testDate = testDate;
    }

    @PrePersist
    @PreUpdate
    void recalcOverall() {
        this.overallBand = IeltsBandMath.overall(listening, reading, writing, speaking);
    }

    public Long getId() { return id; }
    public User getStudent() { return student; }
    public LocalDate getTestDate() { return testDate; }
    public void setTestDate(LocalDate testDate) { this.testDate = testDate; }
    public ExamType getExamType() { return examType; }
    public void setExamType(ExamType examType) { this.examType = examType; }
    public Double getListening() { return listening; }
    public void setListening(Double listening) { this.listening = listening; }
    public Double getReading() { return reading; }
    public void setReading(Double reading) { this.reading = reading; }
    public Double getWriting() { return writing; }
    public void setWriting(Double writing) { this.writing = writing; }
    public Double getSpeaking() { return speaking; }
    public void setSpeaking(Double speaking) { this.speaking = speaking; }
    public Double getOverallBand() { return overallBand; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Instant getCreatedAt() { return createdAt; }
}
