// BluetoothModule.kt

package com.di.core.bluetooth.di

import com.di.core.bluetooth.CadenceRepository
import com.di.core.bluetooth.CadenceRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BluetoothModule {

    @Binds
    @Singleton
    abstract fun bindCadenceRepository(
        cadenceRepositoryImpl: CadenceRepositoryImpl
    ): CadenceRepository
}