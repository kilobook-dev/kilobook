package com.kilobook.app.speech

import com.kilobook.app.data.Purpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoicePromptsTest {
    @Test
    fun spokenKm() {
        assertEquals("십이 점 삼 킬로미터", VoicePrompts.spokenKm(12.3))
        assertEquals("일 킬로미터", VoicePrompts.spokenKm(1.0))
        assertEquals("영 점 육 킬로미터", VoicePrompts.spokenKm(0.6))
        assertEquals("일 점 육 킬로미터", VoicePrompts.spokenKm(1.6))
        assertEquals("십 킬로미터", VoicePrompts.spokenKm(9.96))
        assertEquals("백오 점 사 킬로미터", VoicePrompts.spokenKm(105.4))
        assertEquals("천이백삼십사 킬로미터", VoicePrompts.spokenKm(1234.0))
        assertEquals("이만 킬로미터", VoicePrompts.spokenKm(20000.0))
        assertEquals("만오십 킬로미터", VoicePrompts.spokenKm(10050.0))
        assertEquals("영 킬로미터", VoicePrompts.spokenKm(-1.0))
    }

    @Test
    fun sentences() {
        assertEquals("이번 주행 십이 점 삼 킬로미터, 업무였나요?", VoicePrompts.tripQuestion(12.3))
        assertEquals("용도 확인이 필요한 주행이 3건 있어요", VoicePrompts.waitingNotice(3))
    }

    @Test
    fun onePurpose() {
        assertEquals(Purpose.BUSINESS, VoicePrompts.purposeFromSpeech("업무"))
        assertEquals(Purpose.BUSINESS, VoicePrompts.purposeFromSpeech("업무요."))
        assertEquals(Purpose.BUSINESS, VoicePrompts.purposeFromSpeech("업무였어요"))
        assertEquals(Purpose.COMMUTE, VoicePrompts.purposeFromSpeech("출퇴근"))
        assertEquals(Purpose.COMMUTE, VoicePrompts.purposeFromSpeech("퇴근했어"))
        assertEquals(Purpose.PERSONAL, VoicePrompts.purposeFromSpeech("개인"))
        assertEquals(Purpose.PERSONAL, VoicePrompts.purposeFromSpeech("사적인 용도"))
    }

    @Test
    fun yesMeansBusiness() {
        assertEquals(Purpose.BUSINESS, VoicePrompts.purposeFromSpeech("네"))
        assertEquals(Purpose.BUSINESS, VoicePrompts.purposeFromSpeech("예."))
        assertEquals(Purpose.BUSINESS, VoicePrompts.purposeFromSpeech("맞아요"))
        assertNull(VoicePrompts.purposeFromSpeech("네 그런데"))
    }

    @Test
    fun noThenAnotherPurpose() {
        assertEquals(Purpose.PERSONAL, VoicePrompts.purposeFromSpeech("아니요, 개인이요"))
        assertEquals(Purpose.COMMUTE, VoicePrompts.purposeFromSpeech("아니 출퇴근"))
        assertEquals(Purpose.PERSONAL, VoicePrompts.purposeFromSpeech("아뇨 개인"))
        assertNull(VoicePrompts.purposeFromSpeech("아니요"))
        assertNull(VoicePrompts.purposeFromSpeech("아니요 업무요"))
    }

    @Test
    fun unclear() {
        assertNull(VoicePrompts.purposeFromSpeech(""))
        assertNull(VoicePrompts.purposeFromSpeech("음"))
        assertNull(VoicePrompts.purposeFromSpeech("업무 아니고 개인"))
        assertNull(VoicePrompts.purposeFromSpeech("출근하고 개인 볼일"))
    }
}
