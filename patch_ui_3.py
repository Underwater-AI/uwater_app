import re

with open('app/src/main/java/com/underwaterai/enhance/ui/screens/HomeScreen.kt', 'r') as f:
    text = f.read()

REPORT_CARD_NEW = """
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
                        
                        uiState.annotatedBitmap?.let { bmp ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Detections Highlighted:", style = MaterialTheme.typography.labelMedium)
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Annotated Image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                        
                        uiState.histogramBitmap?.let { hist ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("RGB Color Distribution (Chlorophyll/Depth proxy):", style = MaterialTheme.typography.labelMedium)
                            Image(
                                bitmap = hist.asImageBitmap(),
                                contentDescription = "RGB Histogram",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.FillBounds
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = report,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        uiState.cpuStats?.let { stats ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Hardware Execution Trace:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stats,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Error Message
"""

text = text.replace('            // Analysis Report', '            // OLD_Analysis_Report')
text = re.sub(r'            // OLD_Analysis_Report[\s\S]*?// Error Message', REPORT_CARD_NEW, text)

with open('app/src/main/java/com/underwaterai/enhance/ui/screens/HomeScreen.kt', 'w') as f:
    f.write(text)
