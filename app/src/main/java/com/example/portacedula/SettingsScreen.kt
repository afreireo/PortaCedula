package com.example.portacedula

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: HomeViewModel) {
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Configuración", fontWeight = FontWeight.Bold) })
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Apariencia", style = MaterialTheme.typography.titleMedium)
            
            Column {
                ThemeOptionItem(
                    title = "Seguir sistema",
                    selected = ui.themeMode == ThemeMode.SYSTEM,
                    onClick = { vm.setThemeMode(ThemeMode.SYSTEM) }
                )
                ThemeOptionItem(
                    title = "Modo Claro",
                    selected = ui.themeMode == ThemeMode.LIGHT,
                    onClick = { vm.setThemeMode(ThemeMode.LIGHT) }
                )
                ThemeOptionItem(
                    title = "Modo Oscuro",
                    selected = ui.themeMode == ThemeMode.DARK,
                    onClick = { vm.setThemeMode(ThemeMode.DARK) }
                )
            }

            HorizontalDivider()

            Text("Acerca de", style = MaterialTheme.typography.titleMedium)
            
            ListItem(
                headlineContent = { Text("Información de la App") },
                supportingContent = { Text("Porta Cédula v1.0 - Guarda tus documentos de forma segura.") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { /* TODO: Link a donación */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Default.Coffee, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Donar un café")
            }
        }
    }
}

@Composable
fun ThemeOptionItem(title: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(title) },
        trailingContent = {
            RadioButton(selected = selected, onClick = null)
        }
    )
}
