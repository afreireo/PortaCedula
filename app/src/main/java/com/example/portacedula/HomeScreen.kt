@file:Suppress("SetJavaScriptEnabled")
package com.example.portacedula

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import androidx.compose.foundation.gestures.detectTapGestures

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()
    val card = ui.favoriteCard

    BackHandler(enabled = ui.selectedPart != null) {
        vm.clearPartSelection()
    }

    var pendingSide by remember { mutableStateOf<((Uri) -> Unit)?>(null) }

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
        val act = activity ?: return
        val opts = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
            .build()
        val scanner = GmsDocumentScanning.getClient(opts)
        pendingSide = onImage
        scanner.getStartScanIntent(act)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
    }

    val appBarContainerColor by animateColorAsState(
        targetValue = if (ui.selectedPart != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 200),
        label = "appBarColor"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Box {
                        AnimatedVisibility(
                            visible = ui.selectedPart == null,
                            enter = fadeIn(animationSpec = tween(200)),
                            exit = fadeOut(animationSpec = tween(200))
                        ) {
                            Text("Porta Cédula", fontWeight = FontWeight.Bold)
                        }
                        AnimatedVisibility(
                            visible = ui.selectedPart != null,
                            enter = fadeIn(animationSpec = tween(200)),
                            exit = fadeOut(animationSpec = tween(200))
                        ) {
                            Text("Modificar", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appBarContainerColor
                ),
                actions = {
                    AnimatedVisibility(
                        visible = ui.selectedPart != null && card != null,
                        enter = fadeIn(animationSpec = tween(200)),
                        exit = fadeOut(animationSpec = tween(200))
                    ) {
                        if (ui.selectedPart != null && card != null) {
                            Row {
                                IconButton(onClick = { 
                                    launchDocScanner { uri -> 
                                        if (ui.selectedPart == CardPart.FRONT) 
                                            vm.onFrontCaptured(card.id, uri.toString())
                                        else 
                                            vm.onBackCaptured(card.id, uri.toString())
                                        vm.clearPartSelection()
                                    }
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar")
                                }
                                IconButton(onClick = { vm.deletePart(card.id, ui.selectedPart!!) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                                }
                                IconButton(onClick = { vm.clearPartSelection() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancelar")
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize()) {
            if (card == null) {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    Text("No hay tarjeta favorita.\nConfigúrala en la pestaña Tarjetas.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else {
                CardContentView(
                    card = card,
                    modifier = Modifier.padding(pad),
                    selectedPart = ui.selectedPart,
                    onFrontAdd = { launchDocScanner { uri -> vm.onFrontCaptured(card.id, uri.toString()) } },
                    onBackAdd = { launchDocScanner { uri -> vm.onBackCaptured(card.id, uri.toString()) } },
                    onZoom = { vm.onZoom(it) },
                    onPartLongClick = { vm.selectPart(it) }
                )
            }

            // Overlay invisible que captura todos los clics para salir del modo edición
            if (ui.selectedPart != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(pad)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { vm.clearPartSelection() })
                        }
                )
            }
        }
    }

    ui.showZoom?.let { FullscreenImageViewer(uri = it, onClose = { vm.onZoom(null) }) }
}

@Composable
fun CardContentView(
    card: IdCard,
    modifier: Modifier = Modifier,
    selectedPart: CardPart? = null,
    onFrontAdd: () -> Unit = {},
    onBackAdd: () -> Unit = {},
    onZoom: (String) -> Unit = {},
    onPartLongClick: (CardPart) -> Unit = {}
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Rotating3DCard(
                frontUri = card.frontUri,
                backUri = card.backUri,
                modifier = Modifier.fillMaxWidth().aspectRatio(ID_CARD_ASPECT_RATIO)
            )
        }
        item {
            IdCardSection(
                modifier = Modifier.fillMaxWidth(0.9f),
                title = "Parte Frontal",
                uri = card.frontUri,
                isSelected = selectedPart == CardPart.FRONT,
                onAdd = onFrontAdd,
                onOpen = { card.frontUri?.let(onZoom) },
                onLongClick = { onPartLongClick(CardPart.FRONT) }
            )
        }
        item {
            IdCardSection(
                modifier = Modifier.fillMaxWidth(0.9f),
                title = "Parte Reversa",
                uri = card.backUri,
                isSelected = selectedPart == CardPart.BACK,
                onAdd = onBackAdd,
                onOpen = { card.backUri?.let(onZoom) },
                onLongClick = { onPartLongClick(CardPart.BACK) }
            )
        }
    }
}
