package com.hernan.laberinto.android

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.badlogic.gdx.Gdx
import com.hernan.laberinto.Store
import java.util.Locale

/**
 * Compra única del juego completo con Google Play Billing.
 * - Consulta el precio del producto [Store.PRODUCT_ID] y las compras al arrancar y al volver a la app.
 * - Confirma (acknowledge) las compras nuevas; si no se confirman en 3 días Google las reembolsa.
 * - Guarda "comprado" en el móvil para que funcione sin conexión.
 */
class PlayStore(private val activity: Activity) : Store, PurchasesUpdatedListener {
    private val prefs = activity.getSharedPreferences("store", Context.MODE_PRIVATE)

    @Volatile override var owned = prefs.getBoolean("owned", false)
        private set

    @Volatile override var pending = false
        private set

    @Volatile override var price: String? = null
        private set

    @Volatile private var fallback: String? = null
    override val priceFallback: String? get() = fallback

    override var onChange: (() -> Unit)? = null

    @Volatile private var product: ProductDetails? = null

    private val client = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    init {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    queryProduct()
                    refresh()
                }
            }

            override fun onBillingServiceDisconnected() {
                // La reconexión es automática (enableAutoServiceReconnection)
            }
        })
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(Store.PRODUCT_ID)
                        .setProductType(ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingResponseCode.OK) return@queryProductDetailsAsync
            val p = details.productDetailsList.firstOrNull() ?: return@queryProductDetailsAsync
            product = p
            val offer = p.oneTimePurchaseOfferDetails
            price = offer?.formattedPrice
            fallback = offer?.let { String.format(Locale.US, "%.2f %s", it.priceAmountMicros / 1_000_000.0, it.priceCurrencyCode) }
            notifyChange()
        }
    }

    override fun refresh() {
        if (!client.isReady) return
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingResponseCode.OK) handle(purchases, fromQuery = true)
        }
    }

    override fun purchase() {
        val p = product ?: return
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(p).build()),
            )
            .build()
        activity.runOnUiThread { client.launchBillingFlow(activity, flow) }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> if (purchases != null) handle(purchases, fromQuery = false)
            BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            else -> {} // cancelada por el usuario u otro error: no cambia nada
        }
    }

    private fun handle(purchases: List<Purchase>, fromQuery: Boolean) {
        var isOwned = false
        var isPending = false
        for (p in purchases.filter { Store.PRODUCT_ID in it.products }) {
            when (p.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    isOwned = true
                    if (!p.isAcknowledged) acknowledge(p)
                }
                Purchase.PurchaseState.PENDING -> isPending = true
                else -> {}
            }
        }
        // La consulta completa es la referencia (así se retira si hay un reembolso);
        // una actualización suelta solo puede añadir la compra.
        owned = if (fromQuery) isOwned else owned || isOwned
        pending = isPending && !owned
        prefs.edit().putBoolean("owned", owned).apply()
        notifyChange()
    }

    private fun acknowledge(p: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
        client.acknowledgePurchase(params) { }
    }

    private fun notifyChange() {
        Gdx.app.postRunnable { onChange?.invoke() }
    }

    override fun dispose() {
        client.endConnection()
    }
}
