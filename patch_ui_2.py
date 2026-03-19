import re

with open('app/src/main/java/com/underwaterai/enhance/ui/screens/HomeScreen.kt', 'r') as f:
    text = f.read()

BUTTONS = """
                    // Enhance Button
                    EnhanceButton(
                        isProcessing = uiState.isProcessing,
                        modelName = uiState.selectedModel.displayName,
                        selectedScale = uiState.selectedScale,
                        onClick = { viewModel.enhanceImage() }
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Marine Analysis Button
                    OutlinedButton(
                        onClick = { viewModel.runMarineAnalysis() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = !uiState.isProcessing,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AccentCyan
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (uiState.isProcessing && uiState.analysisReport == null && uiState.enhancedBitmap == null) {
                            CircularProgressIndicator(
                                color = AccentCyan,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Analyzing...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Filled.Science, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Run Marine Research Analysis", fontWeight = FontWeight.Bold)
                        }
                    }
"""

text = text.replace("""
                    // Enhance Button
                    EnhanceButton(
                        isProcessing = uiState.isProcessing,
                        modelName = uiState.selectedModel.displayName,
                        selectedScale = uiState.selectedScale,
                        onClick = { viewModel.enhanceImage() }
                    )""", BUTTONS)

REPORT_CARD = """
            // Analysis Report
            uiState.analysisReport?.let { report ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Analysis Results",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = { viewModel.clearAnalysis() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close Report")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = report,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Error Message
"""

text = text.replace('            // Error Message', REPORT_CARD)

with open('app/src/main/java/com/underwaterai/enhance/ui/screens/HomeScreen.kt', 'w') as f:
    f.write(text)

