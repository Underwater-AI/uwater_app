import os
filepath = 'app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt'

with open(filepath, 'r') as f:
    text = f.read()

# Make sure we import Executors
if 'java.util.concurrent.Executors' not in text:
    text = text.replace('import kotlinx.coroutines.launch', 'import kotlinx.coroutines.launch\nimport java.util.concurrent.Executors\nimport kotlinx.coroutines.asCoroutineDispatcher\n')

# Create a single thread dispatcher
if 'private val aiDispatcher' not in text:
    text = text.replace('class EnhanceViewModel', 'private val aiDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()\n\nclass EnhanceViewModel')

# Update marine analysis to use the single thread dispatcher
text = text.replace('viewModelScope.launch(Dispatchers.Default)', 'viewModelScope.launch(aiDispatcher)')
text = text.replace('viewModelScope.launch(Dispatchers.IO)', 'viewModelScope.launch(aiDispatcher)')

with open(filepath, 'w') as f:
    f.write(text)
