package com.finpulse.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finpulse.feature.portfolio.api.AssetHolding
import com.finpulse.feature.portfolio.api.PortfolioRepository
import com.finpulse.feature.portfolio.api.PortfolioStreamState
import com.finpulse.feature.vault.api.VaultStatusProvider
import org.koin.android.ext.android.inject
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val portfolioRepository: PortfolioRepository by inject()
    private val vaultStatusProvider: VaultStatusProvider by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val portfolioState by portfolioRepository.portfolioState.collectAsState()
                    val isUnlocked by vaultStatusProvider.isVaultUnlocked.collectAsState()

                    FinPulseDashboard(
                        state = portfolioState,
                        isVaultUnlocked = isUnlocked,
                        onToggleVault = { vaultStatusProvider.setVaultUnlocked(!isUnlocked) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FinPulseDashboard(
    state: PortfolioStreamState,
    isVaultUnlocked: Boolean,
    onToggleVault: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "FinPulse Live Ticker",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Phase 2: callbackFlow + WhileSubscribed(5000)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onToggleVault) {
                Text(if (isVaultUnlocked) "Lock Vault" else "Unlock Vault")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        when (state) {
            PortfolioStreamState.Connecting -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Connecting to WebSocket Ticker Engine...")
                }
            }

            PortfolioStreamState.Locked -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Hardware Vault Locked",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "flatMapLatest cancelled the upstream callbackFlow and awaitClose disconnected the WebSocket.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            is PortfolioStreamState.Active -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Total Portfolio Value",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = String.format(Locale.US, "$%,.2f", state.totalPortfolioUsd),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.holdings, key = { it.symbol.value }) { holding ->
                        HoldingRow(holding)
                    }
                }
            }
        }
    }
}

@Composable
private fun HoldingRow(holding: AssetHolding) {
    val isPositive = holding.changePercent24h >= 0.0
    val deltaColor = if (isPositive) Color(0xFF1B873F) else Color(0xFFC5221F)

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = holding.symbol.value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${holding.name} • ${String.format(Locale.US, "%.2f", holding.quantity.units)} units",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(Locale.US, "$%,.2f", holding.livePrice.usd),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = String.format(
                        Locale.US,
                        "%s%.2f%%",
                        if (isPositive) "+" else "",
                        holding.changePercent24h,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = deltaColor,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
