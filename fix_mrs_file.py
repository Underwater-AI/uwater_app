import re

with open('app/src/main/java/com/underwaterai/enhance/model/MarineResearchServices.kt', 'r') as f:
    text = f.read()

text = text.replace(
    'fun mapBenthicCoverage(areaSquareMeters: Float): Map<String, Float> {',
    'fun mapBenthicCoverage(areaSquareMeters: Float, r: Int, g: Int, b: Int): Map<String, Float> {'
)
text = text.replace(
    'return mapOf("Coral" to 42.5f, "Sand" to 35.0f, "Rock" to 15.5f, "Algae" to 7.0f)',
    '''
        val total = (r + g + b).toFloat().coerceAtLeast(1f)
        val coralEst = (r / total) * 100f
        val algaeEst = (g / total) * 100f
        val sandEst = (b / total) * 70f
        val remaining = Math.max(0f, 100f - coralEst - algaeEst - sandEst)
        return mapOf(
            "Coral (Est)" to kotlin.math.round(coralEst * 10f) / 10f, 
            "Algae/Plant" to kotlin.math.round(algaeEst * 10f) / 10f, 
            "Sand/Water" to kotlin.math.round(sandEst * 10f) / 10f, 
            "Other/Rock" to kotlin.math.round(remaining * 10f) / 10f
        )
'''
)

with open('app/src/main/java/com/underwaterai/enhance/model/MarineResearchServices.kt', 'w') as f:
    f.write(text)

with open('app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt', 'r') as f:
    text_vm = f.read()

text_vm = text_vm.replace(
    'val coverage = MarineResearchServices.mapBenthicCoverage(100f)',
    'val coverage = MarineResearchServices.mapBenthicCoverage(100f, r, g, b)'
)
with open('app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt', 'w') as f:
    f.write(text_vm)

