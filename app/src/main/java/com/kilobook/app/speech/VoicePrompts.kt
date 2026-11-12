package com.kilobook.app.speech

import com.kilobook.app.data.Purpose
import kotlin.math.roundToLong

/** What the car says, and how a spoken answer maps to a purpose. Pure functions (no Android). */
object VoicePrompts {
    /** Asked when the trip that just ended is shown: "이번 주행 십이 점 삼 킬로미터, 업무였나요?" */
    fun tripQuestion(km: Double): String = "이번 주행 ${spokenKm(km)}, 업무였나요?"

    /** Said when the card shows trips that have been waiting: "용도 확인이 필요한 주행이 3건 있어요" */
    fun waitingNotice(count: Int): String = "용도 확인이 필요한 주행이 ${count}건 있어요"

    /**
     * Distance read the Korean way, rounded to one decimal place:
     * 12.3 → "십이 점 삼 킬로미터", 1.0 → "일 킬로미터", 0.6 → "영 점 육 킬로미터".
     * Written in Hangul so the engine never reads the number as something else.
     */
    fun spokenKm(km: Double): String {
        val tenths = (km.coerceAtLeast(0.0) * 10).roundToLong()
        val whole = tenths / 10
        val decimal = (tenths % 10).toInt()
        val number = if (decimal == 0) sinoKorean(whole) else "${sinoKorean(whole)} 점 ${DIGITS[decimal]}"
        return "$number 킬로미터"
    }

    /** Sino-Korean numerals up to 99,999,999: 0 → "영", 105 → "백오", 1,234 → "천이백삼십사", 20,000 → "이만". */
    fun sinoKorean(value: Long): String {
        require(value in 0..99_999_999) { "out of range: $value" }
        if (value == 0L) return DIGITS[0]
        val man = value / 10_000
        val rest = value % 10_000
        return buildString {
            if (man > 0) append(if (man == 1L) "" else underTenThousand(man.toInt())).append("만")
            if (rest > 0) append(underTenThousand(rest.toInt()))
        }
    }

    private fun underTenThousand(value: Int): String = buildString {
        val units = listOf(1000 to "천", 100 to "백", 10 to "십")
        var rest = value
        for ((size, name) in units) {
            val digit = rest / size
            if (digit > 0) append(if (digit == 1) "" else DIGITS[digit]).append(name)
            rest %= size
        }
        if (rest > 0) append(DIGITS[rest])
    }

    private val DIGITS = listOf("영", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구")

    /**
     * Maps a recognized answer to a purpose. Exactly one purpose must be heard; anything else is null
     * (the card stays for a tap). The question is "업무였나요?", so:
     * - "네/예/응/맞아" alone mean 업무.
     * - "아니요, 개인이요" means 개인; after a "no" only 출퇴근 or 개인 count ("아니요 업무요" is unclear → null).
     */
    fun purposeFromSpeech(text: String): Purpose? {
        val t = text.replace(Regex("[\\s.,!?]"), "")
        if (t.isEmpty()) return null
        val no = NEGATIVE.find(t)
        val rest = if (no != null) t.substring(no.range.last + 1) else t
        val heard = buildSet {
            if (COMMUTE.any { it in rest }) add(Purpose.COMMUTE)
            if (BUSINESS.any { it in rest }) add(Purpose.BUSINESS)
            if (PERSONAL.any { it in rest }) add(Purpose.PERSONAL)
            if (no == null && isEmpty() && YES.any { t == it || t == it + "요" }) add(Purpose.BUSINESS)
        }
        val purpose = heard.singleOrNull() ?: return null
        return if (no != null && purpose == Purpose.BUSINESS) null else purpose
    }

    private val COMMUTE = listOf("출퇴근", "출근", "퇴근")
    private val BUSINESS = listOf("업무")
    private val PERSONAL = listOf("개인", "사적")
    private val YES = listOf("네", "예", "응", "맞아", "맞습니다", "그래")
    /** A leading "no": 아니요 / 아니오 / 아니야 / 아니 / 아뇨. */
    private val NEGATIVE = Regex("^(아니요|아니오|아니야|아니|아뇨)")
}
