package kz.ielts.platform.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Набор критериев/материалов преподавателя с версией.
 * content — сами критерии и инструкции для ИИ (markdown);
 * путь к файлам книг не хранится: MVP работает с текстовыми критериями.
 */
@Entity
@Table(name = "criteria_set",
        indexes = @Index(name = "ix_criteria_owner", columnList = "owner_id"))
public class CriteriaSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_criteria_owner"))
    private User owner;

    @Column(nullable = false, length = 160)
    private String title;

    /** Версия набора: 1, 2, ... Преподаватель может создавать новые версии. */
    @Column(nullable = false)
    private int version = 1;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public CriteriaSet() {}

    public CriteriaSet(User owner, String title, String content) {
        this.owner = owner;
        this.title = title;
        this.content = content;
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
}
