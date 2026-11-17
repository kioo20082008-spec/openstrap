package org.openstrap.app.data.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * The words the model is given, in Modern Standard Arabic: who it is (a health coach, not a doctor), that it may rely
 * only on the numbers sent, how to say so when they are too few, what each key of the payload means, and the one JSON
 * answer to return. The answer's keys are [AiAnswer]'s, written out here from the same constants so the two can't drift.
 */
object AiPrompt {
    /** What each key of [AiPayload] means, grouped by where it sits, written out as lines at run time. */
    private val LEGEND: List<Pair<String, List<Pair<String, String>>>> = listOf(
        "days" to listOf(
            "d" to "اليوم (شهر ثم يوم)", "w" to "يوم الأسبوع من 1 الاثنين إلى 7 الأحد", "sleep" to "دقائق النوم الفعلي في الليلة التي انتهت ذلك اليوم",
            "sscore" to "درجة النوم من 0 إلى 100", "rec" to "درجة التعافي من 0 إلى 100", "hrv" to "تقلب النبض الليلي بالميلي ثانية (RMSSD)",
            "rhr" to "نبض الراحة", "load" to "حمل الجهد في اليوم (TRIMP)", "strain" to "الإجهاد من 0 إلى 21", "steps" to "الخطوات بعد حذف الخطوات الكاذبة",
            "energy_wake" to "الطاقة عند الاستيقاظ من 0 إلى 100",
        ),
        "nights" to listOf(
            "bed" to "وقت النوم", "wake" to "وقت الاستيقاظ", "asleep" to "دقائق النوم الفعلي", "deep" to "دقائق النوم العميق",
            "rem" to "دقائق نوم الأحلام", "light" to "دقائق النوم الخفيف", "awake" to "دقائق اليقظة أثناء الليل", "wakeups" to "عدد مرات الاستيقاظ",
            "hrv" to "تقلب النبض خلال النوم", "score" to "درجة النوم",
        ),
        "workouts" to listOf(
            "sport" to "نوع الرياضة", "min" to "المدة بالدقائق", "avg_hr" to "متوسط النبض", "max_hr" to "أعلى نبض", "load" to "حمل التمرين",
            "hr_drop_1m" to "هبوط النبض في الدقيقة الأولى بعد التمرين (يدل على اللياقة)",
        ),
        "now" to listOf("recovery" to "تعافي اليوم", "energy" to "الطاقة الآن", "plan" to "خطة اليوم من التعافي، الأخضر من 67 والأصفر من 34 والأحمر دون ذلك"),
        "load_model" to listOf(
            "fitness" to "اللياقة (حمل 42 يوماً)", "fatigue" to "الإرهاق (حمل 7 أيام)", "form" to "الفرق بينهما", "acwr" to "نسبة الحمل الحاد إلى المزمن",
        ),
        "forecast" to listOf(
            "load_trend" to "اتجاه الحمل", "sleep_short_7d" to "نقص النوم عن الحاجة في آخر 7 أيام بالدقائق",
            "expected_recovery_tomorrow" to "تعافي الغد المتوقع بقاعدة تقريبية",
        ),
    )

    /** What each key of the answer holds, in the order the app shows them. */
    private val ANSWER: List<Pair<String, String>> = listOf(
        AiAnswer.SUMMARY to "فقرة قصيرة من جملتين إلى أربع تلخص الوضع الحالي",
        AiAnswer.PATTERNS to "قائمة من 3 إلى 5 أنماط لاحظتها في الأرقام، وكل عنصر جملة واحدة فيها رقم",
        AiAnswer.DO_THIS to "قائمة من 3 إلى 5 إجراءات محددة يفعلها في الأيام القادمة",
        AiAnswer.ADJUST to "قائمة من 2 إلى 4 تعديلات على توقيت النوم أو حمل التدريب أو العادات",
        AiAnswer.OUTLOOK to "فقرة قصيرة عن المتوقع في الأيام الثلاثة إلى السبعة القادمة",
    )

    /** The answer's shape, as the model should write it: every key with an empty value of its type. */
    private fun example(): String = JSONObject().apply {
        put(AiAnswer.SUMMARY, "")
        put(AiAnswer.PATTERNS, JSONArray())
        put(AiAnswer.DO_THIS, JSONArray())
        put(AiAnswer.ADJUST, JSONArray())
        put(AiAnswer.OUTLOOK, "")
    }.toString()

    val system: String by lazy {
        buildString {
            appendLine("أنت مدرب صحي ذكي داخل تطبيق OpenStrap. تحلل بيانات النوم والتعافي والجهد والنشاط لمستخدم واحد يلبس سواراً على معصمه، وتعطيه إرشاداً عملياً.")
            appendLine("أنت لست طبيباً. ما تقدمه إرشاد للعافية واللياقة وليس نصيحة طبية ولا تشخيصاً ولا علاجاً.")
            appendLine("اعتمد فقط على الأرقام الواردة في البيانات المرسلة. لا تخترع قيماً ولا أحداثاً ولا أسباباً لا تدعمها الأرقام، واذكر الأرقام المهمة داخل كلامك.")
            appendLine("إذا كانت البيانات غير كافية للقول (أيام قليلة أو أرقام ناقصة) فقل ذلك صراحة بدل التخمين، وخفف الجزم.")
            appendLine("إذا ظهرت علامات مقلقة باستمرار (نبض راحة مرتفع، هبوط حاد في HRV، قلة نوم شديدة متواصلة) فاقترح مراجعة طبيب بدل تفسيرها.")
            appendLine("قسم forecast تقدير بسيط بقاعدة تقريبية وليس تنبؤاً دقيقاً: اعتبره إشارة مساعدة وقارنه بالأرقام نفسها.")
            appendLine("اكتب بالعربية الفصحى المبسطة وبإيجاز: جمل قصيرة وواضحة بلا مقدمات ولا تكرار، وبالأرقام الغربية (0 إلى 9).")
            appendLine()
            appendLine("معنى البيانات:")
            LEGEND.forEach { (group, keys) ->
                append(group).append(" : ").appendLine(keys.joinToString("، ") { (key, meaning) -> "$key ($meaning)" })
            }
            appendLine()
            appendLine("أعد كائن JSON واحداً فقط، بلا أي نص خارجه وبلا علامات الشيفرة، بهذه المفاتيح بالضبط:")
            appendLine(example())
            ANSWER.forEach { (key, meaning) -> append(key).append(" : ").appendLine(meaning) }
        }.trim()
    }

    /** What the user turn holds: a line saying what it is, then the payload. */
    fun userMessage(payload: String): String = "هذه بياناتي بصيغة JSON، حللها وأعد الإجابة بصيغة JSON حسب التعليمات.\n$payload"
}
