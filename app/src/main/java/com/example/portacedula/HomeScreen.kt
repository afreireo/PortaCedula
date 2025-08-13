package com.example.portacedula

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

// ---------- extensión para obtener Activity ----------
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel) {
    val ui by vm.ui.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context.findActivity()

    var pendingSide by remember { mutableStateOf<((Uri) -> Unit)?>(null) }

    // Launcher del Document Scanner
    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { ar ->
        if (ar.resultCode == Activity.RESULT_OK) {
            val res = GmsDocumentScanningResult.fromActivityResultIntent(ar.data)
            res?.pages?.firstOrNull()?.imageUri?.let { uri ->
                pendingSide?.invoke(uri)
            }
        }
    }

    fun launchDocScanner(onImage: (Uri) -> Unit) {
        val act = activity ?: run {
            Toast.makeText(context, "No se encontró Activity", Toast.LENGTH_SHORT).show()
            return
        }

        val opts = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE) // con cuadro guía
            .build()

        val scanner = GmsDocumentScanning.getClient(opts)
        pendingSide = onImage
        scanner.getStartScanIntent(act)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener {
                Toast.makeText(context, "Error al abrir el escáner", Toast.LENGTH_SHORT).show()
            }
    }

    // -------- UI principal --------
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Porta Cedula") }, // por defecto alineado izquierda
                colors = TopAppBarDefaults.topAppBarColors()
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.toggleAddSheet(true) }) {
                Icon(Icons.Default.Edit, contentDescription = "Editar")
            }
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            IdCardSection(
                title = "Parte anverso",
                uri = ui.frontUri,
                onAdd = { launchDocScanner { uri -> vm.onFrontCaptured(uri.toString()) } },
                onOpen = { ui.frontUri?.let(vm::onZoom) }
            )
            IdCardSection(
                title = "Parte reverso",
                uri = ui.backUri,
                onAdd = { launchDocScanner { uri -> vm.onBackCaptured(uri.toString()) } },
                onOpen = { ui.backUri?.let(vm::onZoom) }
            )
        }
    }

    // Bottom sheet con opciones del FAB
    if (ui.showAddSheet) {
        ModalBottomSheet(onDismissRequest = { vm.toggleAddSheet(false) }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Agregar", style = MaterialTheme.typography.titleMedium)
                ListItem(
                    headlineContent = { Text("Agregar anverso") },
                    modifier = Modifier.clickable {
                        vm.toggleAddSheet(false)
                        launchDocScanner { uri -> vm.onFrontCaptured(uri.toString()) }
                    }
                )
                ListItem(
                    headlineContent = { Text("Agregar reverso") },
                    modifier = Modifier.clickable {
                        vm.toggleAddSheet(false)
                        launchDocScanner { uri -> vm.onBackCaptured(uri.toString()) }
                    }
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Zoom de imagen
    if (ui.showZoom != null) {
        Dialog(onDismissRequest = { vm.onZoom(null) }) {
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp) {
                AsyncImage(
                    model = ui.showZoom,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.onZoom(null) }
                )
            }
        }
    }
}

@Composable
private fun IdCardSection(
    title: String,
    uri: String?,
    onAdd: () -> Unit,
    onOpen: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clickable { if (uri != null) onOpen() else onAdd() },
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (uri == null) {
                    Text("Toca para agregar", textAlign = TextAlign.Center)
                } else {
                    AsyncImage(
                        model = uri,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
