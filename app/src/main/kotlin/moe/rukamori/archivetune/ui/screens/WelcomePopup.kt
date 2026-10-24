/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R

@Composable
fun WelcomePopup(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = "Selamat Datang 👋",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Daftar Engine ArchiveTune:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // List Bahan Engine
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EngineItem("Kotlin & Jetpack Compose", "Bahasa & UI Framework Utama")
                    EngineItem("Ktor Client & OkHttp", "Sistem Jaringan & API Request")
                    EngineItem("ExoPlayer (Media3)", "Mesin Pemutar Audio Utama")
                    EngineItem("Room Database", "Penyimpanan Database Lokal")
                    EngineItem("Dagger Hilt", "Sistem Injeksi Dependensi")
                    EngineItem("Pterodactyl Panel", "Manajemen Backend Server")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Masuk Aplikasi")
            }
        },
        dismissButton = {
            // Row untuk menampung tombol sosial media di kiri bawah popup
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { uriHandler.openUri("https://github.com/SayidGithub") }) {
                    Icon(
                        painter = painterResource(R.drawable.github), 
                        contentDescription = "GitHub", 
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Menggunakan TextButton karena ikon TikTok tidak ada di sistem Anda
                TextButton(onClick = { uriHandler.openUri("https://www.tiktok.com/@sya_whd1") }) {
                    Text("TikTok", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                IconButton(onClick = { uriHandler.openUri("https://wahid-archivetune.vercel.app") }) {
                    Icon(
                        painter = painterResource(R.drawable.website), 
                        contentDescription = "Website", 
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

@Composable
private fun EngineItem(name: String, desc: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = name, 
                style = MaterialTheme.typography.bodyLarge, 
                fontWeight = FontWeight.Bold, 
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc, 
                style = MaterialTheme.typography.bodySmall, 
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
