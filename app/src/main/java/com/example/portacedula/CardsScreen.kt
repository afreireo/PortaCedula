package com.example.portacedula

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(vm: HomeViewModel) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    var viewingCard by remember { mutableStateOf<IdCard?>(null) }
    var showNameDialog by remember { mutableStateOf(false) }
    
    BackHandler(enabled = ui.selectedCards.isNotEmpty() || ui.isAddingNewCard) {
        if (ui.selectedCards.isNotEmpty()) {
            vm.clearCardSelection()
        } else if (ui.isAddingNewCard) {
            vm.cancelAddingCard()
        }
    }

    if (ui.isAddingNewCard) {
        NewCardView(vm = vm)
        return
    }

    val appBarContainerColor by animateColorAsState(
        targetValue = if (ui.selectedCards.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 200),
        label = "appBarColor"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Box {
                        AnimatedVisibility(
                            visible = ui.selectedCards.isEmpty(),
                            enter = fadeIn(animationSpec = tween(200)),
                            exit = fadeOut(animationSpec = tween(200))
                        ) {
                            Text("Mis Tarjetas", fontWeight = FontWeight.Bold)
                        }
                        AnimatedVisibility(
                            visible = ui.selectedCards.isNotEmpty(),
                            enter = fadeIn(animationSpec = tween(200)),
                            exit = fadeOut(animationSpec = tween(200))
                        ) {
                            Text("Modificar", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = appBarContainerColor),
                actions = {
                    AnimatedVisibility(
                        visible = ui.selectedCards.isNotEmpty(),
                        enter = fadeIn(animationSpec = tween(200)),
                        exit = fadeOut(animationSpec = tween(200))
                    ) {
                        Row {
                            IconButton(onClick = { vm.deleteSelectedCards() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (ui.selectedCards.isEmpty()) {
                FloatingActionButton(onClick = { showNameDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Añadir")
                }
            }
        }
    ) { pad ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(pad)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = ui.selectedCards.isNotEmpty()
            ) { vm.clearCardSelection() }

        if (ui.cards.isEmpty()) {
            Box(contentModifier, contentAlignment = Alignment.Center) {
                Text("No tienes tarjetas guardadas.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                modifier = contentModifier,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(ui.cards) { card ->
                    CardListItem(
                        card = card,
                        isSelected = card.id in ui.selectedCards,
                        isSelectionMode = ui.selectedCards.isNotEmpty(),
                        onFavorite = { vm.setFavorite(card.id) },
                        onToggleSelection = { vm.toggleCardSelection(card.id) },
                        onClick = {
                            if (ui.selectedCards.isNotEmpty()) {
                                vm.toggleCardSelection(card.id)
                            } else {
                                viewingCard = card
                            }
                        }
                    )
                }
            }
        }
    }

    if (showNameDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Nueva Tarjeta") },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de la tarjeta") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        vm.startAddingCard()
                        vm.updateDraftName(name)
                        showNameDialog = false
                    }
                }) { Text("Siguiente") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (viewingCard != null) {
        CardDetailDialog(card = viewingCard!!, vm = vm, onClose = { viewingCard = null })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardListItem(
    card: IdCard,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onFavorite: () -> Unit,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ID_CARD_ASPECT_RATIO)
            .combinedClickable(onClick = onClick, onLongClick = onToggleSelection),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(Modifier.fillMaxSize()) {
            if (card.frontUri != null) {
                AsyncImage(model = card.frontUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text(card.name, style = MaterialTheme.typography.titleLarge)
                }
            }
            
            if (!isSelectionMode) {
                IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(imageVector = Icons.Default.Home, contentDescription = "Favorito", tint = if (card.isFavorite) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f))
                }
            } else if (isSelected) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)))
                Icon(Icons.Default.CheckCircle, contentDescription = "Seleccionado", modifier = Modifier.align(Alignment.TopEnd).padding(12.dp), tint = MaterialTheme.colorScheme.primary)
            }

            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(8.dp)) {
                Text(card.name, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewCardView(vm: HomeViewModel) {
    val ui by vm.ui.collectAsState()
    val draft = ui.newCardDraft ?: return
    val context = LocalContext.current
    val activity = context.findActivity()

    var pendingSide by remember { mutableStateOf<((Uri) -> Unit)?>(null) }
    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { ar ->
        if (ar.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(ar.data)?.pages?.firstOrNull()?.imageUri?.let { uri ->
                pendingSide?.invoke(uri)
            }
        }
    }

    fun launchScanner(onImage: (Uri) -> Unit) {
        val act = activity ?: return
        val opts = GmsDocumentScannerOptions.Builder().setPageLimit(1).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE).build()
        GmsDocumentScanning.getClient(opts).getStartScanIntent(act).addOnSuccessListener { scannerLauncher.launch(IntentSenderRequest.Builder(it).build()) }
        pendingSide = onImage
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(draft.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { vm.cancelAddingCard() }) { Icon(Icons.Default.Close, contentDescription = "Cancelar") }
                },
                actions = {
                    if (draft.frontUri != null && draft.backUri != null) {
                        IconButton(onClick = { vm.finishAddingCard() }) { Icon(Icons.Default.Check, contentDescription = "Listo") }
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IdCardSection(
                title = "Parte Frontal",
                uri = draft.frontUri,
                onAdd = { launchScanner { vm.onFrontCaptured(draft.id, it.toString()) } },
                onOpen = { /* Zoom */ }
            )
            IdCardSection(
                title = "Parte Reversa",
                uri = draft.backUri,
                onAdd = { launchScanner { vm.onBackCaptured(draft.id, it.toString()) } },
                onOpen = { /* Zoom */ }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailDialog(card: IdCard, vm: HomeViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val ui by vm.ui.collectAsState()
    
    val activity = context.findActivity()
    var pendingSide by remember { mutableStateOf<((Uri) -> Unit)?>(null) }
    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { ar ->
        if (ar.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(ar.data)?.pages?.firstOrNull()?.imageUri?.let { uri ->
                pendingSide?.invoke(uri)
            }
        }
    }

    fun launchDocScanner(onImage: (Uri) -> Unit) {
        val act = activity ?: return
        val opts = GmsDocumentScannerOptions.Builder().setPageLimit(1).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE).build()
        GmsDocumentScanning.getClient(opts).getStartScanIntent(act).addOnSuccessListener { scannerLauncher.launch(IntentSenderRequest.Builder(it).build()) }
        pendingSide = onImage
    }

    // --- BackHandler para el detalle ---
    BackHandler(enabled = ui.selectedPart != null) {
        vm.clearPartSelection()
    }

    val appBarContainerColor by animateColorAsState(
        targetValue = if (ui.selectedPart != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 200),
        label = "appBarColorDetail"
    )

    Dialog(
        onDismissRequest = { 
            if (ui.selectedPart != null) {
                vm.clearPartSelection()
            } else {
                onClose()
            }
        }, 
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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
                                Text("Detalle", fontWeight = FontWeight.Bold)
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
                    navigationIcon = {
                        if (ui.selectedPart == null) {
                            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Cerrar") }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = appBarContainerColor),
                    actions = {
                        AnimatedVisibility(
                            visible = ui.selectedPart != null,
                            enter = fadeIn(animationSpec = tween(200)),
                            exit = fadeOut(animationSpec = tween(200))
                        ) {
                            if (ui.selectedPart != null) {
                                Row {
                                    IconButton(onClick = { 
                                        launchDocScanner { uri -> 
                                            if (ui.selectedPart == CardPart.FRONT) 
                                                vm.onFrontCaptured(card.id, uri.toString())
                                            else 
                                                vm.onBackCaptured(card.id, uri.toString())
                                            vm.clearPartSelection()
                                        }
                                    }) { Icon(Icons.Default.Edit, contentDescription = "Editar") }
                                    IconButton(onClick = { vm.deletePart(card.id, ui.selectedPart!!) }) { Icon(Icons.Default.Delete, contentDescription = "Eliminar") }
                                }
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (ui.selectedPart == null) {
                    FloatingActionButton(
                        onClick = { PdfGenerator.generateAndShare(context, card) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) { Icon(Icons.Default.Share, contentDescription = "Compartir") }
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize()) {
                CardContentView(
                    card = card,
                    modifier = Modifier.padding(pad),
                    selectedPart = ui.selectedPart,
                    onFrontAdd = { launchDocScanner { uri -> vm.onFrontCaptured(card.id, uri.toString()) } },
                    onBackAdd = { launchDocScanner { uri -> vm.onBackCaptured(card.id, uri.toString()) } },
                    onZoom = { vm.onZoom(it) },
                    onPartLongClick = { vm.selectPart(it) }
                )

                // Overlay invisible para salir del modo edición al tocar fuera
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
    }
}
