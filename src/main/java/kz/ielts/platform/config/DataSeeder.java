package kz.ielts.platform.config;

import kz.ielts.platform.entity.CriteriaSet;
import kz.ielts.platform.entity.SpeakingTopic;
import kz.ielts.platform.entity.User;
import kz.ielts.platform.entity.WritingTask;
import kz.ielts.platform.repository.CriteriaSetRepository;
import kz.ielts.platform.repository.SpeakingTopicRepository;
import kz.ielts.platform.repository.UserRepository;
import kz.ielts.platform.repository.WritingTaskRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

/**
 * Демо-данные при первом запуске (только если БД пуста):
 * учитель teacher@ielts.local / teacher123, ученик student@ielts.local / student123.
 */
@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seed(UserRepository users, PasswordEncoder encoder,
                           WritingTaskRepository tasks, CriteriaSetRepository criteria,
                           SpeakingTopicRepository topics,
                           @Value("${app.bootstrap.teacher-email:teacher@ielts.local}") String teacherEmail,
                           @Value("${app.bootstrap.teacher-password:teacher123}") String teacherPassword,
                           @Value("${app.bootstrap.demo-student:true}") boolean demoStudent) {
        return args -> {
            if (users.count() > 0) return;

            User teacher = users.save(new User(teacherEmail,
                    encoder.encode(teacherPassword), "Учитель IELTS", User.Role.TEACHER));
            if (demoStudent) {
                User student = users.save(new User("student@ielts.local",
                        encoder.encode("student123"), "Ученик Демо", User.Role.STUDENT));
                student.setTeacher(teacher);
                student.setTargetBand(7.0);
                users.save(student);
            }

            tasks.save(new WritingTask(teacher, WritingTask.TaskType.TASK_2,
                    WritingTask.ModuleType.ACADEMIC,
                    "Some people believe that university education should be free for everyone. "
                            + "To what extent do you agree or disagree with this statement? "
                            + "Give reasons for your answer and include relevant examples from your "
                            + "own knowledge or experience."));

            tasks.save(new WritingTask(teacher, WritingTask.TaskType.TASK_1,
                    WritingTask.ModuleType.ACADEMIC,
                    "The table below shows the number of international students enrolled "
                            + "at three universities between 2015 and 2020. Summarise the information "
                            + "by selecting and reporting the main features, and make comparisons "
                            + "where relevant."));
            // Task 1 (таблица) — помечаем тип промпта; ищем по типу, а не по позиции
            tasks.findByOwnerIdOrderByCreatedAtDesc(teacher.getId()).stream()
                    .filter(t -> t.getTaskType() == WritingTask.TaskType.TASK_1)
                    .findFirst()
                    .ifPresent(t1 -> {
                        t1.setPromptKind("table");
                        tasks.save(t1);
                    });

            criteria.save(new CriteriaSet(teacher, "IELTS Writing — официальный дескриптор (v1)",
                    """
                    Оценивай эссе строго по официальным критериям IELTS Writing Task 2:

                    1. Task Response — полнота ответа, ясная позиция, развитие идей и аргументов.
                    2. Coherence and Cohesion — логика, структура абзацев, связность, связующие средства.
                    3. Lexical Resource — точность, разнообразие и уместность лексики и коллокаций.
                    4. Grammatical Range and Accuracy — разнообразие грамматических структур, точность, характер ошибок.

                    Правила:
                    - Каждый критерий оценивается от 1 до 9 с шагом 0.5.
                    - Цитируй конкретные места из эссе ученика для каждой ошибки.
                    - Не завышай баллы: средний ученик получает 5.5–6.5.
                    - Рекомендации пиши на русском, термины и разбор — на английском.
                    """));

            SpeakingTopic education = new SpeakingTopic(teacher, "Education");
            education.setDescription("Вопросы о школе, университете и обучении");
            education.setQuestions(List.of(
                    "Did you enjoy going to school when you were a child?",
                    "What was your favourite subject at school? Why?",
                    "Do you think it is better to study online or in a classroom?",
                    "How important is it for everyone to learn a foreign language?"));
            topics.save(education);

            SpeakingTopic technology = new SpeakingTopic(teacher, "Technology");
            technology.setDescription("Вопросы о гаджетах, интернете и ИИ");
            technology.setQuestions(List.of(
                    "How often do you use your smartphone during the day?",
                    "Has technology changed the way people work in your country?",
                    "Do you think artificial intelligence will replace teachers one day?",
                    "What piece of technology could you not live without? Why?"));
            topics.save(technology);

            SpeakingTopic work = new SpeakingTopic(teacher, "Work");
            work.setDescription("Вопросы о работе и карьере");
            work.setQuestions(List.of(
                    "What do you do — do you work or are you a student?",
                    "Do you have to work with other people as part of your job or studies?",
                    "Is it more important to have a well-paid job or an interesting one?",
                    "Would you like to work abroad in the future? Why or why not?"));
            topics.save(work);
        };
    }
}
