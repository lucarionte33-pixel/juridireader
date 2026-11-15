package com.juridireader.app.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juridireader.app.ui.viewmodel.LawViewModel
import com.juridireader.app.util.ImportMode
import com.juridireader.app.util.UserBackupPayload
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupTransferDialog(
    viewModel: LawViewModel,
    initialTab: Int = 0,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var selectedTab by remember { mutableIntStateOf(initialTab) }

    // Estado de Exportação
    var generatedCode by remember { mutableStateOf<String?>(null) }
    var exportedPayload by remember { mutableStateOf<UserBackupPayload?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    // Estado de Importação
    var pastedCode by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(ImportMode.MERGE) }
    var isImporting by remember { mutableStateOf(false) }

    val previewResult = remember(pastedCode) {
        if (pastedCode.isBlank()) null else viewModel.previewBackupCode(pastedCode)
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 0 && generatedCode == null && !isGenerating) {
            isGenerating = true
            viewModel.generateBackupCode { res ->
                isGenerating = false
                res.onSuccess { (code, payload) ->
                    generatedCode = code
                    exportedPayload = payload
                    exportError = null
                }.onFailure { err ->
                    exportError = err.message ?: "Erro ao gerar código"
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Backup Offline (Código)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Transfira favoritos, notas e grifos entre aparelhos sem internet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Exportar") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Importar") }
                    )
                }

                if (selectedTab == 0) {
                    // --- EXPORTAR ---
                    if (isGenerating) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (exportError != null) {
                        Text(
                            text = exportError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    } else {
                        val payload = exportedPayload
                        val code = generatedCode.orEmpty()

                        if (payload != null) {
                            val totalHighlights = payload.textHighlights.size + payload.highlights.size
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (payload.totalCount == 0) {
                                        "Você ainda não possui favoritos, notas ou grifos salvos."
                                    } else {
                                        "Pronto para copiar:\n• ${payload.favorites.size} favoritos\n• ${payload.notes.size} anotações\n• $totalHighlights grifos"
                                    },
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        if (code.isNotEmpty() && (payload?.totalCount ?: 0) > 0) {
                            Text(
                                text = "Código de backup (${code.length} caracteres):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = code,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    maxLines = 5,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }

                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(code))
                                    Toast.makeText(
                                        context,
                                        "Código copiado! Cole no outro dispositivo.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Copiar Código Longo")
                            }

                            OutlinedButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Backup Juridireader")
                                        putExtra(Intent.EXTRA_TEXT, code)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Enviar código de backup")
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Enviar / Salvar Texto")
                            }
                        }
                    }
                } else {
                    // --- IMPORTAR ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cole o código gerado:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrBlank()) {
                                    pastedCode = clip
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Área de transferência vazia.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Colar", fontSize = 12.sp)
                        }
                    }

                    OutlinedTextField(
                        value = pastedCode,
                        onValueChange = { pastedCode = it },
                        placeholder = {
                            Text("JURIDI1:...", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp, max = 140.dp),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        maxLines = 5
                    )

                    if (previewResult != null) {
                        previewResult.onSuccess { payload ->
                            val dateStr = remember(payload.exportedAt) {
                                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                    .format(Date(payload.exportedAt))
                            }
                            val hlCount = payload.textHighlights.size + payload.highlights.size

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFDCFCE7),
                                border = BorderStroke(1.dp, Color(0xFF16A34A)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Código válido! (Gerado em $dateStr)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF14532D)
                                    )
                                    Text(
                                        text = "${payload.favorites.size} favoritos • ${payload.notes.size} anotações • $hlCount grifos",
                                        fontSize = 12.sp,
                                        color = Color(0xFF166534)
                                    )
                                }
                            }

                            Text(
                                text = "Como deseja importar estes dados?",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Opção 1: Mesclar / Adicionar aos atuais
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    width = if (selectedMode == ImportMode.MERGE) 2.dp else 1.dp,
                                    color = if (selectedMode == ImportMode.MERGE) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedMode = ImportMode.MERGE }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    RadioButton(
                                        selected = selectedMode == ImportMode.MERGE,
                                        onClick = { selectedMode = ImportMode.MERGE },
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Adicionar aos dados atuais (Mesclar)",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "Mantém seus favoritos, notas e grifos existentes e acrescenta os itens recebidos.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Opção 2: Substituir tudo
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    width = if (selectedMode == ImportMode.REPLACE) 2.dp else 1.dp,
                                    color = if (selectedMode == ImportMode.REPLACE) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedMode = ImportMode.REPLACE }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    RadioButton(
                                        selected = selectedMode == ImportMode.REPLACE,
                                        onClick = { selectedMode = ImportMode.REPLACE },
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Substituir tudo (Backup antigo)",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = if (selectedMode == ImportMode.REPLACE) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        Text(
                                            text = "Apaga todos os favoritos, notas e grifos atuais deste aparelho e substitui pelos do código.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (selectedMode == ImportMode.REPLACE) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Atenção: seus dados locais atuais serão substituídos.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Button(
                                enabled = !isImporting,
                                onClick = {
                                    isImporting = true
                                    viewModel.importBackupCode(pastedCode, selectedMode) { res ->
                                        isImporting = false
                                        res.onSuccess { summary ->
                                            val totalHls = summary.addedTextHighlights + summary.addedHighlights
                                            val msg = if (summary.mode == ImportMode.REPLACE) {
                                                "Backup restaurado: ${summary.addedFavorites} favoritos, ${summary.addedOrUpdatedNotes} notas e $totalHls grifos."
                                            } else {
                                                "Adicionados: ${summary.addedFavorites} favoritos, ${summary.addedOrUpdatedNotes} notas e $totalHls grifos."
                                            }
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            onDismiss()
                                        }.onFailure { err ->
                                            Toast.makeText(
                                                context,
                                                err.message ?: "Erro ao importar dados.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                colors = if (selectedMode == ImportMode.REPLACE) {
                                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                } else {
                                    ButtonDefaults.buttonColors()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (isImporting) "Importando..."
                                    else if (selectedMode == ImportMode.REPLACE) "Substituir Tudo Agora"
                                    else "Mesclar e Importar Agora"
                                )
                            }
                        }.onFailure { err ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = err.message ?: "Código inválido.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}
