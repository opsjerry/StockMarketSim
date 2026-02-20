package com.example.stockmarketsim.data.remote

import com.example.stockmarketsim.domain.broker.BrokerSource
import com.example.stockmarketsim.domain.broker.Holding
import com.zerodhatech.kiteconnect.KiteConnect
import com.zerodhatech.kiteconnect.utils.Constants
import com.zerodhatech.models.OrderParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ZerodhaBrokerSource @Inject constructor(
    private val zerodhaClient: ZerodhaClient
) : BrokerSource {

    override suspend fun placeBuyOrder(symbol: String, quantity: Int, price: Double, tag: String): String {
        return placeOrder(symbol, quantity, price, Constants.TRANSACTION_TYPE_BUY, tag)
    }

    override suspend fun placeSellOrder(symbol: String, quantity: Int, price: Double, tag: String): String {
        return placeOrder(symbol, quantity, price, Constants.TRANSACTION_TYPE_SELL, tag)
    }

    private suspend fun placeOrder(symbol: String, quantity: Int, price: Double, type: String, tag: String): String = withContext(Dispatchers.IO) {
        val kite = zerodhaClient.getKiteConnect() ?: throw Exception("Zerodha Broker Not Connected")
        
        val zerodhaSymbol = convertToZerodhaSymbol(symbol) // Helper needed here too or shared
        
        val params = OrderParams()
        params.exchange = Constants.EXCHANGE_NSE
        params.tradingsymbol = zerodhaSymbol
        params.transactionType = type
        params.quantity = quantity
        params.price = price
        params.orderType = Constants.ORDER_TYPE_LIMIT // SAFETY: Limit Orders Only
        params.product = Constants.PRODUCT_CNC // Delivery
        params.validity = Constants.VALIDITY_DAY
        params.tag = tag
        
        val order = kite.placeOrder(params, Constants.VARIETY_REGULAR)
        return@withContext order.orderId ?: throw Exception("Order Placed but No ID returned")
    }

    override suspend fun getHoldings(): List<Holding> = withContext(Dispatchers.IO) {
        val kite = zerodhaClient.getKiteConnect() ?: return@withContext emptyList()
        
        val holdings = kite.getHoldings()
        holdings.map { h ->
            Holding(
                symbol = h.tradingSymbol, // Zerodha SDK uses camelCase for Holding
                quantity = h.quantity.toInt(),
                averagePrice = h.averagePrice.toDouble(),
                currentPrice = h.lastPrice.toDouble(),
                pnl = h.pnl.toDouble()
            )
        }
    }

    override suspend fun isConnected(): Boolean {
        return zerodhaClient.isSessionActive()
    }
    
    private fun convertToZerodhaSymbol(appSymbol: String): String {
        // Shared logic ideally, but duplicating for now to avoid dependency on Source
        return when {
            appSymbol.endsWith(".NS") -> appSymbol.removeSuffix(".NS") // Zerodha doesn't use "NSE:" in OrderParams tradingsymbol, just "RELIANCE"
            appSymbol.endsWith(".BO") -> appSymbol.removeSuffix(".BO")
            else -> appSymbol
        }
    }
}
