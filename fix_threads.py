file_path = 'app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt'
with open(file_path, 'r') as f:
    code = f.read()

import_statement = "import org.pytorch.PyTorchAndroid\n"
if "org.pytorch.PyTorchAndroid" not in code:
    code = code.replace("import org.pytorch.IValue\n", "import org.pytorch.IValue\n" + import_statement)

code = code.replace("        viewModelScope.launch(Dispatchers.Default) {\n            _uiState.value = _uiState.value.copy(isProcessing = true, analysisReport = null, annotatedBitmap = null, histogramBitmap = null, cpuStats = null)\n            try {\n                // Feature: Actual Image Classification", "        viewModelScope.launch(Dispatchers.Default) {\n            _uiState.value = _uiState.value.copy(isProcessing = true, analysisReport = null, annotatedBitmap = null, histogramBitmap = null, cpuStats = null)\n            try {\n                org.pytorch.PyTorchAndroid.setNumThreads(1)\n                // Feature: Actual Image Classification")

with open(file_path, 'w') as f:
    f.write(code)
