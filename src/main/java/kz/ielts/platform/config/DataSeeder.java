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
                    encoder.encode(teacherPassword), "IELTS Teacher", User.Role.TEACHER));
            if (demoStudent) {
                User student = users.save(new User("student@ielts.local",
                        encoder.encode("student123"), "Demo Student", User.Role.STUDENT));
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

            criteria.save(new CriteriaSet(teacher, "IELTS Writing — Official Descriptor (v1)",
                    """
                    Assess the essay strictly against the official IELTS Writing Task 2 criteria:

                    1. Task Response — how fully the question is answered, clear position, development of ideas and arguments.
                    2. Coherence and Cohesion — logic, paragraph structure, linking, use of cohesive devices.
                    3. Lexical Resource — accuracy, range and appropriateness of vocabulary and collocations.
                    4. Grammatical Range and Accuracy — variety of structures, accuracy, nature of errors.

                    Rules:
                    - Score each criterion from 1 to 9 in 0.5 steps.
                    - Quote specific places from the student's essay for every error.
                    - Do not inflate scores: an average student gets 5.5–6.5.
                    - Write the full report in English.
                    """));

            SpeakingTopic education = new SpeakingTopic(teacher, "Education");
            education.setDescription("School, university and lifelong learning");
            education.setPart1Questions(List.of(
                    "Did you enjoy going to school when you were a child?",
                    "What was your favourite subject at school? Why?",
                    "Do you think it is better to study online or in a classroom?",
                    "How important is it for everyone to learn a foreign language?"));
            education.setCueCard("""
                    Describe a teacher who has influenced you.
                    You should say:
                    - who this person is
                    - what they taught you
                    - how they helped you
                    and explain why this teacher influenced you so much.""");
            education.setPart3Questions(List.of(
                    "How has education in your country changed over the last 20 years?",
                    "Should exams be the main way of assessing students?",
                    "What makes a good teacher?",
                    "Is university education necessary for success in life?"));
            topics.save(education);

            SpeakingTopic technology = new SpeakingTopic(teacher, "Technology");
            technology.setDescription("Gadgets, the internet and AI");
            technology.setPart1Questions(List.of(
                    "How often do you use your smartphone during the day?",
                    "Has technology changed the way people work in your country?",
                    "Do you think artificial intelligence will replace teachers one day?",
                    "What piece of technology could you not live without? Why?"));
            technology.setCueCard("""
                    Describe a piece of technology you find difficult to use.
                    You should say:
                    - what it is
                    - when you got it
                    - what you use it for
                    and explain why you find it difficult to use.""");
            technology.setPart3Questions(List.of(
                    "Do you think people rely too much on technology nowadays?",
                    "Should children be allowed to use smartphones at school?",
                    "How might technology change our lives in the next 20 years?",
                    "Is it better to work from home using technology or in an office?"));
            topics.save(technology);

            SpeakingTopic work = new SpeakingTopic(teacher, "Work");
            work.setDescription("Jobs and careers");
            work.setPart1Questions(List.of(
                    "What do you do — do you work or are you a student?",
                    "Do you have to work with other people as part of your job or studies?",
                    "Is it more important to have a well-paid job or an interesting one?",
                    "Would you like to work abroad in the future? Why or why not?"));
            work.setCueCard("""
                    Describe a job you would like to have in the future.
                    You should say:
                    - what the job is
                    - what skills you would need
                    - how you could get this job
                    and explain why you would like to have this job.""");
            work.setPart3Questions(List.of(
                    "What are the most popular jobs among young people in your country?",
                    "Should people change careers several times in their life?",
                    "Is it better to work for a big company or a small one?",
                    "How important is work-life balance?"));
            topics.save(work);

            topics.save(familyTopic(teacher));
        };
    }

    /** Тема «Family and Relationships» с вопросами заказчицы. */
    public static SpeakingTopic familyTopic(User teacher) {
        SpeakingTopic family = new SpeakingTopic(teacher, "Family and Relationships");
        family.setDescription("Family, relatives and family life");
        family.setPart1Questions(List.of(
                "Do you have a large or a small family?",
                "How much time do you spend with your family?",
                "What do you like doing together as a family?",
                "Who are you closest to in your family?",
                "Are you similar to any other member of your family?",
                "Do you think it is important to keep in touch with distant relatives?"));
        family.setCueCard("""
                Describe a family member you admire.
                You should say:
                - who this person is
                - what they do
                - what you usually do together
                and explain why you admire them.""");
        family.setPart3Questions(List.of(
                "In what ways have families in your country changed over the last fifty years?",
                "Should parents be strict with their children or treat them as friends?",
                "Who should take care of elderly people — the family or the government?",
                "Is it better for children to grow up in a large family or a small one?"));
        return family;
    }
}
