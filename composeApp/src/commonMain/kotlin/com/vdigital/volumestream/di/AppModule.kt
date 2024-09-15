package com.vdigital.volumestream.di

import com.vdigital.volumestream.platform.di.platformCoreModule
import org.koin.core.module.Module
import org.koin.dsl.module


val appModule: Module
    get() = module {
        includes(commonModule + platformCoreModule)
    }

