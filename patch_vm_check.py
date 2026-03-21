import os
filepath = 'app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt'

with open(filepath, 'r') as f:
    text = f.read()

# Add processing check for enhanceImage
text = text.replace('fun enhanceImage() {', 'fun enhanceImage() {\n        if (_uiState.value.isProcessing) return')

# Add processing check for runMarineAnalysis
text = text.replace('fun runMarineAnalysis() {', 'fun runMarineAnalysis() {\n        if (_uiState.value.isProcessing) return')

with open(filepath, 'w') as f:
    f.write(text)
