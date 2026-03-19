import re

with open('app/src/test/java/com/underwaterai/enhance/utils/HardwareProfilerTest.kt', 'r') as f:
    text = f.read()

# remove one of the duplicates
text = text.replace('''    @Test
    fun `countPerformanceCores - counts only performance cores properly`() {
        val cores = listOf(
            HardwareProfiler.CpuCoreInfo(0, 1800, 2000),
            HardwareProfiler.CpuCoreInfo(1, 1700, 2000),
            HardwareProfiler.CpuCoreInfo(2, 1000, 1200)
        )
        // 2000 * 0.8 = 1600. Cores 0 and 1 have maxFreq >= 1600. Core 2 does not.
        assertEquals(2, HardwareProfiler.countPerformanceCores(cores))
    }''', '', 1)

with open('app/src/test/java/com/underwaterai/enhance/utils/HardwareProfilerTest.kt', 'w') as f:
    f.write(text)
