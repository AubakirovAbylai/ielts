package kz.ielts.platform.config;

import kz.ielts.platform.entity.SpeakingTopic;
import kz.ielts.platform.entity.User;
import kz.ielts.platform.repository.SpeakingTopicRepository;
import kz.ielts.platform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Идемпотентная миграция данных существующей БД при переходе на Speaking-части (v2):
 * - старое поле questions маппится теперь как part1Questions (та же таблица speaking_question);
 *   для топиков без part3 — дискуссионные вопросы (последняя треть) переносятся в part3;
 * - тема "Family and Relationships" добавляется каждому учителю, у которого её нет
 *   (вопросы из запроса заказчицы);
 * - существующие критерии и прочие данные пользователей не изменяются.
 *
 * В отличие от DataSeeder, выполняется на ЛЮБОЙ базе (не только пустой).
 */
@Component
@Order(20)
public class SpeakingPartsMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SpeakingPartsMigration.class);

    private final SpeakingTopicRepository topics;
    private final UserRepository users;

    public SpeakingPartsMigration(SpeakingTopicRepository topics, UserRepository users) {
        this.topics = topics;
        this.users = users;
    }

    @Override
    public void run(String... args) {
        migrateTopics();
        seedFamilyTopic();
    }

    /**
     * Топики со старым полем questions (без частей): разнести вопросы по частям.
     * part1Questions уже живёт в той же таблице, что старое questions, поэтому данные
     * старой БД уже видны как part1Questions; здесь только добавляем недостающую
     * структуру: cue card пуст → оставить как есть; part3 пуст → сформировать из
     * «дискуссионных» вопросов (последняя треть списка).
     */
    private void migrateTopics() {
        List<SpeakingTopic> all = topics.findAll();
        int migrated = 0;
        for (SpeakingTopic t : all) {
            List<String> p1 = t.getPart1Questions();
            boolean changed = false;
            if (t.getPart3Questions().isEmpty() && p1.size() >= 3) {
                int keep = Math.max(2, (p1.size() + 1) / 2);
                List<String> part3 = p1.subList(keep, p1.size());
                if (!part3.isEmpty()) {
                    t.setPart3Questions(part3);
                    t.setPart1Questions(p1.subList(0, keep));
                    changed = true;
                }
            }
            if (changed) {
                topics.save(t);
                migrated++;
            }
        }
        if (migrated > 0) {
            log.info("SpeakingPartsMigration: restructured {} topic(s) into parts", migrated);
        }
    }

    /** Добавить тему Family and Relationships каждому учителю, у которого её нет. */
    private void seedFamilyTopic() {
        for (User teacher : users.findAll()) {
            if (teacher.getRole() != User.Role.TEACHER) continue;
            boolean exists = topics.findByOwnerIdOrderByCreatedAtDesc(teacher.getId()).stream()
                    .anyMatch(t -> t.getTitle().toLowerCase().startsWith("family"));
            if (exists) continue;
            topics.save(DataSeeder.familyTopic(teacher));
            log.info("SpeakingPartsMigration: added topic 'Family and Relationships' for teacher {}",
                    teacher.getEmail());
        }
    }
}
