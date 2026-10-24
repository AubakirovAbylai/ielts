package kz.ielts.platform.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "student_group")
public class StudentGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    /** Владелец группы — преподаватель. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_group_teacher"))
    private User teacher;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @ManyToMany(mappedBy = "groups")
    private Set<User> students = new LinkedHashSet<>();

    public StudentGroup() {}

    public StudentGroup(String name, User teacher) {
        this.name = name;
        this.teacher = teacher;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public User getTeacher() { return teacher; }
    public void setTeacher(User teacher) { this.teacher = teacher; }
    public Instant getCreatedAt() { return createdAt; }
    public Set<User> getStudents() { return students; }
}
