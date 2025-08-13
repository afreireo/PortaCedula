package com.example.portacedula

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.portacedula.ui.theme.PortaCedulaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = IdCardRepository(applicationContext)

        setContent {
            PortaCedulaTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "splash") {
                    composable("splash") {
                        SplashScreen { nav.navigate("home") { popUpTo("splash") { inclusive = true } } }
                    }
                    composable("home") {
                        val vm = viewModel<HomeViewModel>(
                            factory = object : ViewModelProvider.Factory {
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    @Suppress("UNCHECKED_CAST") return HomeViewModel(repo) as T
                                }
                            }
                        )
                        HomeScreen(vm)
                    }
                }
            }
        }
    }
}
