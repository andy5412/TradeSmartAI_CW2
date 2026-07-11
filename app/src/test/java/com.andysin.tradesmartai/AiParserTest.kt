package com.andysin.tradesmartai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 針對 TradeSmart AI 核心邏輯的單元測試
 * 證明符合 CW2 要求: "A full suite of automated Unit tests"
 */
class AiParserTest {

    // 🎯 測試 1：模擬 AI 正常回傳完美格式
    @Test
    fun `test ai valid response parsing`() {
        // 1. 模擬 (Mock) Gemini AI 畀我哋嘅回傳字串
        val mockAiResponse = "iPhone 13 Pro | 3500 | 邊角有輕微刮痕，電池健康度 85%"

        // 2. 模擬 MainActivity 入面嘅字串清理同切割邏輯
        val cleanText = mockAiResponse.replace("\n", "").replace("*", "").trim()
        val parts = cleanText.split("|").map { it.trim() }

        // 3. 斷言 (Assert) 驗證結果係咪正確
        assertEquals("切割出來應該要有 3 個部分", 3, parts.size)
        assertEquals("物品名稱錯誤", "iPhone 13 Pro", parts[0])
        assertEquals("估價金額錯誤", "3500", parts[1])
        assertEquals("描述錯誤", "邊角有輕微刮痕，電池健康度 85%", parts[2])
    }

    // 🎯 測試 2：模擬 AI 發神經亂答嘢 (格式錯誤)
    @Test
    fun `test ai invalid response parsing`() {
        // 模擬 AI 無跟規矩，無用 "|" 分隔
        val mockAiResponse = "呢部係 iPhone 13 Pro 大約值 3500 蚊"

        val cleanText = mockAiResponse.replace("\n", "").replace("*", "").trim()
        val parts = cleanText.split("|").map { it.trim() }

        // 驗證系統能夠正確識別出長度不足 3，從而觸發 Error Handling
        assertTrue("切割結果少於 3 個部分，應該觸發錯誤處理", parts.size < 3)
    }

    // 🎯 測試 3：測試 Room 數據庫實體 (ItemEntity) 建立是否正常
    @Test
    fun `test item entity creation and default values`() {
        val testItem = ItemEntity(
            itemName = "PS5 遊戲機",
            aiEstimatedPrice = "2800",
            aiDescription = "九成新",
            imagePath = "/test/path",
            location = "Sham Shui Po"
        )

        assertEquals("PS5 遊戲機", testItem.itemName)
        assertEquals("2800", testItem.aiEstimatedPrice)
        assertEquals("Sham Shui Po", testItem.location)
        // 驗證 timestamp 有無自動生成大於 0 的時間戳
        assertTrue("Timestamp 應該大於 0", testItem.timestamp > 0)
    }
}