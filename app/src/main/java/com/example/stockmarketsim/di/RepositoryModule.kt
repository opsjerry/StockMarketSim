package com.example.stockmarketsim.di

import com.example.stockmarketsim.data.repository.SimulationRepositoryImpl
import com.example.stockmarketsim.data.repository.StockRepositoryImpl
import com.example.stockmarketsim.domain.repository.SimulationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSimulationRepository(
        simulationRepositoryImpl: SimulationRepositoryImpl
    ): SimulationRepository

    @Binds
    @Singleton
    abstract fun bindStockRepository(
        stockRepositoryImpl: StockRepositoryImpl
    ): com.example.stockmarketsim.domain.repository.StockRepository
}
