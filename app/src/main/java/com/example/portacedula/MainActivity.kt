package com.example.portacedula

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
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

            PortaCedulaTheme(darkTheme = ui.isDarkMode) {
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
    
    // Configuración del Pager con 3 páginas (Inicio, Tarjetas, Ajustes)
    val pagerState = rememberPagerState(pageCount = { 3 })

    // Determinamos si hay overlays activos (Zoom, Selección, etc) que deban cerrarse primero
    val isOverlayActive = ui.isAddingNewCard || ui.selectedCards.isNotEmpty() || ui.selectedPart != null || ui.showZoom != null
    
    // NAVEGACIÓN INTELIGENTE DEL BOTÓN ATRÁS:
    // Siempre volvemos a la pestaña 0 (Inicio) desde la 1 o 2 si no hay nada abierto.
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
                                if (!isSelected) {
                                    scope.launch { 
                                        pagerState.animateScrollToPage(index) 
                                    }
                                }
                            }
                        )
                    }
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
            // CLAVE: Mantener todas las páginas vivas para que la tarjeta 3D no se destruya
            beyondViewportPageCount = 2
        ) { pageIndex ->
            when (pageIndex) {
                0 -> HomeScreen(vm)
                1 -> CardsScreen(vm)
                2 -> SettingsScreen(vm)
            }
        }
    }
}
