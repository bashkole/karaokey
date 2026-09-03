package nl.ikomex.karaokey.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import nl.ikomex.karaokey.ui.LoginUiState
import nl.ikomex.karaokey.ui.components.QrCodeImage

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginScreen(
    state: LoginUiState,
    onConnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Karaokey",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1DB954)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Connect your Spotify Premium account to start the party.",
            fontSize = 22.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (state.authorizeUrl != null) {
            QrCodeImage(
                content = state.authorizeUrl,
                modifier = Modifier.size(220.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Scan with your phone to log in to Spotify",
                fontSize = 20.sp,
                color = Color(0xFFCCCCCC),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Use the same Wi-Fi as this Fire Stick. Waiting for authorization...",
                fontSize = 16.sp,
                color = Color(0xFF888888),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 720.dp)
            )
        } else {
            Button(onClick = onConnect, enabled = !state.isLoading) {
                Text(
                    text = if (state.isLoading) "Starting..." else "Connect Spotify",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        }

        state.error?.let { error ->
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = error,
                fontSize = 18.sp,
                color = Color(0xFFFF6B6B),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 720.dp)
            )
        }
    }
}
