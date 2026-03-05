package com.example.portacedula

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.portacedula.ui.theme.PortaCedulaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val repo = IdCardRepository(applicationContext)
        
        setContent {
            val vm: HomeViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return HomeViewModel(repo) as T
                    }
                }
            )
            
            val ui by vm.ui.collectAsState()

            val darkTheme = when (ui.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            PortaCedulaTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()

                // Visor de Zoom GLOBAL
                if (ui.showZoom != null) {
                    FullscreenImageViewer(uri = ui.showZoom!!, onClose = { vm.onZoom(null) })
                }

                NavHost(navController = navController, startDestination = "splash") {
                    composable("splash") {
                        SplashScreen { 
                            navController.navigate("main") { 
                                popUpTo("splash") { inclusive = true } 
                            } 
                        }
                    }
                    composable("main") {
                        MainPagerScreen(vm)
                    }
                }
            }
        }
    }
}

@Composable
fun MainPagerScreen(vm: HomeViewModel) {
    val ui by vm.ui.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val pagerState = rememberPagerState(pageCount = { 3 })
    var showNameDialog by remember { mutableStateOf(false) }

    val isOverlayActive = ui.isAddingNewCard || ui.selectedCards.isNotEmpty() || ui.selectedPart != null || ui.showZoom != null
    
    BackHandler(enabled = pagerState.currentPage != 0 && !isOverlayActive) {
        scope.launch { 
            pagerState.animateScrollToPage(0) 
        }
    }

    Scaffold(
        bottomBar = {
            if (!ui.isAddingNewCard) {
                NavigationBar {
                    val items = listOf(
                        Triple(0, "Inicio", Icons.Default.Home),
                        Triple(1, "Tarjetas", Icons.Default.Wallet),
                        Triple(2, "Ajustes", Icons.Default.Settings)
                    )
                    items.forEach { (index, label, icon) ->
                        val isSelected = pagerState.currentPage == index
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            selected = isSelected,
                            onClick = {
                                if (ui.selectedPart != null || ui.selectedCards.isNotEmpty()) {
                                    vm.clearPartSelection()
                                    vm.clearCardSelection()
                                } else if (!isSelected) {
                                    scope.launch { 
                                        pagerState.animateScrollToPage(index) 
                                    }
                                }
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            val currentPage = pagerState.currentPage
            val showFab = !ui.isAddingNewCard && when (currentPage) {
                0 -> ui.favoriteCard != null && ui.selectedPart == null
                1 -> ui.selectedCards.isEmpty()
                else -> false
            }
            
            AnimatedVisibility(
                visible = showFab,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        when (currentPage) {
                            0 -> ui.favoriteCard?.let { PdfGenerator.generateAndShare(context, it) }
                            1 -> showNameDialog = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    val icon = if (currentPage == 0) Icons.Default.Share else Icons.Default.Add
                    Icon(icon, contentDescription = null)
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            userScrollEnabled = !isOverlayActive,
            beyondViewportPageCount = 2
        ) { pageIndex ->
            when (pageIndex) {
                0 -> HomeScreen(vm)
                1 -> CardsScreen(vm)
                2 -> SettingsScreen(vm)
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
}
