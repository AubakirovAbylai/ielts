package kz.ielts.platform.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Картинка задания Writing Task 1 (график/таблица/диаграмма).
 * Хранится в БД байтами: диск Render эфемерный, файлы терялись при каждом деплое.
 */
@Entity
@Table(name = "uploaded_image",
        indexes = @Index(name = "ix_image_owner", columnList = "owner_id"))
public class UploadedImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_image_owner"))
    private User owner;

    @Column(nullable = false, length = 100)
    private String filename;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private byte[] data;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UploadedImage() {}

    public UploadedImage(User owner, String filename, String contentType, byte[] data) {
        this.owner = owner;
        this.filename = filename;
        this.contentType = contentType;
        this.data = data;
        this.sizeBytes = data.length;
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public String getFilename() { return filename; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
}
