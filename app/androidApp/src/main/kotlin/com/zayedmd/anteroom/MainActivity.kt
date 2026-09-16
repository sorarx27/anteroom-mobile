package com.zayedmd.anteroom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Initialize RevenueCat SDK in sandbox/test mode
        try {
            Purchases.logLevel = com.revenuecat.purchases.LogLevel.DEBUG
            Purchases.configure(
                PurchasesConfiguration.Builder(
                    this,
                    "goog_GoVlNPjRvRsCPJZnSwbvxVLyIxL"
                ).build()
            )
        } catch (e: Exception) {
            android.util.Log.w("AnteroomPurchases", "RevenueCat configuration fallback: ${e.message}")
        }

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}