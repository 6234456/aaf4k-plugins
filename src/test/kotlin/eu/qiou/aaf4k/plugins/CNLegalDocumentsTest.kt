package eu.qiou.aaf4k.plugins

import eu.qiou.aaf4k.util.time.TimeSpan
import eu.qiou.aaf4k.util.time.times
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class CNLegalDocumentsTest {

    @Test
    fun co1() = runBlocking {
        var timeSpan = TimeSpan(LocalDate.of(2019, 6, 30), LocalDate.of(2019, 6, 30))

        while (timeSpan.start > LocalDate.of(2015, 1, 1)) {
            listOf("江苏省", "北京市", "湖北省", "浙江省", "广东省").forEach { location ->
                listOf("基层", "中级", "高级").forEach { level ->
                    withContext(Dispatchers.Default) {
                        CNLegalDocuments().search(
                            courtLevel = level,
                            date = timeSpan,
                            location = if (level == "最高") null else location,
                            category = "民事",
                            subCategory = SubCategory.Contract
                        )
                    }
                }
            }

            timeSpan -= ChronoUnit.DAYS * 1
        }
    }
}