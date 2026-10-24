package kz.ielts.platform.entity;

/**
 * Официальная математика IELTS.
 * Отдельный класс без состояния, чтобы использовать из JPA-хуков и сервисов.
 */
public final class IeltsBandMath {

    private IeltsBandMath() {}

    /**
     * Среднее четырёх навыков с официальным округлением IELTS:
     * дробная часть .25 округляется вверх до .5; .75 — вверх до целого;
     * .00 и .50 остаются. Если хоть один навык null — Overall не считается.
     */
    public static Double overall(Double listening, Double reading,
                                 Double writing, Double speaking) {
        if (listening == null || reading == null || writing == null || speaking == null) {
            return null;
        }
        double avg = (listening + reading + writing + speaking) / 4.0;
        return round(avg);
    }

    /** Округление одного значения по правилам IELTS. */
    public static double round(double band) {
        double whole = Math.floor(band);
        double frac = band - whole;
        if (frac < 0.25) return whole;
        if (frac < 0.75) return whole + 0.5;
        return whole + 1.0;
    }

    /** Валидация отдельного навыка: 1.0–9.0 с шагом 0.5. */
    public static boolean isValidSkillScore(Double v) {
        if (v == null) return true; // null = навык не сдавался, это допустимо
        return v >= 1.0 && v <= 9.0 && (v * 2) == Math.floor(v * 2);
    }
}
