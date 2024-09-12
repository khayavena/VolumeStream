package com.vdigital.volumestream.di

import com.vdigital.volumestream.platform.di.platformCoreModule
import org.koin.core.module.Module
import org.koin.dsl.module


val coreModule: Module
    get() = module {
        includes(commonModule + platformCoreModule)
    }

