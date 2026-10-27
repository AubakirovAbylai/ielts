package kz.ielts.platform.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Банк тем Speaking: тема + список вопросов.
 * Тема принадлежит преподавателю; вопросы хранятся по частям экзамена.
 */
@Entity
@Table(name = "speaking_topic",
        indexes = @Index(name = "ix_speaking_topic_owner", columnList = "owner_id"))
public class SpeakingTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_speaking_topic_owner"))
    private User owner;

    @Column(nullable = false, length = 160)
    private String title;   // например "Education", "Technology"

    @Column(length = 1000)
    private String description;

    /** Part 1 questions: short answers on familiar topics (4-6 questions). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "speaking_question",
            joinColumns = @JoinColumn(name = "topic_id", nullable = false),
            indexes = @Index(name = "ix_speaking_question_topic", columnList = "topic_id"))
    @Column(name = "text", nullable = false, length = 1000)
    private List<String> part1Questions = new ArrayList<>();

    /** Cue card for Part 2 (task text with "You should say..." points). */
    @Column(length = 2000)
    private String cueCard;

    /** Part 3 questions: discussion questions related to the Part 2 topic (2-6 questions). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "speaking_question_p3",
            joinColumns = @JoinColumn(name = "topic_id", nullable = false),
            indexes = @Index(name = "ix_speaking_question_p3_topic", columnList = "topic_id"))
    @Column(name = "text", nullable = false, length = 1000)
    private List<String> part3Questions = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();


    public SpeakingTopic() {}

    public SpeakingTopic(User owner, String title) {
        this.owner = owner;
        this.title = title;
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getPart1Questions() { return part1Questions; }
    public void setPart1Questions(List<String> questions) {
        this.part1Questions = questions == null ? new ArrayList<>() : new ArrayList<>(questions);
    }
    public List<String> getPart3Questions() { return part3Questions; }
    public void setPart3Questions(List<String> questions) {
        this.part3Questions = questions == null ? new ArrayList<>() : new ArrayList<>(questions);
    }
    public String getCueCard() { return cueCard; }
    public void setCueCard(String cueCard) { this.cueCard = cueCard; }
    public Instant getCreatedAt() { return createdAt; }
    /** Есть ли материал хотя бы в одной части. */
    public boolean isEmpty() {
        return part1Questions.isEmpty() && part3Questions.isEmpty()
                && (cueCard == null || cueCard.isBlank());
    }
}
