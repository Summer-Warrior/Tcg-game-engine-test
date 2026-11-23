package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ImportedMpvCard
import com.example.engine.CatalogBlueprint
import com.example.engine.EngineInvariantsVerifier
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldReady
import org.json.JSONArray
import org.json.JSONObject

/**
 * Discreet Diagnostics & Invariants Verification Modal accessible from Settings.
 * Runs all 6 deterministic checks in `EngineInvariantsVerifier`.
 */
@Composable
fun DiagnosticsInvariantsModal(
    catalogBlueprints: List<CatalogBlueprint>,
    loadedMatchCards: List<ImportedMpvCard>,
    onDismiss: () -> Unit
) {
    val report = remember(catalogBlueprints, loadedMatchCards) {
        EngineInvariantsVerifier.runAllChecks(catalogBlueprints, loadedMatchCards)
    }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Verified,
                contentDescription = "Engine Diagnostics",
                tint = if (report.allPassed) EmeraldReady else CrimsonAlert,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Engine Invariants (${report.passedCount}/${report.totalCount} Passed)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                report.items.forEach { item ->
                    val tint = if (item.passed) EmeraldReady else CrimsonAlert
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(tint.copy(alpha = 0.08f))
                            .border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = if (item.passed) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = if (item.passed) "Passed" else "Failed",
                            tint = tint,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = item.detail,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val json = JSONObject().apply {
                        put("passedCount", report.passedCount)
                        put("totalCount", report.totalCount)
                        put("allPassed", report.allPassed)
                        put(
                            "checks",
                            JSONArray().apply {
                                report.items.forEach { it ->
                                    put(
                                        JSONObject().apply {
                                            put("id", it.id)
                                            put("title", it.title)
                                            put("passed", it.passed)
                                            put("detail", it.detail)
                                        }
                                    )
                                }
                            }
                        )
                    }.toString(2)
                    clipboard.setText(AnnotatedString(json))
                    copied = true
                },
                modifier = Modifier.testTag("copy_diagnostics_json_button")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (copied) "Copied JSON!" else "Copy Diagnostics JSON")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
