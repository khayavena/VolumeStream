package com.vditital.data.di

import org.koin.core.module.Module
import org.koin.dsl.module


val externalDataModule: Module
    get() = module {
        includes(internalDataModule + platformDataModule)
    }